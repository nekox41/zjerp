package top.aameow.erpnext.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import top.aameow.erpnext.files.PrivateFileStorage;
import top.aameow.erpnext.files.PhotoEncoding;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/service-workflow")
public class ServiceWorkflowController {
    private static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PrivateFileStorage storage;
    private final ReportDocxPhotoInserter inserter;
    private final InspectionDocxBinder binder;

    public ServiceWorkflowController(JdbcTemplate jdbc,ObjectMapper mapper,PrivateFileStorage storage,ReportDocxPhotoInserter inserter,InspectionDocxBinder binder) {
        this.jdbc=jdbc;this.mapper=mapper;this.storage=storage;this.inserter=inserter;this.binder=binder;
    }
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private String req(Map<String,Object>b,String key){Object v=b.get(key);if(v==null||v.toString().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 不能为空");return v.toString().trim();}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");return rows.get(0);}
    private Long opt(Map<String,Object>b,String key){Object v=b.get(key);return v==null||v.toString().isBlank()?null:Long.valueOf(v.toString());}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    private void audit(long who,String action,String type,long id,String summary){
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            who,action,type,Long.toString(id),UUID.randomUUID().toString(),summary);
    }
    private void eventDelivery(long id,String from,String to,long who,String notes){
        jdbc.update("INSERT INTO erp_service_delivery_event(delivery_id,from_status,to_status,actor_identity_id,notes) VALUES(?,?,?,?,?)",id,from,to,who,notes);
    }

    @GetMapping("/projects") @PreAuthorize("hasAuthority('service:read')")
    public List<Map<String,Object>> projects(){
        return jdbc.queryForList("SELECT p.id,p.project_no,p.name,p.status,c.name customer_name FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id ORDER BY p.updated_at DESC");
    }
    @GetMapping("/projects/{projectId}/work-packages") @PreAuthorize("hasAuthority('service:read')")
    public List<Map<String,Object>> packages(@PathVariable long projectId){
        return jdbc.queryForList("SELECT w.*,e.full_name assignee_name FROM erp_service_work_package w LEFT JOIN erp_employee e ON e.id=w.assignee_employee_id WHERE w.project_id=? ORDER BY w.id DESC",projectId);
    }
    @GetMapping("/work-packages/{id}/visits") @PreAuthorize("hasAuthority('service:visit:read')")
    public List<Map<String,Object>> visits(@PathVariable long id){
        one("SELECT id FROM erp_service_work_package WHERE id=?",id);
        return jdbc.queryForList("SELECT v.*,i.display_name creator_name,(SELECT COUNT(*) FROM erp_service_visit_staff s WHERE s.visit_id=v.id) staff_count,(SELECT GROUP_CONCAT(e.full_name ORDER BY e.full_name SEPARATOR '、') FROM erp_service_visit_staff s JOIN erp_employee e ON e.id=s.employee_id WHERE s.visit_id=v.id) staff_names,(SELECT COUNT(*) FROM erp_service_visit_issue x WHERE x.visit_id=v.id) issue_count FROM erp_service_visit v LEFT JOIN erp_identity i ON i.id=v.created_by WHERE v.work_package_id=? ORDER BY v.visit_date DESC,v.id DESC",id);
    }
    @PostMapping("/work-packages/{id}/visits") @PreAuthorize("hasAuthority('service:visit:write')") @Transactional
    public Map<String,Object> createVisit(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_service_work_package WHERE id=? FOR UPDATE",id);
        String title=req(b,"title"),date=req(b,"visitDate"),type=b.get("visitType")==null?"inspection":b.get("visitType").toString();
        if(!Set.of("inspection","rectification_review","supplement","maintenance","other").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效现场服务类型");
        Integer visitNo=jdbc.queryForObject("SELECT COALESCE(MAX(visit_no),0)+1 FROM erp_service_visit WHERE work_package_id=?",Integer.class,id);
        jdbc.update("INSERT INTO erp_service_visit(work_package_id,visit_no,visit_date,visit_type,title,summary,result_summary,status,created_by) VALUES(?,?,?,?,?,?,?,'completed',?)",
            id,visitNo,date,type,title,b.get("summary"),b.get("resultSummary"),actor(a));
        long visitId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        Object staff=b.get("staff");
        if(!(staff instanceof List<?> list)||list.isEmpty()||list.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择实际到场人员");
        Set<Long> assignedStaff=new HashSet<>();
        for(Object item:list){
            if(!(item instanceof Map<?,?> m))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场人员记录无效");
            long employee=Long.parseLong(Objects.toString(m.get("employeeId")));
            String role=Objects.toString(m.get("role"),"field_operator");
            if(!assignedStaff.add(employee))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场人员重复");
            one("SELECT id FROM erp_employee WHERE id=? AND status='active'",employee);
            if(!Set.of("field_operator","engineer","team_lead","observer").contains(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效现场人员角色");
            jdbc.update("INSERT INTO erp_service_visit_staff(visit_id,employee_id,staff_role) VALUES(?,?,?)",visitId,employee,role);
        }
        audit(actor(a),"service.visit.created","erp_service_visit",visitId,title);
        return one("SELECT * FROM erp_service_visit WHERE id=?",visitId);
    }
    @PostMapping("/visits/{id}/issues") @PreAuthorize("hasAuthority('service:visit:write')") @Transactional
    public Map<String,Object> linkIssue(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_service_visit WHERE id=?",id);
        long issueId=Long.parseLong(req(b,"issueId"));
        Map<String,Object> issue=one("SELECT i.id,i.project_id FROM erp_project_issue i WHERE i.id=?",issueId);
        Long visitProject=jdbc.queryForObject("SELECT w.project_id FROM erp_service_visit v JOIN erp_service_work_package w ON w.id=v.work_package_id WHERE v.id=?",Long.class,id);
        if(visitProject==null||((Number)issue.get("project_id")).longValue()!=visitProject)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"只能关联同一项目的问题清单");
        String relation=b.get("relationType")==null?"found":b.get("relationType").toString();
        if(!Set.of("found","reviewed","resolved").contains(relation))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效问题关联类型");
        jdbc.update("INSERT INTO erp_service_visit_issue(visit_id,issue_id,relation_type,notes) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE relation_type=VALUES(relation_type),notes=VALUES(notes)",id,issueId,relation,b.get("notes"));
        audit(actor(a),"service.visit.issue.linked","erp_service_visit",id,"issue="+issueId);
        return one("SELECT * FROM erp_service_visit_issue WHERE visit_id=? AND issue_id=?",id,issueId);
    }

    @GetMapping("/photo-systems") @PreAuthorize("hasAuthority('report:photo:read')")
    public List<String> photoSystems(){return new ArrayList<>(allPhotoSystems());}
    private Set<String> allPhotoSystems(){
        Set<String> systems=new LinkedHashSet<>(inserter.supportedSystems());
        systems.addAll(jdbc.query("SELECT DISTINCT system_name FROM erp_inspection_template WHERE template_kind='system' ORDER BY system_name",(rs,i)->rs.getString(1)));
        return systems;
    }

    @GetMapping("/work-packages/{id}/photo-batches") @PreAuthorize("hasAuthority('report:photo:read')")
    public List<Map<String,Object>> photoBatches(@PathVariable long id){
        return jdbc.queryForList("SELECT b.*,i.display_name creator_name,(SELECT COUNT(*) FROM erp_report_photo p WHERE p.batch_id=b.id) photo_count,(SELECT COUNT(*) FROM erp_report_photo p WHERE p.batch_id=b.id AND p.confirmation_status='confirmed' AND p.system_code IS NOT NULL AND p.taken_on IS NOT NULL) confirmed_count FROM erp_report_photo_batch b JOIN erp_identity i ON i.id=b.created_by WHERE b.work_package_id=? ORDER BY b.visit_date DESC,b.id DESC",id);
    }
    @PostMapping("/work-packages/{id}/photo-batches") @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> createBatch(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_service_work_package WHERE id=?",id);
        Long visitId=opt(b,"visitId"); if(visitId!=null)one("SELECT id FROM erp_service_visit WHERE id=? AND work_package_id=?",visitId,id);
        String title=req(b,"title"),date=req(b,"visitDate");
        jdbc.update("INSERT INTO erp_report_photo_batch(work_package_id,visit_id,title,visit_date,created_by) VALUES(?,?,?,?,?)",id,visitId,title,date,actor(a));
        long batchId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(actor(a),"report.photo_batch.created","erp_report_photo_batch",batchId,title);
        return batchDetail(batchId);
    }
    @GetMapping("/photo-batches/{id}") @PreAuthorize("hasAuthority('report:photo:read')")
    public Map<String,Object> getBatch(@PathVariable long id){return batchDetail(id);}
    private Map<String,Object> batchDetail(long id){
        Map<String,Object> batch=new LinkedHashMap<>(one("SELECT b.*,i.display_name creator_name,r.report_no FROM erp_report_photo_batch b JOIN erp_identity i ON i.id=b.created_by LEFT JOIN erp_service_report r ON r.id=b.report_id WHERE b.id=?",id));
        batch.put("reviewHistory",jdbc.queryForList("SELECT a.action,a.summary_json,a.occurred_at,i.display_name actor_name FROM erp_audit_event a LEFT JOIN erp_identity i ON i.id=a.actor_identity_id WHERE a.resource_type='erp_report_photo_batch' AND a.resource_id=? AND a.action IN ('report.photo_batch.submitted','report.photo_batch.reviewed','report.photo_batch.returned') ORDER BY a.id",Long.toString(id)));
        batch.put("photos",jdbc.queryForList("SELECT p.id,p.file_id,p.system_code,p.taken_on,p.caption,p.sort_order,p.confirmation_status,p.confirmed_by,p.confirmed_at,f.original_name,f.media_type,f.byte_size file_size,f.sha256 FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.batch_id=? ORDER BY p.sort_order,p.id",id));
        return batch;
    }
    @PostMapping("/photo-batches/{id}/photos") @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> addPhoto(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> batch=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",id);
        if(!"collecting".equals(batch.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"照片批次已提交审核，不能继续添加");
        long fileId=Long.parseLong(req(b,"fileId"));
        Map<String,Object> file=one("SELECT id,uploaded_by,media_type,byte_size FROM erp_file_object WHERE id=?",fileId);
        if(!(file.get("uploaded_by") instanceof Number owner)||owner.longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能添加自己上传的照片");
        if(!Set.of("image/jpeg","image/png").contains(file.get("media_type").toString()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持 JPG/PNG 照片");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=?",Integer.class,id);
        if(count!=null&&count>=500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"单批最多 500 张照片");
        Long totalBytes=jdbc.queryForObject("SELECT COALESCE(SUM(f.byte_size),0) FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.batch_id=?",Long.class,id);
        if(totalBytes!=null&&totalBytes+((Number)file.get("byte_size")).longValue()>220L*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"单批照片总量不能超过 220MB");
        Integer order=jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM erp_report_photo WHERE batch_id=?",Integer.class,id);
        jdbc.update("INSERT INTO erp_report_photo(batch_id,file_id,taken_on,caption,sort_order) VALUES(?,?,?,?,?)",id,fileId,b.get("takenOn"),b.get("caption"),order);
        long photoId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(actor(a),"report.photo.added","erp_report_photo",photoId,"batch="+id);
        return one("SELECT p.*,f.original_name,f.media_type,f.byte_size file_size FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.id=?",photoId);
    }
    // One transaction, one batch lock, and no implicit confirmation after changing a system.
    public record BatchClassification(List<Long> photoIds,String systemCode,Boolean confirmed){}
    @PutMapping("/photo-batches/{id}/classification")
    @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> classifyPhotos(@PathVariable long id,@RequestBody BatchClassification b,Authentication a){
        Map<String,Object> batch=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",id);
        if(!"collecting".equals(batch.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前批次已提交，照片不可修改");
        if(b.photoIds()==null||b.photoIds().isEmpty()||b.photoIds().size()>500||b.photoIds().stream().anyMatch(x->x==null||x<=0)||new HashSet<>(b.photoIds()).size()!=b.photoIds().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择 1 至 500 张不重复的照片");
        String system=b.systemCode()==null||b.systemCode().isBlank()?null:b.systemCode().trim();
        if(system!=null&&!allPhotoSystems().contains(system))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效消防系统分类");
        boolean confirmed=Boolean.TRUE.equals(b.confirmed());
        if(confirmed&&system==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"未分类照片不能确认");
        var ids=b.photoIds().stream().sorted().toList();
        for(Long photoId:ids){
            var rows=jdbc.queryForList("SELECT id,taken_on FROM erp_report_photo WHERE id=? AND batch_id=? FOR UPDATE",photoId,id);
            if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片不属于当前批次");
            if(confirmed&&rows.get(0).get("taken_on")==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"所选照片包含未登记拍摄日期的记录");
        }
        String status=confirmed?"confirmed":"pending";
        jdbc.batchUpdate("UPDATE erp_report_photo SET system_code=?,confirmation_status=?,confirmed_by=?,confirmed_at=CASE WHEN ? THEN CURRENT_TIMESTAMP(3) ELSE NULL END WHERE id=? AND batch_id=?",
            ids.stream().map(photoId->new Object[]{system,status,confirmed?actor(a):null,confirmed,photoId,id}).toList());
        audit(actor(a),"report.photo.batch_classified","erp_report_photo_batch",id,"count="+ids.size()+",system="+system+",confirmed="+confirmed);
        return batchDetail(id);
    }

    @PutMapping("/photos/{id}") @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> updatePhoto(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> p=one("SELECT p.*,b.status batch_status FROM erp_report_photo p JOIN erp_report_photo_batch b ON b.id=p.batch_id WHERE p.id=? FOR UPDATE",id);
        if(!"collecting".equals(p.get("batch_status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前批次已提交，照片不可修改");
        Object requestedSystem=b.containsKey("systemCode")?b.get("systemCode"):p.get("system_code");
        String system=requestedSystem==null?null:requestedSystem.toString();
        if(system!=null&&system.isBlank())system=null;
        if(system!=null&&!allPhotoSystems().contains(system))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效消防系统分类");
        String confirmation=b.get("confirmed")==null?null:(Boolean.parseBoolean(b.get("confirmed").toString())?"confirmed":"pending");
        Object takenOn=b.containsKey("takenOn")?b.get("takenOn"):p.get("taken_on");
        if(takenOn!=null&&takenOn.toString().isBlank())takenOn=null;
        if(confirmation==null&&(!Objects.equals(system,p.get("system_code"))||!Objects.equals(Objects.toString(takenOn,""),Objects.toString(p.get("taken_on"),""))))confirmation="pending";
        if("confirmed".equals(confirmation)&&(system==null||takenOn==null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"确认前须完成系统分类及拍摄日期");
        jdbc.update("UPDATE erp_report_photo SET system_code=?,taken_on=?,caption=?,sort_order=COALESCE(?,sort_order),confirmation_status=COALESCE(?,confirmation_status),confirmed_by=CASE WHEN ?='confirmed' THEN ? WHEN ?='pending' THEN NULL ELSE confirmed_by END,confirmed_at=CASE WHEN ?='confirmed' THEN CURRENT_TIMESTAMP(3) WHEN ?='pending' THEN NULL ELSE confirmed_at END WHERE id=?",
            system,takenOn,b.containsKey("caption")?b.get("caption"):p.get("caption"),
            opt(b,"sortOrder"),confirmation,confirmation,actor(a),confirmation,confirmation,confirmation,id);
        audit(actor(a),"report.photo.updated","erp_report_photo",id,system);
        return one("SELECT * FROM erp_report_photo WHERE id=?",id);
    }
    @DeleteMapping("/photos/{id}") @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> deletePhoto(@PathVariable long id,Authentication a){
        Map<String,Object> p=one("SELECT p.*,b.status batch_status FROM erp_report_photo p JOIN erp_report_photo_batch b ON b.id=p.batch_id WHERE p.id=? FOR UPDATE",id);
        if(!"collecting".equals(p.get("batch_status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前批次已提交，照片不可删除");
        jdbc.update("DELETE FROM erp_report_photo WHERE id=?",id);audit(actor(a),"report.photo.deleted","erp_report_photo",id,"batch="+p.get("batch_id"));
        return Map.of("success",true);
    }
    @PostMapping("/photo-batches/{id}/submit") @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> submitBatch(@PathVariable long id,Authentication a){
        Map<String,Object>b=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",id);
        if(!"collecting".equals(b.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有整理中的批次可以提交审核");
        Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=?",Integer.class,id);
        Integer ready=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=? AND confirmation_status='confirmed' AND system_code IS NOT NULL AND taken_on IS NOT NULL",Integer.class,id);
        if(total==null||total==0||!total.equals(ready))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"所有照片必须完成系统分类、拍摄日期填写和人工确认后才能提交");
        jdbc.update("UPDATE erp_report_photo_batch SET status='submitted' WHERE id=?",id);
        audit(actor(a),"report.photo_batch.submitted","erp_report_photo_batch",id,"photos="+total);
        return batchDetail(id);
    }
    @PostMapping("/photo-batches/{id}/return") @PreAuthorize("hasAuthority('report:review') and hasAuthority('report:photo:read')") @Transactional
    public Map<String,Object> returnBatch(@PathVariable long id,@RequestBody Map<String,Object> request,Authentication a){
        var batch=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",id);
        if(!"submitted".equals(batch.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"仅待审核批次可以退回");
        requirePhotoReviewer(batch,actor(a));
        String reason=req(request,"reason");if(reason.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"退回原因过长");
        jdbc.update("UPDATE erp_report_photo_batch SET status='collecting',reviewed_by=NULL,reviewed_at=NULL WHERE id=?",id);
        audit(actor(a),"report.photo_batch.returned","erp_report_photo_batch",id,reason);
        return batchDetail(id);
    }
    private void requirePhotoReviewer(Map<String,Object> b,long identity){
        if(((Number)b.get("created_by")).longValue()==identity)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"照片整理人不能审核自己的批次");
        Integer qualified=jdbc.queryForObject("SELECT COUNT(*) FROM erp_employee e JOIN erp_employee_qualification q ON q.employee_id=e.id WHERE e.identity_id=? AND e.employee_type='engineer' AND e.status='active' AND q.status='valid' AND (q.expires_on IS NULL OR q.expires_on>=CURRENT_DATE)",Integer.class,identity);
        if(qualified==null||qualified==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"照片批次审核必须由具有有效工程师资格的员工完成");
    }
    @PostMapping("/photo-batches/{id}/review") @PreAuthorize("hasAuthority('report:review') and hasAuthority('report:photo:read')") @Transactional
    public Map<String,Object> reviewBatch(@PathVariable long id,Authentication a){
        Map<String,Object>b=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",id);
        if(!"submitted".equals(b.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已提交的照片批次可以审核");
        requirePhotoReviewer(b,actor(a));
        jdbc.update("UPDATE erp_report_photo_batch SET status='reviewed',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP(3) WHERE id=?",actor(a),id);
        audit(actor(a),"report.photo_batch.reviewed","erp_report_photo_batch",id,"reviewed");
        return batchDetail(id);
    }

    private Map<String,Object> reportForGeneration(long id,boolean lock){
        var report=one("SELECT * FROM erp_service_report WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(!"draft".equals(report.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"请先保存为新的草稿版本，再生成 DOCX");
        return report;
    }
    private Map<String,Object> templateForGeneration(Map<String,Object>b,Authentication a){
        Long libraryId=opt(b,"baseTemplateId");
        long templateId;
        if(libraryId!=null){
            if(a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("report:read")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权使用模板库");
            var lib=one("SELECT file_id FROM erp_inspection_template WHERE id=? AND template_kind='base' AND active=1",libraryId);
            templateId=((Number)lib.get("file_id")).longValue();
        }else templateId=Long.parseLong(req(b,"templateFileId"));
        var template=one("SELECT * FROM erp_file_object WHERE id=?",templateId);
        if(libraryId==null&&(!(template.get("uploaded_by") instanceof Number owner)||owner.longValue()!=actor(a))){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请先上传自己有权使用的 DOCX 模板");
        }
        if(!DOCX.equals(template.get("media_type"))||!template.get("original_name").toString().toLowerCase(Locale.ROOT).endsWith(".docx"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板必须是 DOCX 文件");
        return template;
    }
    private byte[] templateBytes(Map<String,Object> template)throws Exception{
        byte[] bytes=Files.readAllBytes(storage.resolve(template.get("storage_key").toString()));
        if(!sha(bytes).equals(template.get("sha256")))throw new ResponseStatusException(HttpStatus.CONFLICT,"模板文件校验失败");
        return bytes;
    }
    private record TemplatePlan(byte[] bytes,String sha256,List<Map<String,Object>> revisions,Set<String> systems){}
    private TemplatePlan prepareTemplate(Map<String,Object>b,Map<String,Object> template)throws Exception{
        byte[] bytes=templateBytes(template);Long baseId=opt(b,"baseTemplateId");
        List<Long> ids=b.get("sectionTemplateIds")==null?List.of():mapper.convertValue(b.get("sectionTemplateIds"),new TypeReference<List<Long>>(){});
        if(ids==null||ids.size()>40||ids.stream().anyMatch(x->x==null||x<=0)||new HashSet<>(ids).size()!=ids.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"章节模板选择无效");
        if(baseId==null){
            if(!ids.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"系统章节需搭配基础模板库使用");
            return new TemplatePlan(bytes,template.get("sha256").toString(),List.of(),allPhotoSystems());
        }
        if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"至少选择一个系统章节模板");
        var base=one("SELECT id,template_name,revision_no,sha256,file_id FROM erp_inspection_template WHERE id=? AND template_kind='base' AND active=1",baseId);
        if(!Objects.equals(base.get("sha256"),template.get("sha256"))||((Number)base.get("file_id")).longValue()!=((Number)template.get("id")).longValue())throw new ResponseStatusException(HttpStatus.CONFLICT,"基础模板已变更");
        List<Map<String,Object>> revisions=new ArrayList<>();revisions.add(base);
        List<InspectionTemplateComposer.Section> sections=new ArrayList<>();Set<String> systems=new LinkedHashSet<>();long total=bytes.length;
        for(Long id:ids){
            var s=one("SELECT t.id,t.system_name,t.template_name,t.revision_no,t.sha256,t.file_id,f.storage_key,f.media_type FROM erp_inspection_template t JOIN erp_file_object f ON f.id=t.file_id WHERE t.id=? AND t.template_kind='system' AND t.active=1",id);
            String system=s.get("system_name").toString();
            if(!systems.add(system))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"同一系统只能选择一个模板："+system);
            byte[] part=templateBytes(s);total+=part.length;if(total>40L*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"基础模板和章节模板合计超过40MB");
            sections.add(new InspectionTemplateComposer.Section(system,part));
            s.remove("storage_key");s.remove("media_type");revisions.add(s);
        }
        String hash=sha(json(revisions).getBytes(StandardCharsets.UTF_8));
        return new TemplatePlan(InspectionTemplateComposer.compose(bytes,sections),hash,List.copyOf(revisions),systems);
    }
    private void checkStructuredRecords(TemplatePlan plan,com.fasterxml.jackson.databind.JsonNode data){
        Set<String> basis=new HashSet<>();for(var ref:data.path("basisReferences"))basis.add(ref.path("id").asText());
        if(!plan.revisions().isEmpty()){
            Set<String> selected=new HashSet<>();for(var t:plan.revisions())if(t.containsKey("system_name"))selected.add(t.get("id").toString());
            for(var row:data.path("records"))if(!row.path("templateItemId").asText("").isBlank()&&!selected.contains(row.path("templateId").asText()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"组合报告未选择资料中使用的系统模板版本："+row.path("system").asText());
        }
        for(var template:plan.revisions()){
            if(!template.containsKey("system_name"))continue;
            long tid=((Number)template.get("id")).longValue();
            String system=template.get("system_name").toString();
            var items=jdbc.queryForList("SELECT i.id,c.standard_id FROM erp_inspection_template_item i JOIN erp_fire_standard_clause c ON c.id=i.clause_id WHERE i.template_id=? ORDER BY i.sort_order",tid);
            if(items.isEmpty())continue;
            Map<String,String> required=new LinkedHashMap<>();
            for(var item:items)required.put(item.get("id").toString(),item.get("standard_id").toString());
            Set<String> covered=new HashSet<>();
            for(var row:data.path("records")){
                if(!system.equals(row.path("system").asText()))continue;
                String item=row.path("templateItemId").asText();
                if(!Long.toString(tid).equals(row.path("templateId").asText())||!required.containsKey(item)||!required.get(item).equals(row.path("standardId").asText()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"检测资料中存在不属于所选结构化模板版本的项目："+system);
                if(!basis.contains(required.get(item)))throw new ResponseStatusException(HttpStatus.CONFLICT,"检测项目引用的规范尚未选为报告依据："+row.path("clauseReference").asText());
                covered.add(item);
            }
            if(!covered.equals(required.keySet()))throw new ResponseStatusException(HttpStatus.CONFLICT,"所选系统模板的检测项目尚未完整导入："+system);
        }
    }
    private com.fasterxml.jackson.databind.JsonNode inspectionSnapshot(long id,Map<String,Object> report)throws Exception{
        if(!"inspection".equals(report.get("report_type")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"只有消防检测报告支持正文资料绑定");
        var version=one("SELECT content_json FROM erp_service_report_version WHERE report_id=? AND version_no=?",id,report.get("current_version_no"));
        var snapshot=mapper.readTree(version.get("content_json").toString()).path("inspectionProfile");
        if(!snapshot.isObject()||snapshot.path("workPackageId").asLong()!=((Number)report.get("work_package_id")).longValue()||!snapshot.path("content").isObject())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请先保存并绑定检测资料版本");
        return snapshot;
    }
    private List<InspectionDocxBinder.Binding> bindings(Map<String,Object>b){
        try{return b.get("bodyBindings")==null?List.of():mapper.convertValue(b.get("bodyBindings"),new TypeReference<List<InspectionDocxBinder.Binding>>(){});}
        catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"正文绑定参数无效");}
    }
    private void checkExpectedVersion(Map<String,Object> b,Map<String,Object> report,boolean required){
        Long expected=opt(b,"expectedVersionNo");
        if((required&&expected==null)||(expected!=null&&expected.longValue()!=((Number)report.get("current_version_no")).longValue()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"报告版本已变化，请重新检查");
    }
    private String planHash(Map<String,Object> report,String templateHash,InspectionDocxBinder.Inspection inspection){
        return sha(json(List.of(report.get("id"),report.get("current_version_no"),templateHash,inspection.changes())).getBytes(StandardCharsets.UTF_8));
    }
    @PostMapping("/reports/{id}/inspect-docx")
    @PreAuthorize("hasAuthority('report:write') and hasAuthority('report:read')")
    @Transactional(readOnly=true)
    public Map<String,Object> inspectDocx(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a)throws Exception{
        var report=reportForGeneration(id,false);
        checkExpectedVersion(b,report,true);
        var template=templateForGeneration(b,a);
        var snapshot=inspectionSnapshot(id,report);
        var plan=prepareTemplate(b,template);
        checkStructuredRecords(plan,snapshot.path("content"));
        byte[] prepared=InspectionDocxRepeatRows.prepare(plan.bytes(),snapshot.path("content"));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(prepared))){
            var result=binder.inspect(doc,snapshot.path("content"),report.get("report_no").toString(),report.get("title").toString(),bindings(b));
            return Map.of("inspection",result,"planSha256",planHash(report,plan.sha256(),result),"templateSha256",plan.sha256(),"versionNo",report.get("current_version_no"),"profileVersionNo",snapshot.path("versionNo").asInt(),"templateRevisions",plan.revisions());
        }catch(org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"DOCX 模板无效或损坏",e);}
    }

    @PostMapping("/reports/{id}/register-docx")
    @PreAuthorize("hasAuthority('report:write') and hasAuthority('report:read') and hasAuthority('file:upload')") @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> registerDocx(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a)throws Exception{
        var report=reportForGeneration(id,true);checkExpectedVersion(b,report,true);
        if(!Boolean.TRUE.equals(b.get("manualReviewed")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请确认已人工核对 Word 正文、照片、报告结论和旧资料");
        var f=templateForGeneration(Map.of("templateFileId",req(b,"fileId")),a);
        long fileId=((Number)f.get("id")).longValue();
        byte[] bytes=templateBytes(f);
        if("inspection".equals(report.get("report_type"))&&InspectionTemplateComposer.pendingToc(bytes))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"目录仍为待更新占位文本，请在 Word 更新整个目录并保存");
        try(var doc=new XWPFDocument(new ByteArrayInputStream(bytes))){
            if(doc.getBodyElements().isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Word 正文为空");
        }catch(org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"DOCX 文件无效",e);}
        int version=((Number)report.get("current_version_no")).intValue();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_artifact WHERE file_id=?",Integer.class,fileId)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"该文件已登记，请上传新的人工编辑文件");
        int revision=jdbc.queryForObject("SELECT COALESCE(MAX(artifact_revision_no),0)+1 FROM erp_service_report_artifact WHERE report_id=? AND version_no=? AND artifact_type='docx'",Integer.class,id,version);
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("source","manual_docx");manifest.put("reportVersionNo",version);manifest.put("sourceSha256",f.get("sha256"));manifest.put("manualReviewed",true);
        if(b.get("sourceArtifactId")!=null){
            long source;
            try{source=Long.parseLong(b.get("sourceArtifactId").toString());}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效来源文件ID");}
            var parent=one("SELECT id,sha256,generation_manifest FROM erp_service_report_artifact WHERE id=? AND report_id=? AND version_no=? AND artifact_type='docx'",source,id,version);
            if(Objects.equals(parent.get("sha256"),f.get("sha256")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"上传文件与来源文件相同，请在 Word 完成修改并保存");
            var original=parent.get("generation_manifest")==null?mapper.createObjectNode():mapper.readTree(parent.get("generation_manifest").toString());
            if(original.path("tocPending").asBoolean(false)){
                if(!InspectionTemplateComposer.hasToc(bytes))throw new ResponseStatusException(HttpStatus.CONFLICT,"最终 Word 缺少原生目录域，请不要删除目录后直接送审");
                if(!Boolean.TRUE.equals(b.get("tocUpdated")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请确认已更新并检查 Word 目录及实际页码");
                manifest.put("tocPending",false);manifest.put("tocUpdatedConfirmed",true);
            }
            manifest.put("sourceArtifactId",source);manifest.put("sourceArtifactSha256",parent.get("sha256"));
            if(original.has("layout"))manifest.put("layout",original.path("layout").asText());
            if(Boolean.TRUE.equals(b.get("sourceEvidenceConfirmed"))&&original.path("photos").isArray()){
                manifest.put("photos",original.path("photos").deepCopy());manifest.put("sourceEvidenceConfirmed",true);
                manifest.put("photoEvidenceNote","来源生成文件的证据清单，由编制人确认最终 Word 仍使用相同照片；内嵌图片哈希及DPI仅对应来源文件，不代表对最终Word重新测量");
            }
            if(original.path("templateRevisions").isArray())manifest.put("sourceTemplateRevisions",original.path("templateRevisions").deepCopy());
        }
        jdbc.update("INSERT INTO erp_service_report_artifact(report_id,version_no,file_id,artifact_type,artifact_revision_no,sha256,created_by,generation_manifest) VALUES(?,?,?,'docx',?,?,?,?)",id,version,fileId,revision,f.get("sha256"),actor(a),json(manifest));
        long artifactId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_attachment WHERE report_id=? AND version_no=? AND file_id=?",Integer.class,id,version,fileId)==0)
            jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",id,version,fileId,actor(a));
        audit(actor(a),"report.docx.registered","erp_service_report",id,"version="+version+",artifact="+artifactId);
        return Map.of("reportId",id,"versionNo",version,"artifactId",artifactId,"fileId",fileId,"filename",f.get("original_name"));
    }
    @PostMapping("/reports/{id}/generate-docx") @PreAuthorize("hasAuthority('report:write') and hasAuthority('report:read') and hasAuthority('file:upload')") @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> generateDocx(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) throws Exception {
        var report=reportForGeneration(id,true);
        boolean fillBody=Boolean.TRUE.equals(b.get("fillBody"));
        checkExpectedVersion(b,report,true);
        if(fillBody&&a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("report:read")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权读取报告资料");
        var template=templateForGeneration(b,a);
        long templateId=((Number)template.get("id")).longValue();
        var plan=prepareTemplate(b,template);boolean modular=!plan.revisions().isEmpty();
        if(modular&&!fillBody)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"组合模板须先完成正文预检");
        if(fillBody&&(!Boolean.TRUE.equals(b.get("templateReviewed"))||!Objects.equals(plan.sha256(),b.get("templateSha256"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请先检查模板及旧项目资料、证照和签署区域，再确认生成");
        List<Long> batchIds=b.get("batchIds")==null?List.of():mapper.convertValue(b.get("batchIds"),new TypeReference<List<Long>>(){});
        if(batchIds==null||(!fillBody&&batchIds.isEmpty()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"至少选择一个已审核照片批次或正文填充");
        if(batchIds.stream().anyMatch(x->x==null||x<=0))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效照片批次");
        if(new HashSet<>(batchIds).size()!=batchIds.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片批次不能重复选择");
        String photoMode=b.get("photoMode")==null?"append":b.get("photoMode").toString();
        if(!Set.of("append","replace").contains(photoMode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效照片插入模式");
        if(modular)photoMode="replace";
        if(!modular&&batchIds.isEmpty()&&"replace".equals(photoMode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"替换照片模式至少需要一个已审核照片批次");
        if(!batchIds.isEmpty()&&a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("report:photo:read")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权读取照片批次");
        int version=((Number)report.get("current_version_no")).intValue();
        String photoQuality=Objects.toString(b.get("photoQuality"),"print");PhotoEncoding.dpi(photoQuality);
        List<ReportDocxPhotoInserter.Photo> photos=new ArrayList<>();long totalPhotoBytes=0,embeddedBytes=0;
        List<Map<String,Object>> photoEvidence=new ArrayList<>();
        for(Long batchId:batchIds){
            Map<String,Object> batch=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",batchId);
            if(!"reviewed".equals(batch.get("status"))&&!"locked".equals(batch.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"照片批次尚未通过审核");
            if("append".equals(photoMode)&&"locked".equals(batch.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"追加模式不能重复使用已生成过的照片批次；如需重做请选择替换模式，或以上一版 DOCX 作为模板追加新批次");
            if(batch.get("report_id")!=null&&((Number)batch.get("report_id")).longValue()!=id)throw new ResponseStatusException(HttpStatus.CONFLICT,"照片批次已关联其他报告");
            if(((Number)batch.get("work_package_id")).longValue()!=((Number)report.get("work_package_id")).longValue())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片批次与报告不属于同一服务工作包");
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.*,f.storage_key,f.original_name,f.sha256,f.byte_size FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.batch_id=? ORDER BY p.system_code,p.sort_order,p.id",batchId);
            for(Map<String,Object> photo:rows){
                if(!"confirmed".equals(photo.get("confirmation_status"))||photo.get("system_code")==null||photo.get("taken_on")==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"存在未确认的照片");
                var path=storage.resolve(photo.get("storage_key").toString());
                long size=Files.size(path);
                if(size!=((Number)photo.get("byte_size")).longValue()||size>25L*1024*1024||size<=0||totalPhotoBytes+size>1024L*1024*1024||photos.size()>=500)
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"照片文件大小异常或本次超过500张/1GB原图");
                byte[] data=Files.readAllBytes(path);
                if(!sha(data).equals(photo.get("sha256")))throw new ResponseStatusException(HttpStatus.CONFLICT,"照片原件校验失败");
                totalPhotoBytes+=size;
                var copy=PhotoEncoding.report(data,photoQuality);embeddedBytes+=copy.bytes().length;
                if(embeddedBytes>160L*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"内嵌照片超过160MB，请减少照片或调整打印质量");
                String name=photo.get("original_name").toString();
                photos.add(new ReportDocxPhotoInserter.Photo(photo.get("system_code").toString(),copy.bytes(),name,photo.get("taken_on").toString(),Objects.toString(photo.get("caption"),"")));
                Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("photoId",photo.get("id"));evidence.put("system",photo.get("system_code"));evidence.put("takenOn",photo.get("taken_on").toString());evidence.put("caption",Objects.toString(photo.get("caption"),""));evidence.put("originalName",name);evidence.put("fileId",photo.get("file_id"));evidence.put("sourceSha256",photo.get("sha256"));evidence.put("embeddedSha256",sha(copy.bytes()));evidence.put("sourceBytes",size);evidence.put("embeddedBytes",copy.bytes().length);evidence.put("sourcePixels",List.of(copy.sourceWidth(),copy.sourceHeight()));evidence.put("embeddedPixels",List.of(copy.width(),copy.height()));evidence.put("effectiveDpi",copy.effectiveDpi());evidence.put("orientation",copy.orientation());photoEvidence.add(evidence);
            }
        }
        byte[] templateBytes=plan.bytes();
        var bodySnapshot=fillBody?inspectionSnapshot(id,report):null;
        if(fillBody){checkStructuredRecords(plan,bodySnapshot.path("content"));templateBytes=InspectionDocxRepeatRows.prepare(templateBytes,bodySnapshot.path("content"));}
        byte[] output;
        Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("templateFileId",templateId);manifest.put("templateSha256",plan.sha256());manifest.put("templateRevisions",plan.revisions());
        manifest.put("reportVersionNo",version);manifest.put("batchIds",batchIds);manifest.put("photoMode",photoMode);
        manifest.put("fillBody",fillBody);manifest.put("photoQuality",photoQuality);manifest.put("sourcePhotoBytes",totalPhotoBytes);manifest.put("embeddedPhotoBytes",embeddedBytes);manifest.put("photos",photoEvidence);
        try(XWPFDocument doc=new XWPFDocument(new ByteArrayInputStream(templateBytes));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            if(fillBody){
                var snapshot=bodySnapshot;
                var mapping=bindings(b);
                var inspection=binder.inspect(doc,snapshot.path("content"),report.get("report_no").toString(),report.get("title").toString(),mapping);
                if(!inspection.errors().isEmpty())throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,String.join("；",inspection.errors().stream().limit(8).toList()));
                String hash=planHash(report,plan.sha256(),inspection);
                if(!hash.equals(b.get("bodyPlanSha256")))throw new ResponseStatusException(HttpStatus.CONFLICT,"正文绑定与预检不一致，请重新检查");
                binder.fill(doc,snapshot.path("content"),report.get("report_no").toString(),report.get("title").toString(),mapping);
                manifest.put("profileVersionNo",snapshot.path("versionNo").asInt());manifest.put("bodyPlanSha256",hash);
                manifest.put("basisReferences",snapshot.path("content").path("basisReferences").isArray()?snapshot.path("content").path("basisReferences"):mapper.createArrayNode());
                manifest.put("bodyTargets",inspection.changes().stream().map(c->Map.of("target",c.target(),"fields",c.fields())).toList());
                manifest.put("unmappedFields",inspection.unmappedFields());
            }
            if(modular){
                doc.write(out);
                byte[] paginated=InspectionTemplateComposer.paginatePhotos(out.toByteArray(),photos,plan.systems());
                try(var paged=new XWPFDocument(new ByteArrayInputStream(paginated));var finalOut=new java.io.ByteArrayOutputStream()){
                    inserter.insert(paged,photos,true,plan.systems());paged.write(finalOut);output=finalOut.toByteArray();
                }
                manifest.put("layout","modular-photo-pages-v1");
                manifest.put("tocPending",InspectionTemplateComposer.pendingToc(paginated));
                manifest.put("tocNote","如基础模板含目录域，须在 Word 更新整个目录并核对实际分页");
            }else{
                if(!photos.isEmpty())inserter.insert(doc,photos,"replace".equals(photoMode),plan.systems());
                doc.write(out);output=out.toByteArray();
            }
        } catch(org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"DOCX 模板无效或损坏",e);
        }
        String filename=template.get("original_name").toString().replaceFirst("(?i)\\.docx$","")+(fillBody?"-正文填充草稿.docx":"-照片整理版.docx");
        PrivateFileStorage.StoredFile stored=storage.storeBytes(output,filename,DOCX);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)storage.deleteQuietly(stored.key());}
        });
        try {
            jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
            long fileId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            Integer artifactRevision=jdbc.queryForObject("SELECT COALESCE(MAX(artifact_revision_no),0)+1 FROM erp_service_report_artifact WHERE report_id=? AND version_no=? AND artifact_type='docx'",Integer.class,id,version);
            jdbc.update("INSERT INTO erp_service_report_artifact(report_id,version_no,file_id,artifact_type,artifact_revision_no,sha256,created_by,generation_manifest) VALUES(?,?,?,'docx',?,?,?,?)",id,version,fileId,artifactRevision,stored.sha256(),actor(a),json(manifest));
            jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",id,version,fileId,actor(a));
            for(Long batchId:batchIds)jdbc.update("UPDATE erp_report_photo_batch SET report_id=?,status='locked',locked_at=CURRENT_TIMESTAMP(3) WHERE id=?",id,batchId);
            audit(actor(a),"report.docx.generated","erp_service_report",id,"version="+version+",photos="+photos.size());
            Map<String,Object> result=new LinkedHashMap<>();result.put("reportId",id);result.put("versionNo",version);result.put("artifactRevisionNo",artifactRevision);result.put("fileId",fileId);result.put("filename",filename);result.put("sha256",stored.sha256());result.put("photoCount",photos.size());result.put("photoMode",photoMode);result.put("bodyFilled",fillBody);result.put("fileBytes",stored.size());result.put("sourcePhotoBytes",totalPhotoBytes);result.put("embeddedPhotoBytes",embeddedBytes);result.put("lowDpiCount",photoEvidence.stream().filter(p->((Number)p.get("effectiveDpi")).intValue()<200).count());return result;
        } catch(RuntimeException e){storage.deleteQuietly(stored.key());throw e;}
    }

    @GetMapping("/reports/{id}/deliveries") @PreAuthorize("hasAuthority('delivery:read')")
    public List<Map<String,Object>> deliveries(@PathVariable long id){
        List<Map<String,Object>> result=jdbc.queryForList("SELECT d.*,i.display_name creator_name,a.file_id,a.sha256 artifact_sha256,f.original_name FROM erp_service_delivery d JOIN erp_identity i ON i.id=d.created_by LEFT JOIN erp_service_report_artifact a ON a.id=d.artifact_id LEFT JOIN erp_file_object f ON f.id=a.file_id WHERE d.report_id=? ORDER BY d.created_at DESC",id);
        for(Map<String,Object> delivery:result)delivery.put("events",jdbc.queryForList("SELECT e.*,i.display_name actor_name FROM erp_service_delivery_event e JOIN erp_identity i ON i.id=e.actor_identity_id WHERE e.delivery_id=? ORDER BY e.id",delivery.get("id")));
        return result;
    }
    @PostMapping("/reports/{id}/deliveries") @PreAuthorize("hasAuthority('delivery:write')") @Transactional
    public Map<String,Object> createDelivery(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> report=one("SELECT * FROM erp_service_report WHERE id=? FOR UPDATE",id);
        int version=b.get("versionNo")==null?((Number)report.get("current_version_no")).intValue():Integer.parseInt(b.get("versionNo").toString());
        var v=one("SELECT submitted_artifact_id,version_issued_at FROM erp_service_report_version WHERE report_id=? AND version_no=?",id,version);
        if(v.get("version_issued_at")==null||v.get("submitted_artifact_id")==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"此版本没有可确认的签发文件，不能登记新交付；历史记录仍保留");
        long artifact=((Number)v.get("submitted_artifact_id")).longValue();
        one("SELECT id FROM erp_service_report_artifact WHERE id=? AND report_id=? AND version_no=? AND artifact_type='docx'",artifact,id,version);
        String method=b.get("deliveryMethod")==null?"courier":b.get("deliveryMethod").toString();
        if(!Set.of("courier","hand_delivery","electronic").contains(method))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效交付方式");
        jdbc.update("INSERT INTO erp_service_delivery(report_id,version_no,artifact_id,delivery_method,recipient_name,recipient_phone,recipient_organization,recipient_address,courier_name,tracking_no,status,notes,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,'prepared',?,?)",
            id,version,artifact,method,req(b,"recipientName"),b.get("recipientPhone"),b.get("recipientOrganization"),req(b,"recipientAddress"),b.get("courierName"),b.get("trackingNo"),b.get("notes"),actor(a));
        long deliveryId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        eventDelivery(deliveryId,null,"prepared",actor(a),"创建交付登记");
        audit(actor(a),"report.delivery.created","erp_service_delivery",deliveryId,method);
        return one("SELECT * FROM erp_service_delivery WHERE id=?",deliveryId);
    }
    @PostMapping("/deliveries/{id}/status") @PreAuthorize("hasAuthority('delivery:write')") @Transactional
    public Map<String,Object> updateDelivery(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object>d=one("SELECT * FROM erp_service_delivery WHERE id=? FOR UPDATE",id);
        String old=d.get("status").toString(),next=req(b,"status");
        Map<String,Set<String>> transitions=Map.of(
            "prepared",Set.of("dispatched","cancelled"),
            "dispatched",Set.of("delivered","returned"),
            "returned",Set.of("dispatched","cancelled"),
            "delivered",Set.of(),
            "cancelled",Set.of()
        );
        if(!transitions.getOrDefault(old,Set.of()).contains(next))throw new ResponseStatusException(HttpStatus.CONFLICT,"不允许的交付状态转换");
        if("dispatched".equals(next))jdbc.update("UPDATE erp_service_delivery SET status=?,dispatched_at=CURRENT_TIMESTAMP(3),courier_name=COALESCE(?,courier_name),tracking_no=COALESCE(?,tracking_no) WHERE id=?",next,b.get("courierName"),b.get("trackingNo"),id);
        else if("delivered".equals(next))jdbc.update("UPDATE erp_service_delivery SET status=?,delivered_at=CURRENT_TIMESTAMP(3) WHERE id=?",next,id);
        else jdbc.update("UPDATE erp_service_delivery SET status=? WHERE id=?",next,id);
        eventDelivery(id,old,next,actor(a),Objects.toString(b.get("notes"),""));
        audit(actor(a),"report.delivery."+next,"erp_service_delivery",id,next);
        return one("SELECT * FROM erp_service_delivery WHERE id=?",id);
    }
}
