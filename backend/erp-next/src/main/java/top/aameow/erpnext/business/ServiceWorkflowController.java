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

    public ServiceWorkflowController(JdbcTemplate jdbc,ObjectMapper mapper,PrivateFileStorage storage,ReportDocxPhotoInserter inserter) {
        this.jdbc=jdbc;this.mapper=mapper;this.storage=storage;this.inserter=inserter;
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
        return jdbc.queryForList("SELECT v.*,i.display_name creator_name,(SELECT COUNT(*) FROM erp_service_visit_staff s WHERE s.visit_id=v.id) staff_count,(SELECT COUNT(*) FROM erp_service_visit_issue x WHERE x.visit_id=v.id) issue_count FROM erp_service_visit v LEFT JOIN erp_identity i ON i.id=v.created_by WHERE v.work_package_id=? ORDER BY v.visit_date DESC,v.id DESC",id);
    }
    @PostMapping("/work-packages/{id}/visits") @PreAuthorize("hasAuthority('service:visit:write')") @Transactional
    public Map<String,Object> createVisit(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_service_work_package WHERE id=?",id);
        String title=req(b,"title"),date=req(b,"visitDate"),type=b.get("visitType")==null?"inspection":b.get("visitType").toString();
        if(!Set.of("inspection","rectification_review","supplement","maintenance","other").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效现场服务类型");
        Integer visitNo=jdbc.queryForObject("SELECT COALESCE(MAX(visit_no),0)+1 FROM erp_service_visit WHERE work_package_id=?",Integer.class,id);
        jdbc.update("INSERT INTO erp_service_visit(work_package_id,visit_no,visit_date,visit_type,title,summary,result_summary,status,created_by) VALUES(?,?,?,?,?,?,?,'completed',?)",
            id,visitNo,date,type,title,b.get("summary"),b.get("resultSummary"),actor(a));
        long visitId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        Object staff=b.get("staff");
        if(staff instanceof List<?> list) for(Object item:list){
            if(!(item instanceof Map<?,?> m))continue;
            long employee=Long.parseLong(Objects.toString(m.get("employeeId")));
            String role=Objects.toString(m.get("role"),"field_operator");
            one("SELECT id FROM erp_employee WHERE id=?",employee);
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
    public List<String> photoSystems(){return new ArrayList<>(inserter.supportedSystems());}

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
        batch.put("photos",jdbc.queryForList("SELECT p.id,p.file_id,p.system_code,p.taken_on,p.caption,p.sort_order,p.confirmation_status,p.confirmed_by,p.confirmed_at,f.original_name,f.media_type,f.byte_size file_size,f.sha256 FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.batch_id=? ORDER BY p.sort_order,p.id",id));
        return batch;
    }
    @PostMapping("/photo-batches/{id}/photos") @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> addPhoto(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> batch=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",id);
        if(!"collecting".equals(batch.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"照片批次已提交审核，不能继续添加");
        long fileId=Long.parseLong(req(b,"fileId"));
        Map<String,Object> file=one("SELECT id,uploaded_by,media_type FROM erp_file_object WHERE id=?",fileId);
        if(((Number)file.get("uploaded_by")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能添加自己上传的照片");
        if(!Set.of("image/jpeg","image/png").contains(file.get("media_type").toString()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持 JPG/PNG 照片");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM erp_report_photo WHERE batch_id=?",Integer.class,id);
        if(count!=null&&count>=500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"单批最多 500 张照片");
        Integer order=jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM erp_report_photo WHERE batch_id=?",Integer.class,id);
        jdbc.update("INSERT INTO erp_report_photo(batch_id,file_id,taken_on,caption,sort_order) VALUES(?,?,?,?,?)",id,fileId,b.get("takenOn"),b.get("caption"),order);
        long photoId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(actor(a),"report.photo.added","erp_report_photo",photoId,"batch="+id);
        return one("SELECT p.*,f.original_name,f.media_type,f.byte_size file_size FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.id=?",photoId);
    }
    @PutMapping("/photos/{id}") @PreAuthorize("hasAuthority('report:photo:write')") @Transactional
    public Map<String,Object> updatePhoto(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> p=one("SELECT p.*,b.status batch_status FROM erp_report_photo p JOIN erp_report_photo_batch b ON b.id=p.batch_id WHERE p.id=? FOR UPDATE",id);
        if(!"collecting".equals(p.get("batch_status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前批次已提交，照片不可修改");
        String system=b.get("systemCode")==null?null:b.get("systemCode").toString();
        if(system!=null&&!inserter.supportedSystems().contains(system))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效消防系统分类");
        String confirmation=b.get("confirmed")==null?null:(Boolean.parseBoolean(b.get("confirmed").toString())?"confirmed":"pending");
        jdbc.update("UPDATE erp_report_photo SET system_code=?,taken_on=?,caption=?,sort_order=COALESCE(?,sort_order),confirmation_status=COALESCE(?,confirmation_status),confirmed_by=CASE WHEN ?='confirmed' THEN ? WHEN ?='pending' THEN NULL ELSE confirmed_by END,confirmed_at=CASE WHEN ?='confirmed' THEN CURRENT_TIMESTAMP(3) WHEN ?='pending' THEN NULL ELSE confirmed_at END WHERE id=?",
            system,b.containsKey("takenOn")?b.get("takenOn"):p.get("taken_on"),b.containsKey("caption")?b.get("caption"):p.get("caption"),
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
    @PostMapping("/photo-batches/{id}/review") @PreAuthorize("hasAuthority('report:review')") @Transactional
    public Map<String,Object> reviewBatch(@PathVariable long id,Authentication a){
        Map<String,Object>b=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",id);
        if(!"submitted".equals(b.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已提交的照片批次可以审核");
        if(((Number)b.get("created_by")).longValue()==actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"照片整理人不能审核自己的批次");
        Integer qualified=jdbc.queryForObject("SELECT COUNT(*) FROM erp_employee e JOIN erp_employee_qualification q ON q.employee_id=e.id WHERE e.identity_id=? AND e.employee_type='engineer' AND e.status='active' AND q.status='valid' AND (q.expires_on IS NULL OR q.expires_on>=CURRENT_DATE)",Integer.class,actor(a));
        if(qualified==null||qualified==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"照片批次审核必须由具有有效工程师资格的员工完成");
        jdbc.update("UPDATE erp_report_photo_batch SET status='reviewed',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP(3) WHERE id=?",actor(a),id);
        audit(actor(a),"report.photo_batch.reviewed","erp_report_photo_batch",id,"reviewed");
        return batchDetail(id);
    }

    @PostMapping("/reports/{id}/generate-docx") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> generateDocx(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) throws Exception {
        Map<String,Object> report=one("SELECT * FROM erp_service_report WHERE id=? FOR UPDATE",id);
        if(!Set.of("draft","returned").contains(report.get("status").toString()))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿或退回状态的报告可以生成 DOCX");
        long templateId=Long.parseLong(req(b,"templateFileId"));
        Map<String,Object> template=one("SELECT * FROM erp_file_object WHERE id=?",templateId);
        if(!DOCX.equals(template.get("media_type"))||!template.get("original_name").toString().toLowerCase(Locale.ROOT).endsWith(".docx"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板必须是 DOCX 文件");
        List<Long> batchIds=mapper.convertValue(b.get("batchIds"),new TypeReference<List<Long>>(){});
        if(batchIds==null||batchIds.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"至少选择一个已审核照片批次");
        int version=((Number)report.get("current_version_no")).intValue();
        List<ReportDocxPhotoInserter.Photo> photos=new ArrayList<>();
        for(Long batchId:batchIds){
            Map<String,Object> batch=one("SELECT * FROM erp_report_photo_batch WHERE id=? FOR UPDATE",batchId);
            if(!"reviewed".equals(batch.get("status"))&&!"locked".equals(batch.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"照片批次尚未通过审核");
            if(batch.get("report_id")!=null&&((Number)batch.get("report_id")).longValue()!=id)throw new ResponseStatusException(HttpStatus.CONFLICT,"照片批次已关联其他报告");
            if(((Number)batch.get("work_package_id")).longValue()!=((Number)report.get("work_package_id")).longValue())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片批次与报告不属于同一服务工作包");
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.*,f.storage_key,f.original_name FROM erp_report_photo p JOIN erp_file_object f ON f.id=p.file_id WHERE p.batch_id=? ORDER BY p.system_code,p.sort_order,p.id",batchId);
            for(Map<String,Object> photo:rows){
                if(!"confirmed".equals(photo.get("confirmation_status"))||photo.get("system_code")==null||photo.get("taken_on")==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"存在未确认的照片");
                byte[] data=Files.readAllBytes(storage.resolve(photo.get("storage_key").toString()));
                photos.add(new ReportDocxPhotoInserter.Photo(photo.get("system_code").toString(),data,photo.get("original_name").toString(),photo.get("taken_on").toString(),Objects.toString(photo.get("caption"),"")));
            }
        }
        byte[] templateBytes=Files.readAllBytes(storage.resolve(template.get("storage_key").toString()));
        byte[] output;
        try(XWPFDocument doc=new XWPFDocument(new ByteArrayInputStream(templateBytes));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            inserter.insert(doc,photos);
            doc.write(out);output=out.toByteArray();
        } catch(org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"DOCX 模板无效或损坏",e);
        }
        String filename=template.get("original_name").toString().replaceFirst("(?i)\\.docx$","")+"-照片整理版.docx";
        PrivateFileStorage.StoredFile stored=storage.storeBytes(output,filename,DOCX);
        try {
            jdbc.update("INSERT INTO erp_file_object(storage_key,original_name,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?,?)",stored.key(),stored.originalName(),stored.mediaType(),stored.size(),stored.sha256(),actor(a));
            long fileId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            Integer artifactRevision=jdbc.queryForObject("SELECT COALESCE(MAX(artifact_revision_no),0)+1 FROM erp_service_report_artifact WHERE report_id=? AND version_no=? AND artifact_type='docx'",Integer.class,id,version);
            jdbc.update("INSERT INTO erp_service_report_artifact(report_id,version_no,file_id,artifact_type,artifact_revision_no,sha256,created_by) VALUES(?,?,?,'docx',?,?,?)",id,version,fileId,artifactRevision,stored.sha256(),actor(a));
            jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",id,version,fileId,actor(a));
            for(Long batchId:batchIds)jdbc.update("UPDATE erp_report_photo_batch SET report_id=?,status='locked',locked_at=CURRENT_TIMESTAMP(3) WHERE id=?",id,batchId);
            audit(actor(a),"report.docx.generated","erp_service_report",id,"version="+version+",photos="+photos.size());
            return Map.of("reportId",id,"versionNo",version,"artifactRevisionNo",artifactRevision,"fileId",fileId,"filename",filename,"sha256",stored.sha256(),"photoCount",photos.size());
        } catch(RuntimeException e){storage.deleteQuietly(stored.key());throw e;}
    }

    @GetMapping("/reports/{id}/deliveries") @PreAuthorize("hasAuthority('delivery:read')")
    public List<Map<String,Object>> deliveries(@PathVariable long id){
        return jdbc.queryForList("SELECT d.*,i.display_name creator_name FROM erp_service_delivery d JOIN erp_identity i ON i.id=d.created_by WHERE d.report_id=? ORDER BY d.created_at DESC",id);
    }
    @PostMapping("/reports/{id}/deliveries") @PreAuthorize("hasAuthority('delivery:write')") @Transactional
    public Map<String,Object> createDelivery(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> report=one("SELECT * FROM erp_service_report WHERE id=?",id);
        if(!Set.of("issued","archived").contains(report.get("status").toString()))throw new ResponseStatusException(HttpStatus.CONFLICT,"报告签发后才能登记交付");
        int version=b.get("versionNo")==null?((Number)report.get("current_version_no")).intValue():Integer.parseInt(b.get("versionNo").toString());
        one("SELECT report_id FROM erp_service_report_version WHERE report_id=? AND version_no=?",id,version);
        Integer artifact=jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_artifact WHERE report_id=? AND version_no=? AND artifact_type='docx'",Integer.class,id,version);
        if(artifact==null||artifact==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"当前报告版本尚未生成 DOCX 文件，不能登记正式交付");
        String method=b.get("deliveryMethod")==null?"courier":b.get("deliveryMethod").toString();
        if(!Set.of("courier","hand_delivery","electronic").contains(method))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效交付方式");
        jdbc.update("INSERT INTO erp_service_delivery(report_id,version_no,delivery_method,recipient_name,recipient_phone,recipient_organization,recipient_address,courier_name,tracking_no,status,notes,created_by) VALUES(?,?,?,?,?,?,?,?,?,'prepared',?,?)",
            id,version,method,req(b,"recipientName"),b.get("recipientPhone"),b.get("recipientOrganization"),req(b,"recipientAddress"),b.get("courierName"),b.get("trackingNo"),b.get("notes"),actor(a));
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
