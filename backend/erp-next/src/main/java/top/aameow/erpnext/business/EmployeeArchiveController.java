package top.aameow.erpnext.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.files.PrivateFileStorage;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Personnel document archive, separate from login-account management. */
@RestController
@RequestMapping("/api/business/employees")
public class EmployeeArchiveController {
    private final JdbcTemplate jdbc;
    private final PrivateFileStorage storage;
    private final ObjectMapper mapper;
    public EmployeeArchiveController(JdbcTemplate jdbc,PrivateFileStorage storage,ObjectMapper mapper){
        this.jdbc=jdbc;this.storage=storage;this.mapper=mapper;
    }
    private ResponseStatusException bad(String msg){return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
    private Map<String,Object> one(String sql,Object...args){
        return jdbc.queryForList(sql,args).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"员工或附件不存在"));
    }
    private boolean allowed(Authentication a,String permission){return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals(permission));}
    private void privateResponse(HttpServletResponse r){r.setHeader(HttpHeaders.CACHE_CONTROL,"private, no-store");}
    private static final String BASIC="id,full_name,gender,age,employee_type,status,(NULLIF(BTRIM(certificate_no),'') IS NOT NULL) AS has_certificate";
    @GetMapping @PreAuthorize("hasAuthority('employee:read')")
    public List<Map<String,Object>> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String type,Authentication a,HttpServletResponse response){
        privateResponse(response);
        if(!type.isBlank()&&!Set.of("operator","engineer").contains(type))throw bad("员工类型无效");
        boolean full=allowed(a,"employee:write");
        String term="%"+q.trim()+"%";
        if(full)return jdbc.queryForList("SELECT "+BASIC+",phone,certificate_no FROM erp_employee WHERE (?='' OR employee_type=?) AND (full_name ILIKE ? OR phone ILIKE ? OR certificate_no ILIKE ?) ORDER BY id DESC LIMIT 500",type,type,term,term,term);
        return jdbc.queryForList("SELECT "+BASIC+" FROM erp_employee WHERE (?='' OR employee_type=?) AND full_name ILIKE ? ORDER BY id DESC LIMIT 500",type,type,term);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('employee:read')")
    public Map<String,Object> detail(@PathVariable long id,Authentication a,HttpServletResponse response){
        privateResponse(response);return detail(id,allowed(a,"employee:write"));
    }
    private Map<String,Object> detail(long id,boolean full){
        Map<String,Object> r=new LinkedHashMap<>(one("SELECT "+BASIC+(full?",phone,id_number,certificate_no,extra_fields":"")+" FROM erp_employee WHERE id=?",id));
        if(full){
            try{r.put("extra_fields",mapper.readValue(r.get("extra_fields").toString(),new TypeReference<List<Map<String,String>>>(){}));}
            catch(Exception e){throw new IllegalStateException("员工扩展字段格式错误",e);}
            r.put("documents",jdbc.queryForList("SELECT id,document_type,original_name,media_type,byte_size,created_at FROM erp_employee_document WHERE employee_id=? ORDER BY id",id));
        }
        return r;
    }
    private Object field(Map<String,Object>b,Map<String,Object>old,String key,String column){return b.containsKey(key)?b.get(key):old.get(column);}
    private String str(Object raw,int max,boolean required,String label){
        String v=Objects.toString(raw,"").trim();
        if((required&&v.isEmpty())||v.length()>max||v.chars().anyMatch(Character::isISOControl))throw bad(label+"不能为空或格式、长度不正确");
        return v.isEmpty()?null:v;
    }
    private Integer age(Object raw){
        if(raw==null||raw.toString().isBlank())return null;
        try{int n=Integer.parseInt(raw.toString());if(n<0||n>120)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw bad("年龄必须为0至120之间的整数");}
    }
    private String extra(Object raw){
        if(raw==null)return "[]";
        if(raw instanceof String s){try{raw=mapper.readValue(s,new TypeReference<List<Map<String,String>>>(){});}catch(Exception e){throw bad("自定义资料格式无效");}}
        if(!(raw instanceof List<?> rows)||rows.size()>50)throw bad("自定义资料最多50项");
        List<Map<String,String>> out=new ArrayList<>();Set<String> keys=new HashSet<>();
        for(Object row:rows){
            if(!(row instanceof Map<?,?> m))throw bad("自定义资料格式无效");
            String k=str(m.get("key"),80,true,"自定义名称");
            if(!keys.add(k.toLowerCase(Locale.ROOT)))throw bad("自定义资料名称重复");
            String v=str(m.get("value"),4000,false,"自定义内容");
            out.add(Map.of("key",k,"value",Objects.toString(v,"")));
        }
        try{return mapper.writeValueAsString(out);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private Map<String,Object> parse(String json){
        try{return mapper.readValue(json,new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw bad("员工档案格式无效");}
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object>b,Authentication a){return save(null,b,null,null,a);}
    @PutMapping(value="/{id}",consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){return save(id,b,null,null,a);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> createWithFiles(@RequestPart("profile") String profile,@RequestPart(value="certificateFiles",required=false) List<MultipartFile> certificates,@RequestPart(value="identityFiles",required=false) List<MultipartFile> identity,Authentication a){return save(null,parse(profile),certificates,identity,a);}
    @PutMapping(value="/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> updateWithFiles(@PathVariable long id,@RequestPart("profile") String profile,@RequestPart(value="certificateFiles",required=false) List<MultipartFile> certificates,@RequestPart(value="identityFiles",required=false) List<MultipartFile> identity,Authentication a){return save(id,parse(profile),certificates,identity,a);}
    private Map<String,Object> save(Long id,Map<String,Object>b,List<MultipartFile> certificates,List<MultipartFile> identity,Authentication a){
        Map<String,Object> old=id==null?Map.of():one("SELECT * FROM erp_employee WHERE id=? FOR UPDATE",id);
        String name=str(field(b,old,"fullName","full_name"),200,true,"姓名");
        String type=str(field(b,old,"employeeType","employee_type"),24,true,"类型");
        if(!Set.of("operator","engineer").contains(type))throw bad("类型只能为操作员或工程师");
        String gender=str(field(b,old,"gender","gender"),16,false,"性别");
        if(gender!=null&&!Set.of("male","female").contains(gender))throw bad("性别无效");
        Integer age=age(field(b,old,"age","age"));
        String idNumber=str(field(b,old,"idNumber","id_number"),40,false,"身份证号码");
        String phone=str(field(b,old,"phone","phone"),40,false,"手机号码");
        String certificate=str(field(b,old,"certificateNo","certificate_no"),120,false,"证书编号");
        String extras=extra(field(b,old,"extraFields","extra_fields"));
        if(id==null)id=jdbc.queryForObject("INSERT INTO erp_employee(full_name,employee_type,gender,age,id_number,phone,certificate_no,extra_fields) VALUES(?,?,?,?,?,?,?,?) RETURNING id",Long.class,name,type,gender,age,idNumber,phone,certificate,extras);
        else jdbc.update("UPDATE erp_employee SET full_name=?,employee_type=?,gender=?,age=?,id_number=?,phone=?,certificate_no=?,extra_fields=? WHERE id=?",name,type,gender,age,idNumber,phone,certificate,extras,id);
        // Internal account relation is only available to the super account, never part of the archive form.
        if(b.containsKey("identityId")){
            if(!allowed(a,"identity:write"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅超级账户可关联审核账号");
            Object raw=b.get("identityId");Long account=raw==null?null:positive(raw);
            if(account!=null){one("SELECT id FROM erp_identity WHERE id=? FOR UPDATE",account);bind(id,account);}
            else jdbc.update("UPDATE erp_employee SET identity_id=NULL WHERE id=?",id);
        }
        if(b.containsKey("keepDocumentIds")){
            if(!(b.get("keepDocumentIds") instanceof List<?> list))throw bad("附件清单无效");
            Set<Long> keep=new HashSet<>();for(Object x:list)keep.add(positive(x));
            List<Map<String,Object>> previous=jdbc.queryForList("SELECT id,storage_key FROM erp_employee_document WHERE employee_id=?",id);
            Set<Long> existing=new HashSet<>();for(var d:previous)existing.add(((Number)d.get("id")).longValue());
            if(!existing.containsAll(keep))throw bad("附件不属于该员工");
            for(var d:previous)if(!keep.contains(((Number)d.get("id")).longValue())){
                jdbc.update("DELETE FROM erp_employee_document WHERE id=? AND employee_id=?",d.get("id"),id);
                String key=d.get("storage_key").toString();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){storage.deleteQuietly(key);}});
            }
        }
        addFiles(id,"certificate",certificates);addFiles(id,"identity",identity);
        return detail(id,true);
    }
    private long positive(Object v){try{long n=Long.parseLong(Objects.toString(v,""));if(n<=0)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw bad("记录编号无效");}}
    private void addFiles(long id,String kind,List<MultipartFile> files){
        if(files==null||files.isEmpty())return;
        int existing=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM erp_employee_document WHERE employee_id=? AND document_type=?",Integer.class,id,kind));
        if(existing+files.size()>10)throw bad("每类证件最多10个附件");
        for(MultipartFile file:files){
            if(!Set.of("application/pdf","image/jpeg","image/png").contains(Objects.toString(file.getContentType(),"")))throw bad("证件附件仅支持PDF、JPG、PNG");
            var stored=storage.store(file);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)storage.deleteQuietly(stored.key());}});
            jdbc.update("INSERT INTO erp_employee_document(employee_id,document_type,storage_key,original_name,media_type,byte_size,sha256) VALUES(?,?,?,?,?,?,?)",id,kind,stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256());
        }
    }
    @GetMapping("/{id}/documents/{documentId}/download") @PreAuthorize("hasAuthority('employee:write')")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id,@PathVariable long documentId)throws Exception{
        var doc=one("SELECT * FROM erp_employee_document WHERE id=? AND employee_id=?",documentId,id);
        var path=storage.resolve(doc.get("storage_key").toString());
        long size=((Number)doc.get("byte_size")).longValue();
        if(Files.size(path)!=size)throw new ResponseStatusException(HttpStatus.CONFLICT,"证件附件大小与记录不一致");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(doc.get("media_type").toString())).contentLength(size)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(doc.get("original_name").toString(),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(new InputStreamResource(Files.newInputStream(path)));
    }
    @GetMapping("/account-links") @PreAuthorize("hasAuthority('identity:write')")
    public List<Map<String,Object>> accountLinks(){return jdbc.queryForList("SELECT id,full_name,employee_type,identity_id FROM erp_employee ORDER BY full_name,id");}
    private void bind(long employee,long account){
        Map<String,Object> e=one("SELECT identity_id FROM erp_employee WHERE id=? FOR UPDATE",employee);
        if(e.get("identity_id")!=null&&((Number)e.get("identity_id")).longValue()!=account)throw bad("此员工已关联其他账户，请先解除关联");
        jdbc.update("UPDATE erp_employee SET identity_id=NULL WHERE identity_id=? AND id<>?",account,employee);
        jdbc.update("UPDATE erp_employee SET identity_id=? WHERE id=?",account,employee);
    }
    @PutMapping("/account-links/{account}") @PreAuthorize("hasAuthority('identity:write')") @Transactional
    public void linkAccount(@PathVariable long account,@RequestBody Map<String,Object>b){
        one("SELECT id FROM erp_identity WHERE id=? FOR UPDATE",account);
        Object raw=b.get("employeeId");
        if(raw==null)jdbc.update("UPDATE erp_employee SET identity_id=NULL WHERE identity_id=?",account);
        else bind(positive(raw),account);
    }
}
