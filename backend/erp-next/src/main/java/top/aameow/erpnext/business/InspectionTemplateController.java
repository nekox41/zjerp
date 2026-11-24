package top.aameow.erpnext.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import top.aameow.erpnext.files.PrivateFileStorage;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/inspection-templates")
public class InspectionTemplateController {
    private final JdbcTemplate jdbc;private final PrivateFileStorage storage;
    public InspectionTemplateController(JdbcTemplate jdbc,PrivateFileStorage storage){this.jdbc=jdbc;this.storage=storage;}
    private static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"模板或文件不存在");return rows.get(0);}
    private String required(Map<String,Object>b,String key,int max){String s=Objects.toString(b.get(key),"").trim();if(s.isEmpty()||s.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 无效");return s;}
    private long fileId(Object raw){try{long id=Long.parseLong(raw.toString());if(id>0)return id;}catch(RuntimeException ignored){}throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件ID无效");}
    private void audit(Authentication a,long id,String action){jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('templateId',?))",actor(a),action,"erp_inspection_template",Long.toString(id),UUID.randomUUID().toString(),id);}
    @GetMapping
    @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:template:write')")
    public List<Map<String,Object>> list(Authentication a,@RequestParam(defaultValue="false") boolean includeInactive){
        boolean manager=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("report:template:write"));
        var rows=jdbc.queryForList("SELECT t.*,f.original_name,f.byte_size,(SELECT COUNT(*) FROM erp_inspection_template_item i WHERE i.template_id=t.id) item_count FROM erp_inspection_template t JOIN erp_file_object f ON f.id=t.file_id WHERE (?=1 OR t.active=1) ORDER BY t.template_kind,t.system_name,t.template_name,t.revision_no DESC",manager&&includeInactive?1:0);
        var references=jdbc.queryForList("SELECT x.template_id,s.id,s.standard_no,s.edition,s.title,s.active,s.verified FROM erp_inspection_template_standard x JOIN erp_fire_standard s ON s.id=x.standard_id ORDER BY s.standard_no,s.edition,s.revision_no");
        Map<Long,List<Map<String,Object>>> byTemplate=new HashMap<>();
        for(var row:references){long id=((Number)row.remove("template_id")).longValue();byTemplate.computeIfAbsent(id,k->new ArrayList<>()).add(row);}
        for(var row:rows)row.put("recommendedStandards",byTemplate.getOrDefault(((Number)row.get("id")).longValue(),List.of()));
        return rows;
    }
    @GetMapping("/systems") @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:photo:read') or hasAuthority('report:template:write')")
    public List<String> systems(){
        Set<String> names=new LinkedHashSet<>(new ReportDocxPhotoInserter().supportedSystems());
        names.addAll(jdbc.query("SELECT DISTINCT system_name FROM erp_inspection_template WHERE template_kind='system' ORDER BY system_name",(rs,i)->rs.getString(1)));
        return new ArrayList<>(names);
    }
    @PostMapping
    @PreAuthorize("hasAuthority('report:template:write') and hasAuthority('file:upload')") @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> create(@RequestBody Map<String,Object>b,Authentication a)throws Exception{
        String kind=required(b,"kind",16),name=required(b,"name",150);
        if(!Set.of("base","system").contains(kind))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板类型无效");
        String system="system".equals(kind)?required(b,"system",80):"";
        if(system.contains("{{")||system.contains("}}")||system.contains("[")||system.contains("]")||system.equals("现场检测照片")||system.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"消防系统名称无效");
        if(!Boolean.TRUE.equals(b.get("cleanConfirmed")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请确认模板已清除旧项目、检测结果、照片、签署及其他不应复用的信息");
        String notes=Objects.toString(b.get("notes"),"");if(notes.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"备注过长");
        var f=one("SELECT * FROM erp_file_object WHERE id=?",fileId(b.get("fileId")));
        if(!(f.get("uploaded_by") instanceof Number owner)||owner.longValue()!=actor(a)||!DOCX.equals(f.get("media_type")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请上传自己的 DOCX 模板");
        byte[] bytes=Files.readAllBytes(storage.resolve(f.get("storage_key").toString()));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if(!hash.equals(f.get("sha256")))throw new ResponseStatusException(HttpStatus.CONFLICT,"模板原件校验失败");
        var info=InspectionTemplateComposer.inspect(bytes,"base".equals(kind));
        // Serialize template revision allocation with an existing stable row; never replace an old revision.
        jdbc.queryForObject("SELECT id FROM erp_permission WHERE code='report:template:write' FOR UPDATE",Long.class);
        int next=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM erp_inspection_template WHERE template_kind=? AND system_name=? AND template_name=?",Integer.class,kind,system,name);
        jdbc.update("INSERT INTO erp_inspection_template(template_kind,system_name,template_name,revision_no,file_id,sha256,notes,created_by) VALUES(?,?,?,?,?,?,?,?)",kind,system,name,next,f.get("id"),hash,notes,actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(b.containsKey("standardIds")){
            if(!(b.get("standardIds") instanceof List<?> selected)||selected.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板参考规范选择无效");
            Set<Long> unique=new HashSet<>();
            for(Object raw:selected){
                long sid=fileId(raw);if(!unique.add(sid))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"重复规范");
                one("SELECT id FROM erp_fire_standard WHERE id=? AND verified=1 AND active=1",sid);
                jdbc.update("INSERT INTO erp_inspection_template_standard(template_id,standard_id) VALUES(?,?)",id,sid);
            }
        }
        audit(a,id,"inspection.template.created");
        return Map.of("id",id,"revisionNo",next,"tables",info.tables(),"rows",info.rows());
    }
    @GetMapping("/{id}/items") @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:template:write')")
    public List<Map<String,Object>> items(@PathVariable long id){
        one("SELECT id FROM erp_inspection_template WHERE id=?",id);
        return jdbc.queryForList("SELECT i.id,i.template_id,i.sort_order,i.item_name,c.id clause_id,c.clause_no,c.requirement_summary,c.source_locator,c.revision_no clause_revision,c.active clause_active,s.id standard_id,s.standard_no,s.edition,s.title,s.source_url,s.access_url,s.active standard_active,s.verified FROM erp_inspection_template_item i JOIN erp_fire_standard_clause c ON c.id=i.clause_id JOIN erp_fire_standard s ON s.id=c.standard_id WHERE i.template_id=? ORDER BY i.sort_order",id);
    }
    @PostMapping("/structured")
    @PreAuthorize("hasAuthority('report:template:write') and hasAuthority('file:upload')") @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> createStructured(@RequestBody Map<String,Object>b,Authentication a)throws Exception{
        String system=required(b,"system",80),name=required(b,"name",150);
        if(system.matches("(?s).*[\\[\\]{}].*")||system.equals("现场检测照片")||system.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"消防系统名称无效");
        if(!Boolean.TRUE.equals(b.get("cleanConfirmed")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请确认检测项目与条款已经人工核对");
        String notes=Objects.toString(b.get("notes"),"");if(notes.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"备注过长");
        if(!(b.get("items") instanceof List<?> rows)||rows.isEmpty()||rows.size()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请建立1至500条检测项目");
        List<Map<String,Object>> items=new ArrayList<>();Set<Long> standards=new LinkedHashSet<>();
        for(Object raw:rows){
            if(!(raw instanceof Map<?,?> row)||!(row.get("item") instanceof String item)||item.isBlank()||item.length()>240||item.contains("{{erp."))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"检测项目名称无效");
            long clause=fileId(row.get("clauseId"));
            var c=one("SELECT c.id,s.id standard_id FROM erp_fire_standard_clause c JOIN erp_fire_standard s ON s.id=c.standard_id WHERE c.id=? AND c.active=1 AND s.active=1 AND s.verified=1",clause);
            standards.add(((Number)c.get("standard_id")).longValue());items.add(Map.of("item",item.trim(),"clauseId",clause));
        }
        jdbc.queryForObject("SELECT id FROM erp_permission WHERE code='report:template:write' FOR UPDATE",Long.class);
        int next=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM erp_inspection_template WHERE template_kind='system' AND system_name=? AND template_name=?",Integer.class,system,name);
        var bytes=StructuredInspectionChapter.create(system);InspectionTemplateComposer.inspect(bytes,false);
        var stored=storage.storeBytes(bytes,"检测章节-"+system+"-v"+next+".docx",DOCX);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)storage.deleteQuietly(stored.key());}
        });
        jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
        long file=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO erp_inspection_template(template_kind,system_name,template_name,revision_no,file_id,sha256,notes,created_by) VALUES('system',?,?,?,?,?,?,?)",system,name,next,file,stored.sha256(),notes,actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        for(long sid:standards)jdbc.update("INSERT INTO erp_inspection_template_standard(template_id,standard_id) VALUES(?,?)",id,sid);
        int order=0;for(var item:items)jdbc.update("INSERT INTO erp_inspection_template_item(template_id,sort_order,item_name,clause_id) VALUES(?,?,?,?)",id,++order,item.get("item"),item.get("clauseId"));
        audit(a,id,"inspection.template.structured.created");return Map.of("id",id,"revisionNo",next,"itemCount",items.size());
    }
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('report:template:write')") @Transactional
    public Map<String,Object> active(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_inspection_template WHERE id=? FOR UPDATE",id);
        if(!(b.get("active") instanceof Boolean value))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"active 必须为布尔值");
        jdbc.update("UPDATE erp_inspection_template SET active=? WHERE id=?",value,id);audit(a,id,value?"inspection.template.enabled":"inspection.template.disabled");
        return Map.of("id",id,"active",value);
    }
}
