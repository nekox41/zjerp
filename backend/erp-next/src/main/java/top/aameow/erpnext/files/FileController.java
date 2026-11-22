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
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,original_name,media_type,file_size,sha256,uploaded_by,created_at,storage_key FROM erp_file_object WHERE id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");
        return rows.get(0);
    }
    private void assertReadable(Map<String,Object> f,Authentication a){
        long owner=((Number)f.get("uploaded_by")).longValue();
        if(owner==actor(a))return;
        Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_attachment a JOIN erp_service_report r ON r.id=a.report_id WHERE a.file_id=? AND a.version_no=r.current_version_no",Integer.class,f.get("id"));
        if(linked==null||linked==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('file:upload')")
    public Map<String,Object> upload(@RequestPart("file") MultipartFile file,Authentication a){
        PrivateFileStorage.StoredFile stored=storage.store(file);
        try{
            jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,file_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",
                stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
            long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('name',?,'size',?,'sha256',?))",
                actor(a),"file.uploaded","erp_file_object",Long.toString(id),UUID.randomUUID().toString(),stored.originalName(),stored.size(),stored.sha256());
            return one(id);
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
            .header("X-Content-Type-Options","nosniff").body(resource);
    }
}
