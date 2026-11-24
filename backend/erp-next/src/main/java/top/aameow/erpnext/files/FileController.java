package top.aameow.erpnext.files;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import java.nio.file.Files;
import java.util.*;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final JdbcTemplate jdbc;
    private final PrivateFileStorage storage;
    public FileController(JdbcTemplate jdbc,PrivateFileStorage storage){this.jdbc=jdbc;this.storage=storage;}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private Map<String,Object> one(long id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,original_name,media_type,byte_size file_size,sha256,uploaded_by,created_at,storage_key FROM erp_file_object WHERE id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");
        return rows.get(0);
    }
    private void assertReadable(Map<String,Object> f,Authentication a){
        Object ownerValue=f.get("uploaded_by");
        if(ownerValue instanceof Number owner && owner.longValue()==actor(a))return;
        boolean reportRead=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("report:read"));
        if(reportRead){
            Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_attachment WHERE file_id=?",Integer.class,f.get("id"));
            if(linked!=null&&linked>0)return;
        }
        boolean photoRead=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("report:photo:read")||x.getAuthority().equals("report:photo:write"));
        if(photoRead){
            Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo p JOIN erp_report_photo_batch b ON b.id=p.batch_id WHERE p.file_id=?",Integer.class,f.get("id"));
            if(linked!=null&&linked>0)return;
        }
        boolean contractRead=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("contract:read"));
        if(contractRead){
            Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM erp_contract_file WHERE file_id=?",Integer.class,f.get("id"));
            if(linked!=null&&linked>0)return;
        }
        boolean maintenanceRead=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("maintenance:read"));
        if(maintenanceRead){
            Integer linked=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM erp_maintenance_card WHERE file_id=?)+(SELECT COUNT(*) FROM erp_maintenance_material WHERE file_id=?)+(SELECT COUNT(*) FROM erp_maintenance_material_archive WHERE file_id=?)",Integer.class,f.get("id"),f.get("id"),f.get("id"));
            if(linked!=null&&linked>0)return;
        }
        boolean projectRead=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("project:read"));
        if(projectRead&&reportRead){
            Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM erp_legacy_project_document_file WHERE file_id=?",Integer.class,f.get("id"));
            if(linked!=null&&linked>0)return;
        }
        boolean expenseRead=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("expense:read"));
        if(expenseRead){
            boolean finance=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("expense:approve")||x.getAuthority().equals("expense:pay"));
            Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM erp_expense_attachment ea JOIN erp_expense_claim c ON c.id=ea.claim_id WHERE ea.file_id=? AND (?=1 OR c.applicant_identity_id=?)",Integer.class,f.get("id"),finance?1:0,actor(a));
            if(linked!=null&&linked>0)return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('file:upload')")
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> upload(@RequestPart("file") MultipartFile file,Authentication a){
        PrivateFileStorage.StoredFile stored=storage.store(file);
        try{
            jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",
                stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
            long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('name',?,'size',?,'sha256',?))",
                actor(a),"file.uploaded","erp_file_object",Long.toString(id),UUID.randomUUID().toString(),stored.originalName(),stored.size(),stored.sha256());
            Map<String,Object> result=one(id);result.remove("storage_key");return result;
        }catch(RuntimeException e){storage.deleteQuietly(stored.key());throw e;}
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('file:read')")
    public Map<String,Object> metadata(@PathVariable long id,Authentication a){
        Map<String,Object> f=one(id);assertReadable(f,a);f.remove("storage_key");return f;
    }
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('file:read')")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id,Authentication a)throws Exception{
        Map<String,Object> f=one(id);assertReadable(f,a);
        var path=storage.resolve(f.get("storage_key").toString());
        var resource=new InputStreamResource(Files.newInputStream(path));
        String name=f.get("original_name").toString();
        ContentDisposition disposition=ContentDisposition.attachment().filename(name,java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.get("media_type").toString()))
            .contentLength(((Number)f.get("file_size")).longValue()).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
            .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(resource);
    }
}
