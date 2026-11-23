package top.aameow.erpnext.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import top.aameow.erpnext.files.PrivateFileStorage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@RestController
@RequestMapping("/api/report-photos")
public class ReportPhotoController {
    private static final long MAX_BATCH_BYTES=120L*1024*1024;
    private static final int MAX_BATCH_PHOTOS=300;
    private static final String DOCX_MEDIA="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final JdbcTemplate jdbc;
    private final PrivateFileStorage storage;
    private final ObjectMapper mapper;

    public ReportPhotoController(JdbcTemplate jdbc,PrivateFileStorage storage,ObjectMapper mapper) {
        this.jdbc=jdbc; this.storage=storage; this.mapper=mapper;
    }

    private long actor(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); }
    private String req(Map<String,Object>b,String k) {
        Object v=b.get(k);
        if(v==null||v.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空");
        return v.toString().trim();
    }
    private Map<String,Object> one(String sql,Object...args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.get(0);
    }
    private void audit(Authentication a,String action,String type,long id,String summary) {
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor(a),action,type,Long.toString(id),UUID.randomUUID().toString(),summary);
    }
    private Map<String,Object> batch(long id,boolean lock) {
        return one("SELECT * FROM erp_report_photo_batch WHERE id=?"+(lock?" FOR UPDATE":""),id);
    }
    private List<Map<String,Object>> photos(long batchId) {
        return jdbc.queryForList("""
          SELECT p.id photo_id,p.batch_id,p.file_id,p.system_code,p.sort_order,p.captured_at,p.metadata_json,
                 f.original_name,f.media_type,f.byte_size file_size,f.sha256
          FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id
          WHERE p.batch_id=? ORDER BY FIELD(p.system_code,
            '火灾自动报警系统','消防给水系统','消火栓系统','自动喷水灭火系统','防烟排烟系统',
            '消防应急照明和疏散指示系统','防火分隔设施','电气火灾监控系统','消防电源监控系统',
            '火灾警报和消防应急广播系统','消防专用电话系统','消防电梯','消防供配电设施','建筑灭火器'),
            p.sort_order,p.id
          """,batchId);
    }
    private Map<String,Object> detail(long id) {
        Map<String,Object> result=new LinkedHashMap<>(batch(id,false));
        result.put("photos",photos(id));
        return result;
    }
    private void assertEditable(Map<String,Object> b) {
        if("inserted".equals(b.get("status"))||"archived".equals(b.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"已插入报告的照片批次不能再修改，请新建批次");
    }
    private String systemCode(Object value) {
        if(value==null||value.toString().isBlank()) return null;
        String system=value.toString().trim();
        if(!DocxPhotoInserter.SYSTEMS.contains(system))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效消防系统分类："+system);
        return system;
    }

    @GetMapping("/systems") @PreAuthorize("hasAuthority('photo:read')")
    public List<String> systems() { return DocxPhotoInserter.SYSTEMS; }

    @GetMapping("/templates/{fileId}/systems")
    @PreAuthorize("hasAuthority('photo:read') and hasAuthority('report:read')")
    public Map<String,Object> templateSystems(@PathVariable long fileId,Authentication a) throws IOException {
        Map<String,Object> f=one("SELECT id,uploaded_by,media_type,byte_size,storage_key,original_name FROM erp_file_object WHERE id=?",fileId);
        boolean owner=((Number)f.get("uploaded_by")).longValue()==actor(a);
        if(!owner) {
            Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_attachment WHERE file_id=?",Integer.class,fileId);
            if(linked==null||linked==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"模板文件不存在");
        }
        if(!DOCX_MEDIA.equals(f.get("media_type")) || ((Number)f.get("byte_size")).longValue()>25L*1024*1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择 25MB 以内的 DOCX 模板");
        byte[] bytes=Files.readAllBytes(storage.resolve(f.get("storage_key").toString()));
        List<String> supported;
        try { supported=DocxPhotoInserter.inspectSystems(bytes); }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无法解析 DOCX 模板中的消防系统照片位置",e); }
        return Map.of("fileId",fileId,"filename",f.get("original_name").toString(),"systems",supported);
    }

    @GetMapping("/batches") @PreAuthorize("hasAuthority('photo:read')")
    public List<Map<String,Object>> batches(@RequestParam(required=false) Long projectId) {
        if(projectId!=null) one("SELECT id FROM erp_project WHERE id=?",projectId);
        return jdbc.queryForList("""
          SELECT b.*,p.project_no,p.name project_name,
                 (SELECT COUNT(*) FROM erp_report_photo x WHERE x.batch_id=b.id) photo_count,
                 (SELECT COUNT(*) FROM erp_report_photo x WHERE x.batch_id=b.id AND x.system_code IS NOT NULL) classified_count
          FROM erp_report_photo_batch b JOIN erp_project p ON p.id=b.project_id
          WHERE (? IS NULL OR b.project_id=?) ORDER BY b.created_at DESC LIMIT 300
          """,projectId,projectId);
    }

    @PostMapping("/batches") @PreAuthorize("hasAuthority('photo:write')") @Transactional
    public Map<String,Object> createBatch(@RequestBody Map<String,Object>b,Authentication a) {
        long projectId=Long.parseLong(req(b,"projectId"));
        one("SELECT id FROM erp_project WHERE id=?",projectId);
        Long visitId=b.get("serviceVisitId")==null?null:Long.valueOf(b.get("serviceVisitId").toString());
        if(visitId!=null) one("SELECT id FROM erp_service_visit WHERE id=? AND project_id=?",visitId,projectId);
        String title=req(b,"title");
        String batchNo="PH-"+java.time.LocalDate.now()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO erp_report_photo_batch(batch_no,project_id,service_visit_id,title,status,created_by) VALUES(?,?,?,?,'draft',?)",
            batchNo,projectId,visitId,title,actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"report.photo_batch.created","erp_report_photo_batch",id,batchNo+" "+title);
        return detail(id);
    }

    @GetMapping("/batches/{id}") @PreAuthorize("hasAuthority('photo:read')")
    public Map<String,Object> getBatch(@PathVariable long id) { return detail(id); }

    @PostMapping(value="/batches/{id}/photos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('photo:write')") @Transactional
    public Map<String,Object> uploadPhotos(@PathVariable long id,@RequestPart("photos") List<MultipartFile> uploads,Authentication a) {
        Map<String,Object> b=batch(id,true); assertEditable(b);
        if(uploads==null||uploads.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择照片");
        int existing=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=?",Integer.class,id);
        if(existing+uploads.size()>MAX_BATCH_PHOTOS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"一个照片批次最多 300 张");
        long total=uploads.stream().mapToLong(MultipartFile::getSize).sum();
        long prior=jdbc.queryForObject("SELECT COALESCE(SUM(f.byte_size),0) FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.batch_id=?",Long.class,id);
        if(total+prior>MAX_BATCH_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"照片批次总大小不能超过 120MB");
        List<PrivateFileStorage.StoredFile> storedFiles=new ArrayList<>();
        try {
            int order=existing;
            for(MultipartFile upload:uploads) {
                if(upload==null||upload.isEmpty()) continue;
                if(upload.getSize()>25L*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"单张照片不能超过 25MB");
                String media=upload.getContentType()==null?"":upload.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
                if(!Set.of("image/jpeg","image/png").contains(media))
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"仅支持 JPG/PNG 图片");
                BufferedImage image=ImageIO.read(upload.getInputStream());
                if(image==null||(long)image.getWidth()*image.getHeight()>40000000L)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片无效或超过 4000 万像素");
                PrivateFileStorage.StoredFile stored=storage.store(upload);
                storedFiles.add(stored);
                jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",
                    stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
                long fileId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
                jdbc.update("INSERT INTO erp_report_photo(batch_id,file_id,sort_order,created_by) VALUES(?,?,?,?)",id,fileId,order++,actor(a));
            }
            jdbc.update("UPDATE erp_report_photo_batch SET status='draft' WHERE id=?",id);
            audit(a,"report.photo_batch.photos_added","erp_report_photo_batch",id,"新增照片 "+storedFiles.size()+" 张");
            return detail(id);
        } catch(Exception e) {
            storedFiles.forEach(f->storage.deleteQuietly(f.key()));
            if(e instanceof RuntimeException runtime) throw runtime;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片导入失败",e);
        }
    }

    @PutMapping("/batches/{id}/photos") @PreAuthorize("hasAuthority('photo:write')") @Transactional
    public Map<String,Object> classifyAndOrder(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> batch=batch(id,true); assertEditable(batch);
        Object value=b.get("photos");
        if(!(value instanceof List<?> updates)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"photos 必须是数组");
        List<Map<String,Object>> existing=photos(id);
        if(updates.size()!=existing.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"必须提交批次内全部照片，避免意外覆盖排序");
        Set<Long> seen=new HashSet<>();
        for(Object item:updates) {
            if(!(item instanceof Map<?,?> raw)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片排序数据无效");
            Map<String,Object> update=new HashMap<>();
            raw.forEach((k,v)->update.put(k.toString(),v));
            long photoId=Long.parseLong(req(update,"photoId"));
            if(!seen.add(photoId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片不能重复提交");
            one("SELECT id FROM erp_report_photo WHERE id=? AND batch_id=?",photoId,id);
            String system=systemCode(update.get("systemCode"));
            int order=update.get("sortOrder")==null?0:Integer.parseInt(update.get("sortOrder").toString());
            if(order<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片顺序不能为负数");
            jdbc.update("UPDATE erp_report_photo SET system_code=?,sort_order=? WHERE id=?",system,order,photoId);
        }
        Integer unclassified=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=? AND system_code IS NULL",Integer.class,id);
        String status=unclassified!=null&&unclassified==0?"ready":"draft";
        jdbc.update("UPDATE erp_report_photo_batch SET status=? WHERE id=?",status,id);
        audit(a,"report.photo_batch.classified","erp_report_photo_batch",id,"照片分类与排序更新");
        return detail(id);
    }

    @DeleteMapping("/batches/{id}/photos/{photoId}") @PreAuthorize("hasAuthority('photo:write')") @Transactional
    public Map<String,Object> deletePhoto(@PathVariable long id,@PathVariable long photoId,Authentication a) {
        Map<String,Object> b=batch(id,true); assertEditable(b);
        Map<String,Object> f=one("""
          SELECT f.id,f.storage_key FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id
          WHERE p.id=? AND p.batch_id=?
          """,photoId,id);
        jdbc.update("DELETE FROM erp_report_photo WHERE id=?",photoId);
        Integer references=jdbc.queryForObject("""
          SELECT
            (SELECT COUNT(*) FROM erp_report_photo WHERE file_id=?) +
            (SELECT COUNT(*) FROM erp_service_report_attachment WHERE file_id=?) +
            (SELECT COUNT(*) FROM erp_contract_attachment WHERE file_id=?)
          """,Integer.class,f.get("id"),f.get("id"),f.get("id"));
        if(references==null||references==0) {
            jdbc.update("DELETE FROM erp_file_object WHERE id=?",f.get("id"));
            storage.deleteQuietly(f.get("storage_key").toString());
        }
        Integer remaining=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=?",Integer.class,id);
        Integer unclassified=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=? AND system_code IS NULL",Integer.class,id);
        jdbc.update("UPDATE erp_report_photo_batch SET status=? WHERE id=?",remaining!=null&&remaining>0&&unclassified!=null&&unclassified==0?"ready":"draft",id);
        audit(a,"report.photo.deleted","erp_report_photo",photoId,"移除照片");
        return detail(id);
    }

    @GetMapping("/batches/{id}/photos/{photoId}/preview") @PreAuthorize("hasAuthority('photo:read')")
    public ResponseEntity<byte[]> previewPhoto(@PathVariable long id,@PathVariable long photoId) throws IOException {
        Map<String,Object> f=one("""
          SELECT f.storage_key,f.media_type,f.original_name FROM erp_report_photo p
          JOIN erp_file_object f ON f.id=p.file_id WHERE p.id=? AND p.batch_id=?
          """,photoId,id);
        byte[] bytes=Files.readAllBytes(storage.resolve(f.get("storage_key").toString()));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.get("media_type").toString()))
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .header("X-Content-Type-Options","nosniff").body(bytes);
    }

    @PostMapping("/batches/{id}/preview") @PreAuthorize("hasAuthority('photo:write') and hasAuthority('report:read')")
    public ResponseEntity<byte[]> previewDocx(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) throws IOException {
        byte[] result=generate(id,b,a);
        return docxResponse(result,outputName(id,b));
    }

    @PostMapping("/batches/{id}/insert") @PreAuthorize("hasAuthority('photo:write') and hasAuthority('report:write')") @Transactional
    public Map<String,Object> insertIntoReport(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) throws IOException {
        Map<String,Object> batch=batch(id,true); assertEditable(batch);
        Map<String,Object> report=reportForInsertion(b,batch,a);
        byte[] output=buildDocx(id,b);
        String name=outputName(id,b);
        PrivateFileStorage.StoredFile stored=null;
        try {
            stored=storage.storeBytes(output,name,DOCX_MEDIA);
            jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",
                stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
            long outputFileId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            long templateFileId=Long.parseLong(req(b,"templateFileId"));
            int oldVersion=((Number)report.get("current_version_no")).intValue();
            int nextVersion=oldVersion+1;
            Map<String,Object> old=one("SELECT content_json,content_sha256 FROM erp_service_report_version WHERE report_id=? AND version_no=?",report.get("id"),oldVersion);
            jdbc.update("INSERT INTO erp_service_report_version(report_id,version_no,content_json,content_sha256,created_by) VALUES(?,?,?,?,?)",
                report.get("id"),nextVersion,old.get("content_json"),old.get("content_sha256"),actor(a));
            jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) SELECT report_id,?,file_id,? FROM erp_service_report_attachment WHERE report_id=? AND version_no=?",
                nextVersion,actor(a),report.get("id"),oldVersion);
            Integer templateAttached=jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_attachment WHERE report_id=? AND version_no=? AND file_id=?",
                Integer.class,report.get("id"),nextVersion,templateFileId);
            if(templateAttached==null||templateAttached==0)
                jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",
                    report.get("id"),nextVersion,templateFileId,actor(a));
            jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",
                report.get("id"),nextVersion,outputFileId,actor(a));
            Map<String,Integer> counts=systemCounts(id);
            jdbc.update("INSERT INTO erp_report_photo_insertion(batch_id,report_id,version_no,template_file_id,output_file_id,system_counts_json,inserted_by) VALUES(?,?,?,?,?,?,?)",
                id,report.get("id"),nextVersion,templateFileId,outputFileId,mapper.writeValueAsString(counts),actor(a));
            String oldStatus=report.get("status").toString();
            jdbc.update("UPDATE erp_service_report SET current_version_no=?,status='draft',reviewer_id=NULL,return_reason=NULL WHERE id=?",
                nextVersion,report.get("id"));
            jdbc.update("INSERT INTO erp_service_report_event(report_id,from_status,to_status,actor_identity_id,reason) VALUES(?,?,?,?,?)",
                report.get("id"),oldStatus,"draft",actor(a),"照片已按消防系统分类插入 DOCX，生成新版本 v"+nextVersion);
            jdbc.update("UPDATE erp_report_photo_batch SET status='inserted' WHERE id=?",id);
            audit(a,"report.photos.inserted","erp_service_report",((Number)report.get("id")).longValue(),"照片批次 "+batch.get("batch_no")+" 已插入报告版本 v"+nextVersion);
            return Map.of("batchId",id,"reportId",report.get("id"),"versionNo",nextVersion,
                "fileId",outputFileId,"filename",name,"sha256",stored.sha256(),"systemCounts",counts,"status","draft");
        } catch(Exception e) {
            if(stored!=null) storage.deleteQuietly(stored.key());
            if(e instanceof RuntimeException runtime) throw runtime;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"照片插入失败",e);
        }
    }

    private byte[] generate(long batchId,Map<String,Object>b,Authentication a) throws IOException {
        Map<String,Object> photoBatch=batch(batchId,false);
        assertEditable(photoBatch);
        reportForInsertion(b,photoBatch,a);
        return buildDocx(batchId,b);
    }

    private Map<String,Object> reportForInsertion(Map<String,Object>b,Map<String,Object> photoBatch,Authentication a) {
        long reportId=Long.parseLong(req(b,"reportId"));
        Map<String,Object> report=one("SELECT * FROM erp_service_report WHERE id=? FOR UPDATE",reportId);
        Object effectiveProject=report.get("project_id");
        if(effectiveProject==null&&report.get("work_package_id")!=null)
            effectiveProject=one("SELECT project_id FROM erp_service_work_package WHERE id=?",report.get("work_package_id")).get("project_id");
        if(effectiveProject==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"报告未关联项目");
        report.put("effective_project_id",effectiveProject);
        if(!Set.of("draft","returned").contains(report.get("status").toString()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿或退回状态的报告可以插入照片");
        if(((Number)report.get("effective_project_id")).longValue()!=((Number)photoBatch.get("project_id")).longValue())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片批次与报告不属于同一项目");
        int expected=Integer.parseInt(req(b,"expectedVersionNo"));
        if(expected!=((Number)report.get("current_version_no")).intValue())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"报告版本已变化，请刷新后重新预览");
        long templateId=Long.parseLong(req(b,"templateFileId"));
        Map<String,Object> template=one("SELECT id,storage_key,media_type,original_name,uploaded_by FROM erp_file_object WHERE id=?",templateId);
        if(!DOCX_MEDIA.equals(template.get("media_type"))||!template.get("original_name").toString().toLowerCase(Locale.ROOT).endsWith(".docx"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板必须是有效 DOCX 文件");
        boolean owned=template.get("uploaded_by") instanceof Number owner&&owner.longValue()==actor(a);
        Integer attached=jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_attachment WHERE report_id=? AND file_id=?",Integer.class,reportId,templateId);
        if(!owned&&(attached==null||attached==0))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"模板文件不存在");
        Integer unclassified=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=? AND system_code IS NULL",Integer.class,photoBatch.get("id"));
        Integer photoCount=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=?",Integer.class,photoBatch.get("id"));
        if(photoCount==null||photoCount==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片批次为空");
        if(unclassified!=null&&unclassified>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仍有未分类照片，请先完成一级系统分类");
        return report;
    }

    private byte[] buildDocx(long batchId,Map<String,Object>b) throws IOException {
        long templateId=Long.parseLong(req(b,"templateFileId"));
        Map<String,Object> template=one("SELECT storage_key FROM erp_file_object WHERE id=?",templateId);
        byte[] source=Files.readAllBytes(storage.resolve(template.get("storage_key").toString()));
        List<Map<String,Object>> rows=photos(batchId);
        List<DocxPhotoInserter.Photo> input=new ArrayList<>();
        for(Map<String,Object> row:rows) {
            if(row.get("system_code")==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仍有未分类照片");
            Map<String,Object> f=one("SELECT storage_key FROM erp_file_object WHERE id=?",row.get("file_id"));
            input.add(new DocxPhotoInserter.Photo(row.get("system_code").toString(),row.get("original_name").toString(),
                Files.readAllBytes(storage.resolve(f.get("storage_key").toString()))));
        }
        try { return DocxPhotoInserter.insert(source,input); }
        catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e); }
    }

    private Map<String,Integer> systemCounts(long batchId) {
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT system_code,COUNT(*) photo_count FROM erp_report_photo WHERE batch_id=? GROUP BY system_code ORDER BY system_code",batchId))
            counts.put(row.get("system_code").toString(),((Number)row.get("photo_count")).intValue());
        return counts;
    }

    private String outputName(long batchId,Map<String,Object>b) {
        Map<String,Object> report=one("SELECT report_no FROM erp_service_report WHERE id=?",Long.parseLong(req(b,"reportId")));
        String no=report.get("report_no").toString().replaceAll("[^\\p{L}\\p{N}._-]","_");
        return no+"-照片整理版-批次"+batchId+".docx";
    }

    private ResponseEntity<byte[]> docxResponse(byte[] bytes,String filename) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(DOCX_MEDIA))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
