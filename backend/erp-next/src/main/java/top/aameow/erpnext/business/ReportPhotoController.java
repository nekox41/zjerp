package top.aameow.erpnext.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import top.aameow.erpnext.files.PrivateFileStorage;

import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/report-photos")
public class ReportPhotoController {
    private static final String DOCX_MIME="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final long MAX_BATCH_BYTES=220L*1024*1024;
    private static final int MAX_PHOTOS=300;
    private final JdbcTemplate jdbc;
    private final PrivateFileStorage storage;
    private final ReportPhotoDocxService docx;
    private final ObjectMapper mapper;

    public ReportPhotoController(JdbcTemplate jdbc,PrivateFileStorage storage,ReportPhotoDocxService docx,ObjectMapper mapper) {
        this.jdbc=jdbc; this.storage=storage; this.docx=docx; this.mapper=mapper;
    }
    private long actor(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); }
    private String required(Map<String,Object>b,String key) {
        Object value=b.get(key);
        if(value==null||value.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 不能为空");
        return value.toString().trim();
    }
    private Map<String,Object> one(String sql,Object...args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.get(0);
    }
    private Map<String,Object> batch(long id) {
        return one("""
          SELECT b.*,f.original_name template_name,f.byte_size template_size,r.report_no,r.status report_status,
                 r.current_version_no,r.project_id report_project_id
          FROM erp_report_photo_batch b
          JOIN erp_file_object f ON f.id=b.template_file_id
          JOIN erp_service_report r ON r.id=b.report_id WHERE b.id=?
          """,id);
    }
    private void assertReportEditable(long reportId,long projectId,Integer expectedVersion) {
        Map<String,Object> r=one("SELECT project_id,status,current_version_no FROM erp_service_report WHERE id=? FOR UPDATE",reportId);
        if(((Number)r.get("project_id")).longValue()!=projectId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"报告不属于所选项目");
        if(!Set.of("draft","returned").contains(r.get("status").toString()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿或退回的报告可以插入照片");
        if(expectedVersion!=null && ((Number)r.get("current_version_no")).intValue()!=expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"报告版本已更新，请刷新后重试");
    }
    private long registerFile(PrivateFileStorage.StoredFile f,long who) {
        jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",
            f.key(),f.originalName(),f.mediaType(),f.size(),f.sha256(),who);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
    private void audit(Authentication a,String action,String type,long id,String summary) {
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor(a),action,type,Long.toString(id),UUID.randomUUID().toString(),summary);
    }
    private boolean isDocx(MultipartFile f) {
        return f!=null&&!f.isEmpty()&&f.getOriginalFilename()!=null&&f.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".docx");
    }
    private boolean isPhoto(MultipartFile f) {
        if(f==null||f.isEmpty()||f.getOriginalFilename()==null) return false;
        String n=f.getOriginalFilename().toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png");
    }
    private List<Map<String,Object>> photos(long batchId) {
        return jdbc.queryForList("""
          SELECT p.id,p.batch_id,p.file_id,p.system_code,p.sort_order,p.notes,p.is_deleted,
                 f.original_name,f.media_type,f.byte_size file_size,f.sha256
          FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id
          WHERE p.batch_id=? AND p.is_deleted=FALSE ORDER BY COALESCE(p.system_code,'~'),p.sort_order,p.id
          """,batchId);
    }
    private Map<String,Object> photo(long id) {
        return one("""
          SELECT p.id,p.batch_id,p.file_id,p.system_code,p.sort_order,p.notes,p.is_deleted,
                 f.original_name,f.media_type,f.byte_size file_size,f.sha256
          FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.id=?
          """,id);
    }
    private Map<String,Object> detail(long id) throws Exception {
        Map<String,Object> result=new LinkedHashMap<>(batch(id));
        result.put("photos",photos(id));
        String key=one("SELECT storage_key FROM erp_file_object WHERE id=?",result.get("template_file_id")).get("storage_key").toString();
        try(var input=Files.newInputStream(storage.resolve(key))) { result.put("systems",docx.inspect(input)); }
        result.put("exports",jdbc.queryForList("""
          SELECT x.id,x.report_id,x.report_version_no,x.file_id,x.mode_json,x.created_at,
                 f.original_name,f.byte_size file_size,f.sha256
          FROM erp_report_photo_export x JOIN erp_file_object f ON f.id=x.file_id
          WHERE x.batch_id=? ORDER BY x.id DESC
          """,id));
        return result;
    }

    @PostMapping(value="/batches",consumes="multipart/form-data")
    @PreAuthorize("hasAuthority('report_photo:write')")
    @Transactional
    public Map<String,Object> createBatch(@RequestParam long projectId,@RequestParam long reportId,
        @RequestParam(required=false) Long siteVisitId,@RequestParam String title,
        @RequestPart("template") MultipartFile template,Authentication a) throws Exception {
        if(!isDocx(template)||template.getSize()>25L*1024*1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择 25MB 以内的 DOCX 报告模板");
        one("SELECT id FROM erp_project WHERE id=?",projectId);
        assertReportEditable(reportId,projectId,null);
        if(siteVisitId!=null) {
            Map<String,Object> v=one("SELECT project_id FROM erp_site_visit WHERE id=?",siteVisitId);
            if(((Number)v.get("project_id")).longValue()!=projectId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场服务记录不属于所选项目");
        }
        if(title==null||title.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"批次名称不能为空");
        PrivateFileStorage.StoredFile stored=storage.store(template);
        try {
            List<Map<String,Object>> anchors;
            try(var input=Files.newInputStream(stored.path())) { anchors=docx.inspect(input); }
            long templateId=registerFile(stored,actor(a));
            jdbc.update("INSERT INTO erp_report_photo_batch(project_id,site_visit_id,report_id,template_file_id,title,created_by) VALUES(?,?,?,?,?,?)",
                projectId,siteVisitId,reportId,templateId,title.trim(),actor(a));
            long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            audit(a,"report.photo_batch.created","erp_report_photo_batch",id,title.trim());
            Map<String,Object> result=detail(id);
            result.put("systems",anchors);
            return result;
        } catch(Exception e) {
            storage.deleteQuietly(stored.key());
            throw e;
        }
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('report_photo:read')")
    public List<Map<String,Object>> listBatches(@RequestParam long projectId) {
        one("SELECT id FROM erp_project WHERE id=?",projectId);
        return jdbc.queryForList("""
          SELECT b.id,b.project_id,b.site_visit_id,b.report_id,b.title,b.status,b.created_at,
                 f.original_name template_name,r.report_no,r.status report_status,
                 (SELECT COUNT(*) FROM erp_report_photo p WHERE p.batch_id=b.id AND p.is_deleted=FALSE) photo_count
          FROM erp_report_photo_batch b JOIN erp_file_object f ON f.id=b.template_file_id
          JOIN erp_service_report r ON r.id=b.report_id WHERE b.project_id=? ORDER BY b.created_at DESC,b.id DESC
          """,projectId);
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAuthority('report_photo:read')")
    public Map<String,Object> getBatch(@PathVariable long id) throws Exception { return detail(id); }

    @PostMapping(value="/batches/{id}/photos",consumes="multipart/form-data")
    @PreAuthorize("hasAuthority('report_photo:write')")
    @Transactional
    public Map<String,Object> addPhotos(@PathVariable long id,@RequestPart("photos") List<MultipartFile> files,Authentication a) throws Exception {
        Map<String,Object> b=batch(id);
        assertReportEditable(((Number)b.get("report_id")).longValue(),((Number)b.get("project_id")).longValue(),null);
        if(files==null||files.isEmpty()||files.size()>MAX_PHOTOS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择 1 至 300 张照片");
        int current=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=? AND is_deleted=FALSE",Integer.class,id);
        if(current+files.size()>MAX_PHOTOS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"一个照片批次最多 300 张");
        long total=jdbc.queryForObject("SELECT COALESCE(SUM(f.byte_size),0) FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.batch_id=? AND p.is_deleted=FALSE",Long.class,id);
        List<PrivateFileStorage.StoredFile> storedFiles=new ArrayList<>();
        try {
            int order=jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM erp_report_photo WHERE batch_id=? AND is_deleted=FALSE",Integer.class,id);
            for(MultipartFile file:files) {
                if(!isPhoto(file)||file.getSize()>20L*1024*1024)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持单张不超过 20MB 的 JPG/PNG 照片");
                total+=file.getSize();
                if(total>MAX_BATCH_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"一个照片批次总大小不能超过 220MB");
                PrivateFileStorage.StoredFile stored=storage.store(file);
                storedFiles.add(stored);
                long fileId=registerFile(stored,actor(a));
                jdbc.update("INSERT INTO erp_report_photo(batch_id,file_id,sort_order) VALUES(?,?,?)",id,fileId,order++);
            }
            audit(a,"report.photo_batch.photos_added","erp_report_photo_batch",id,"新增 "+files.size()+" 张照片");
            return detail(id);
        } catch(Exception e) {
            for(PrivateFileStorage.StoredFile f:storedFiles) storage.deleteQuietly(f.key());
            throw e;
        }
    }

    @GetMapping(value="/photos/{id}/thumbnail",produces="image/jpeg")
    @PreAuthorize("hasAuthority('report_photo:read')")
    public org.springframework.http.ResponseEntity<byte[]> thumbnail(@PathVariable long id) throws Exception {
        Map<String,Object> p=photo(id);
        if(Boolean.TRUE.equals(p.get("is_deleted"))) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"照片不存在");
        Map<String,Object> f=one("SELECT storage_key FROM erp_file_object WHERE id=?",p.get("file_id"));
        BufferedImage source=ImageIO.read(Files.newInputStream(storage.resolve(f.get("storage_key").toString())));
        if(source==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片无法解码");
        double scale=Math.min(1.0,Math.min(720.0/source.getWidth(),540.0/source.getHeight()));
        int w=Math.max(1,(int)Math.round(source.getWidth()*scale)),h=Math.max(1,(int)Math.round(source.getHeight()*scale));
        BufferedImage thumb=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=thumb.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,w,h);
        g.drawImage(source,0,0,w,h,null);g.dispose();
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            ImageIO.write(thumb,"jpg",out);
            return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_JPEG)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL,"private, no-store").body(out.toByteArray());
        }
    }

    @PutMapping("/photos/{id}")
    @PreAuthorize("hasAuthority('report_photo:write')")
    @Transactional
    public Map<String,Object> updatePhoto(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> p=photo(id),batch=batch(((Number)p.get("batch_id")).longValue());
        assertReportEditable(((Number)batch.get("report_id")).longValue(),((Number)batch.get("project_id")).longValue(),null);
        String code=b.get("systemCode")==null||b.get("systemCode").toString().isBlank()?null:b.get("systemCode").toString();
        if(code!=null&&ReportPhotoDocxService.SYSTEMS.stream().noneMatch(s->s.code().equals(code)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效的消防系统分类");
        int order=b.get("sortOrder")==null?((Number)p.get("sort_order")).intValue():Integer.parseInt(b.get("sortOrder").toString());
        jdbc.update("UPDATE erp_report_photo SET system_code=?,sort_order=?,notes=? WHERE id=?",code,order,b.get("notes"),id);
        audit(a,"report.photo.updated","erp_report_photo",id,"更新照片分类或顺序");
        return photo(id);
    }

    @PostMapping("/batches/{id}/classify")
    @PreAuthorize("hasAuthority('report_photo:write')")
    @Transactional
    public Map<String,Object> classify(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) throws Exception {
        Map<String,Object> batch=batch(id);
        assertReportEditable(((Number)batch.get("report_id")).longValue(),((Number)batch.get("project_id")).longValue(),null);
        String code=required(b,"systemCode");
        if(ReportPhotoDocxService.SYSTEMS.stream().noneMatch(s->s.code().equals(code)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效的消防系统分类");
        if(!(b.get("photoIds") instanceof List<?> ids)||ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择需要分类的照片");
        for(Object raw:ids) {
            long photoId=Long.parseLong(raw.toString());
            Map<String,Object> p=photo(photoId);
            if(((Number)p.get("batch_id")).longValue()!=id||Boolean.TRUE.equals(p.get("is_deleted")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片不属于当前批次");
            jdbc.update("UPDATE erp_report_photo SET system_code=? WHERE id=?",code,photoId);
        }
        audit(a,"report.photo.batch_classified","erp_report_photo_batch",id,"批量分类 "+ids.size()+" 张照片");
        return detail(id);
    }

    @PostMapping("/batches/{id}/reorder")
    @PreAuthorize("hasAuthority('report_photo:write')")
    @Transactional
    public Map<String,Object> reorder(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) throws Exception {
        Map<String,Object> batch=batch(id);
        assertReportEditable(((Number)batch.get("report_id")).longValue(),((Number)batch.get("project_id")).longValue(),null);
        if(!(b.get("photoIds") instanceof List<?> ids)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"缺少照片顺序");
        int order=0;
        for(Object raw:ids) {
            long photoId=Long.parseLong(raw.toString());
            Map<String,Object> p=photo(photoId);
            if(((Number)p.get("batch_id")).longValue()!=id||Boolean.TRUE.equals(p.get("is_deleted")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片不属于当前批次");
            jdbc.update("UPDATE erp_report_photo SET sort_order=? WHERE id=?",order++,photoId);
        }
        audit(a,"report.photo_batch.reordered","erp_report_photo_batch",id,"调整照片顺序");
        return detail(id);
    }

    @DeleteMapping("/photos/{id}")
    @PreAuthorize("hasAuthority('report_photo:write')")
    @Transactional
    public void deletePhoto(@PathVariable long id,Authentication a) {
        Map<String,Object> p=photo(id),b=batch(((Number)p.get("batch_id")).longValue());
        assertReportEditable(((Number)b.get("report_id")).longValue(),((Number)b.get("project_id")).longValue(),null);
        jdbc.update("UPDATE erp_report_photo SET is_deleted=TRUE WHERE id=?",id);
        audit(a,"report.photo.deleted","erp_report_photo",id,"从批次移除照片（保留原文件）");
    }

    @PostMapping("/batches/{id}/export")
    @PreAuthorize("hasAuthority('report_photo:write')")
    @Transactional
    public Map<String,Object> export(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) throws Exception {
        Map<String,Object> batch=batch(id);
        long projectId=((Number)batch.get("project_id")).longValue(),reportId=((Number)batch.get("report_id")).longValue();
        Integer expected=b.get("expectedReportVersionNo")==null?null:Integer.parseInt(b.get("expectedReportVersionNo").toString());
        assertReportEditable(reportId,projectId,expected);
        List<Map<String,Object>> rows=photos(id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前批次没有照片");
        Map<String,List<ReportPhotoDocxService.PhotoImage>> grouped=new LinkedHashMap<>();
        List<Map<String,Object>> manifestPhotos=new ArrayList<>();
        long total=0;
        for(Map<String,Object> p:rows) {
            String code=(String)p.get("system_code");
            if(code==null||code.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仍有未分类照片，不能插入报告");
            if(ReportPhotoDocxService.SYSTEMS.stream().noneMatch(s->s.code().equals(code)))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"存在无效系统分类");
            Map<String,Object> f=one("SELECT storage_key,original_name,byte_size FROM erp_file_object WHERE id=?",p.get("file_id"));
            total+=((Number)f.get("byte_size")).longValue();
            if(total>MAX_BATCH_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片批次超过 220MB");
            byte[] bytes=Files.readAllBytes(storage.resolve(f.get("storage_key").toString()));
            grouped.computeIfAbsent(code,k->new ArrayList<>()).add(new ReportPhotoDocxService.PhotoImage(f.get("original_name").toString(),bytes));
            manifestPhotos.add(Map.of("photoId",p.get("id"),"fileId",p.get("file_id"),"name",f.get("original_name"),
                "systemCode",code,"sortOrder",p.get("sort_order"),"sha256",p.get("sha256")));
        }
        Map<String,String> modes=new LinkedHashMap<>();
        if(b.get("modes") instanceof Map<?,?> supplied)
            for(Map.Entry<?,?> entry:supplied.entrySet()) modes.put(entry.getKey().toString(),entry.getValue().toString());
        for(String code:grouped.keySet()) modes.putIfAbsent(code,"replace");
        String templateKey=one("SELECT storage_key FROM erp_file_object WHERE id=?",batch.get("template_file_id")).get("storage_key").toString();
        byte[] templateBytes=Files.readAllBytes(storage.resolve(templateKey));
        List<Map<String,Object>> anchors=docx.inspect(new java.io.ByteArrayInputStream(templateBytes));
        Set<String> available=new HashSet<>();
        for(Map<String,Object> anchor:anchors) if(Boolean.TRUE.equals(anchor.get("headingFound"))) available.add(anchor.get("systemCode").toString());
        for(String code:grouped.keySet()) if(!available.contains(code))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板中没有 "+code+" 的照片锚点，请检查照片分类或更换模板");
        byte[] output=docx.insert(new java.io.ByteArrayInputStream(templateBytes),grouped,modes);
        String original=batch.get("template_name").toString();
        String filename=original.toLowerCase(Locale.ROOT).endsWith(".docx")
            ?original.substring(0,original.length()-5)+"-照片整理版.docx":original+"-照片整理版.docx";
        PrivateFileStorage.StoredFile stored=storage.storeGenerated(output,filename,DOCX_MIME);
        try {
            long outputFileId=registerFile(stored,actor(a));
            Map<String,Object> report=one("SELECT status,current_version_no FROM erp_service_report WHERE id=? FOR UPDATE",reportId);
            int oldVersion=((Number)report.get("current_version_no")).intValue(),nextVersion=oldVersion+1;
            if(expected!=null&&expected.intValue()!=oldVersion) throw new ResponseStatusException(HttpStatus.CONFLICT,"报告版本已更新，请刷新后重试");
            Map<String,Object> old=one("SELECT content_json FROM erp_service_report_version WHERE report_id=? AND version_no=?",reportId,oldVersion);
            Map<String,Object> content=mapper.readValue(old.get("content_json").toString(),new TypeReference<Map<String,Object>>() {});
            content.put("photoInsertion",Map.of("batchId",id,"photoCount",rows.size(),"modes",modes,"exportedAt",new Date().toInstant().toString()));
            String json=mapper.writeValueAsString(content);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
            jdbc.update("INSERT INTO erp_service_report_version(report_id,version_no,content_json,content_sha256,created_by) VALUES(?,?,?,?,?)",
                reportId,nextVersion,json,hash,actor(a));
            jdbc.update("""
              INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by)
              SELECT report_id,?,file_id,? FROM erp_service_report_attachment WHERE report_id=? AND version_no=?
              """,nextVersion,actor(a),reportId,oldVersion);
            jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",
                reportId,nextVersion,outputFileId,actor(a));
            String oldStatus=report.get("status").toString();
            if("returned".equals(oldStatus)) {
                jdbc.update("UPDATE erp_service_report SET current_version_no=?,status='draft',reviewer_id=NULL,return_reason=NULL WHERE id=?",nextVersion,reportId);
                jdbc.update("INSERT INTO erp_service_report_event(report_id,from_status,to_status,actor_identity_id,reason) VALUES(?,?,?,?,?)",
                    reportId,"returned","draft",actor(a),"根据退回意见插入整理照片并生成新版本");
            } else jdbc.update("UPDATE erp_service_report SET current_version_no=? WHERE id=?",nextVersion,reportId);
            Map<String,Object> manifest=new LinkedHashMap<>();
            manifest.put("templateFileId",batch.get("template_file_id"));manifest.put("photos",manifestPhotos);
            manifest.put("systemModes",modes);manifest.put("anchors",anchors);manifest.put("outputSha256",stored.sha256());
            jdbc.update("INSERT INTO erp_report_photo_export(batch_id,report_id,report_version_no,file_id,mode_json,manifest_json,created_by) VALUES(?,?,?,?,?,?,?)",
                id,reportId,nextVersion,outputFileId,mapper.writeValueAsString(modes),mapper.writeValueAsString(manifest),actor(a));
            jdbc.update("UPDATE erp_report_photo_batch SET status='exported' WHERE id=?",id);
            audit(a,"report.photo_batch.exported","erp_report_photo_batch",id,"生成报告版本 v"+nextVersion+"，照片 "+rows.size()+" 张");
            return Map.of("exportId",jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class),"fileId",outputFileId,
                "fileName",filename,"reportId",reportId,"reportVersionNo",nextVersion,"photoCount",rows.size(),"sha256",stored.sha256());
        } catch(Exception e) {
            storage.deleteQuietly(stored.key());
            throw e;
        }
    }

    @GetMapping("/batches/{id}/exports")
    @PreAuthorize("hasAuthority('report_photo:read')")
    public List<Map<String,Object>> exports(@PathVariable long id) {
        batch(id);
        return jdbc.queryForList("""
          SELECT x.id,x.report_id,x.report_version_no,x.file_id,x.mode_json,x.created_at,
                 f.original_name,f.byte_size file_size,f.sha256
          FROM erp_report_photo_export x JOIN erp_file_object f ON f.id=x.file_id
          WHERE x.batch_id=? ORDER BY x.id DESC
          """,id);
    }
}
