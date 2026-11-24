package top.aameow.erpnext.business;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import top.aameow.erpnext.files.PrivateFileStorage;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceDocumentController {
    private final JdbcTemplate jdbc;
    private final PrivateFileStorage storage;
    private final MaintenanceCardRenderer renderer;
    public MaintenanceDocumentController(JdbcTemplate jdbc,PrivateFileStorage storage,MaintenanceCardRenderer renderer){
        this.jdbc=jdbc;this.storage=storage;this.renderer=renderer;
    }
    private static ResponseStatusException bad(String msg){return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private Map<String,Object> one(String sql,Object...args){
        return jdbc.queryForList(sql,args).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在"));
    }
    private void audit(Authentication a,String action,long id,String detail){
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor(a),action,"maintenance_document",Long.toString(id),UUID.randomUUID().toString(),detail);
    }
    private String field(Map<String,Object>b,String key,int max,boolean required){
        String s=Objects.toString(b.get(key),"").trim();
        if((required&&s.isBlank())||s.length()>max)throw bad(key+" 不能为空或超出长度限制");
        return s;
    }
    private long number(Map<String,Object>b,String key){
        try{long n=Long.parseLong(Objects.toString(b.get(key),""));if(n<=0)throw new IllegalArgumentException();return n;}catch(IllegalArgumentException e){throw bad(key+" 无效");}
    }
    private Map<String,Object> scope(long project,long contract,boolean lock){
        return one("""
            SELECT p.name project_name,p.manager_employee_id,e.full_name leader,e.phone,
                   c.contract_no,c.starts_on,c.ends_on,c.status
            FROM erp_contract_project cp JOIN erp_project p ON p.id=cp.project_id
            JOIN erp_contract c ON c.id=cp.contract_id LEFT JOIN erp_employee e ON e.id=p.manager_employee_id
            WHERE cp.project_id=? AND cp.contract_id=? AND c.service_type='maintenance'
            """+(lock?" FOR UPDATE":""),project,contract);
    }
    private String code(long n){return String.format(Locale.ROOT,"%07d",n);}
    private long lastNumber(boolean lock){return jdbc.queryForObject("SELECT last_number FROM erp_maintenance_card_sequence WHERE id=1"+(lock?" FOR UPDATE":""),Long.class);}
    private String nextCode(long n){do{n=Math.addExact(n,1);}while(jdbc.queryForObject("SELECT COUNT(*) FROM erp_maintenance_card WHERE code=?",Integer.class,code(n))>0);return code(n);}
    private record CardInput(MaintenanceCardRenderer.Card card,LocalDate start,LocalDate end){}
    private CardInput input(Map<String,Object>b,Map<String,Object>scope,String code){
        LocalDate start,end;
        try{start=LocalDate.parse(field(b,"startsOn",10,true));end=LocalDate.parse(field(b,"endsOn",10,true));}catch(java.time.DateTimeException ex){throw bad("维保日期无效");}
        if(end.isBefore(start))throw bad("结束日期不能早于开始日期");
        return new CardInput(new MaintenanceCardRenderer.Card(
            Objects.toString(scope.get("project_name"),""),field(b,"company",255,true),
            field(b,"leader",100,true),field(b,"phone",255,true),code,start+" 至 "+end,field(b,"systems",4000,true)),start,end);
    }
    @GetMapping("/projects/{project}/contracts/{contract}")
    @PreAuthorize("hasAuthority('maintenance:read')")
    public Map<String,Object> documents(@PathVariable long project,@PathVariable long contract){
        Map<String,Object> result=new LinkedHashMap<>(scope(project,contract,false));
        result.put("nextCode",nextCode(lastNumber(false)));
        result.put("cards",jdbc.queryForList("SELECT c.*,f.original_name FROM erp_maintenance_card c JOIN erp_file_object f ON f.id=c.file_id WHERE c.project_id=? AND c.contract_id=? ORDER BY c.id DESC",project,contract));
        result.put("materials",jdbc.queryForList("SELECT a.*,f.original_name,f.sha256 FROM erp_maintenance_material_archive a JOIN erp_file_object f ON f.id=a.file_id WHERE a.project_id=? AND a.contract_id=? ORDER BY a.id DESC",project,contract));
        return result;
    }
    @PostMapping("/projects/{project}/contracts/{contract}/preview")
    @PreAuthorize("hasAuthority('maintenance:write')")
    public ResponseEntity<byte[]> preview(@PathVariable long project,@PathVariable long contract,@RequestBody Map<String,Object>b)throws IOException{
        Map<String,Object> s=scope(project,contract,false);String no=field(b,"code",100,false);
        byte[] pdf=renderer.render(input(b,s,no.isBlank()?nextCode(lastNumber(false)):no).card());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=maintenance-card.pdf").body(pdf);
    }
    @PostMapping("/projects/{project}/contracts/{contract}/cards")
    @PreAuthorize("hasAuthority('maintenance:write')")
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> create(@PathVariable long project,@PathVariable long contract,@RequestBody Map<String,Object>b,Authentication a)throws IOException{
        Map<String,Object> s=scope(project,contract,true);
        if(!"active".equals(s.get("status")))throw bad("正式生成须选择履行中的维保合同");
        long last=lastNumber(true);String no=field(b,"code",100,false);
        if(no.isBlank())no=nextCode(last);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM erp_maintenance_card WHERE code=?",Integer.class,no)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"维保卡编号已使用");
        CardInput v=input(b,s,no);var card=v.card();
        byte[] pdf=renderer.render(card);
        if(no.matches("[0-9]{1,18}"))jdbc.update("UPDATE erp_maintenance_card_sequence SET last_number=? WHERE id=1",Math.max(last,Long.parseLong(no)));
        var stored=storage.storeBytes(pdf,"maintenance-card.pdf","application/pdf");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.deleteQuietly(stored.key());}
        });
        jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",
            stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
        long file=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO erp_maintenance_card(project_id,contract_id,file_id,code,project_name,company,leader,phone,systems,starts_on,ends_on,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            project,contract,file,no,card.name(),card.company(),card.leader(),card.phone(),card.system1(),v.start(),v.end(),actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(?,?,?,?,?)",contract,file,"maintenance_card","维保公示卡 "+no,actor(a));
        audit(a,"maintenance.card.created",id,no);
        return one("SELECT * FROM erp_maintenance_card WHERE id=?",id);
    }
    @PutMapping("/cards/{id}/status")
    @PreAuthorize("hasAuthority('maintenance:write')")
    @Transactional
    public Map<String,Object> status(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_maintenance_card WHERE id=? FOR UPDATE",id);
        for(String key:List.of("printed","sent"))if(b.containsKey(key)&&!(b.get(key) instanceof Boolean))throw bad("状态必须为布尔值");
        jdbc.update("UPDATE erp_maintenance_card SET printed=COALESCE(?,printed),sent=COALESCE(?,sent) WHERE id=?",b.get("printed"),b.get("sent"),id);
        audit(a,"maintenance.card.status",id,b.toString());
        return one("SELECT * FROM erp_maintenance_card WHERE id=?",id);
    }
    @GetMapping("/contacts") @PreAuthorize("hasAuthority('maintenance:read')")
    public List<Map<String,Object>> contacts(){return jdbc.queryForList("SELECT * FROM erp_maintenance_contact ORDER BY active DESC,name,id");}
    @PostMapping("/contacts") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> addContact(@RequestBody Map<String,Object>b,Authentication a){
        jdbc.update("INSERT INTO erp_maintenance_contact(name,phone) VALUES(?,?)",field(b,"name",100,true),field(b,"phone",50,true));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);audit(a,"maintenance.contact.created",id,"");
        return one("SELECT * FROM erp_maintenance_contact WHERE id=?",id);
    }
    @PutMapping("/contacts/{id}") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> editContact(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_maintenance_contact WHERE id=? FOR UPDATE",id);
        if(b.containsKey("active")&&!(b.get("active") instanceof Boolean))throw bad("状态无效");
        jdbc.update("UPDATE erp_maintenance_contact SET name=?,phone=?,active=COALESCE(?,active) WHERE id=?",field(b,"name",100,true),field(b,"phone",50,true),b.get("active"),id);
        audit(a,"maintenance.contact.updated",id,"");return one("SELECT * FROM erp_maintenance_contact WHERE id=?",id);
    }
    @GetMapping("/materials") @PreAuthorize("hasAuthority('maintenance:read')")
    public List<Map<String,Object>> materials(){return jdbc.queryForList("SELECT m.*,f.original_name,f.sha256 FROM erp_maintenance_material m JOIN erp_file_object f ON f.id=m.file_id ORDER BY m.active DESC,m.id DESC");}
    @PostMapping("/materials") @PreAuthorize("hasAuthority('maintenance:write') and hasAuthority('file:upload')") @Transactional
    public Map<String,Object> addMaterial(@RequestBody Map<String,Object>b,Authentication a){
        long file=number(b,"fileId");String name=field(b,"name",200,true);
        Map<String,Object> f=one("SELECT * FROM erp_file_object WHERE id=?",file);
        if(((Number)f.get("uploaded_by")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能登记自己上传的文件");
        if(!"application/pdf".equals(f.get("media_type")))throw bad("附带资料必须为 PDF");
        try(var pdf=Loader.loadPDF(storage.resolve(f.get("storage_key").toString()).toFile())){
            if(pdf.isEncrypted()||pdf.getNumberOfPages()<1||pdf.getNumberOfPages()>300)throw bad("仅支持未加密的 1 至 300 页 PDF");
        }catch(IOException e){throw bad("无效或加密的 PDF 文件");}
        jdbc.update("INSERT INTO erp_maintenance_material(name,file_id,notes,created_by) VALUES(?,?,?,?)",name,file,field(b,"notes",2000,false),actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);audit(a,"maintenance.material.created",id,name);
        return one("SELECT * FROM erp_maintenance_material WHERE id=?",id);
    }
    @PutMapping("/materials/{id}") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> editMaterial(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_maintenance_material WHERE id=? FOR UPDATE",id);
        if(b.containsKey("active")&&!(b.get("active") instanceof Boolean))throw bad("状态无效");
        jdbc.update("UPDATE erp_maintenance_material SET name=?,notes=?,active=COALESCE(?,active) WHERE id=?",field(b,"name",200,true),field(b,"notes",2000,false),b.get("active"),id);
        audit(a,"maintenance.material.updated",id,"");return one("SELECT * FROM erp_maintenance_material WHERE id=?",id);
    }
    @PostMapping("/projects/{project}/contracts/{contract}/materials")
    @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> archive(@PathVariable long project,@PathVariable long contract,@RequestBody Map<String,Object>b,Authentication a){
        scope(project,contract,true);long material=number(b,"materialId");
        Map<String,Object> m=one("SELECT * FROM erp_maintenance_material WHERE id=? AND active=TRUE FOR UPDATE",material);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM erp_maintenance_material_archive WHERE contract_id=? AND project_id=? AND material_id=?",Integer.class,contract,project,material)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"该资料已归档");
        jdbc.update("INSERT INTO erp_maintenance_material_archive(contract_id,project_id,material_id,file_id,title,created_by) VALUES(?,?,?,?,?,?)",contract,project,material,m.get("file_id"),m.get("name"),actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT IGNORE INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(?,?,?,?,?)",contract,m.get("file_id"),"project_material",m.get("name"),actor(a));
        audit(a,"maintenance.material.archived",id,"contract="+contract+",project="+project);
        return one("SELECT * FROM erp_maintenance_material_archive WHERE id=?",id);
    }
}
