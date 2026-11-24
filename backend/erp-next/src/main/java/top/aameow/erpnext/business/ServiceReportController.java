package top.aameow.erpnext.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/business")
public class ServiceReportController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ServiceReportController(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private String req(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空");return v.toString().trim();}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> r=jdbc.queryForList(sql,args);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");return r.get(0);}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> report(long id){return one("SELECT * FROM erp_service_report WHERE id=? FOR UPDATE",id);}
    private Map<String,Object> version(long id,int no){return one("SELECT * FROM erp_service_report_version WHERE report_id=? AND version_no=?",id,no);}
    private Map<String,Object> content(long id,Map<String,Object> report){return one("SELECT * FROM erp_service_report_version WHERE report_id=? AND version_no=?",id,report.get("current_version_no"));}
    private void event(long id,String from,String to,long who,String reason){
        jdbc.update("INSERT INTO erp_service_report_event(report_id,from_status,to_status,actor_identity_id,reason) VALUES(?,?,?,?,?)",id,from,to,who,reason);
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('from',?,'to',?,'reason',?))",who,"report.status."+to,"erp_service_report",Long.toString(id),UUID.randomUUID().toString(),from,to,reason);
    }
    private void assertDraft(Map<String,Object> r){
        String s=r.get("status").toString();
        if(!Set.of("draft","returned").contains(s))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前报告状态不可编辑");
    }
    private void requireVersion(Map<String,Object>b,Map<String,Object>r){
        Object expected=b.get("expectedVersionNo");
        if(expected==null||Integer.parseInt(expected.toString())!=((Number)r.get("current_version_no")).intValue())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"报告版本已更新，请刷新后重试");
    }
    // Never trust a client-supplied inspection snapshot. Resolve the immutable profile version in the report's own work package.
    private Map<String,Object> bindInspectionProfile(Map<String,Object> request,Map<String,Object> data,Map<String,Object> previous,long workPackage,String type,Authentication a){
        Map<String,Object> result=new LinkedHashMap<>(data);
        result.remove("inspectionProfile");
        if(!request.containsKey("inspectionProfileVersionNo")){
            if(previous.get("inspectionProfile")!=null)result.put("inspectionProfile",previous.get("inspectionProfile"));
            return result;
        }
        Object raw=request.get("inspectionProfileVersionNo");
        if(raw==null)return result;
        int version;
        try{version=Integer.parseInt(raw.toString());}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"检测资料版本无效");}
        if(version<=0||!"inspection".equals(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"只有消防检测报告可以绑定有效的检测资料版本");
        if(a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("service:read"))||a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("report:read")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权读取检测资料");
        Map<String,Object> row=one("SELECT content_json FROM erp_inspection_profile_version WHERE work_package_id=? AND version_no=?",workPackage,version);
        Object snapshot=parse(row.get("content_json").toString());
        result.put("inspectionProfile",Map.of("workPackageId",workPackage,"versionNo",version,"content",snapshot));
        return result;
    }
    private Map<String,Object> insertVersion(long reportId,int no,Map<String,Object> data,long who){
        String body=json(data);
        jdbc.update("INSERT INTO erp_service_report_version(report_id,version_no,content_json,content_sha256,created_by) VALUES(?,?,?,?,?)",reportId,no,body,sha(body),who);
        return version(reportId,no);
    }
    private long versionAuthor(long id,Map<String,Object> r){
        return ((Number)content(id,r).get("created_by")).longValue();
    }
    private Map<String,Object> detail(long id){
        Map<String,Object> r=new LinkedHashMap<>(report(id));
        Map<String,Object> v=content(id,r);
        r.put("content",mapper.convertValue(parse(v.get("content_json").toString()),new TypeReference<Map<String,Object>>(){}));
        r.put("contentSha256",v.get("content_sha256"));
        r.put("versionCreatedBy",v.get("created_by"));
        r.put("versions",jdbc.queryForList("SELECT version_no,content_sha256,archive_manifest,created_by,created_at FROM erp_service_report_version WHERE report_id=? ORDER BY version_no DESC",id));
        r.put("attachments",jdbc.queryForList("SELECT a.id attachment_id,f.id file_id,f.original_name,f.media_type,f.byte_size file_size,f.sha256,a.version_no FROM erp_service_report_attachment a JOIN erp_file_object f ON f.id=a.file_id WHERE a.report_id=? AND a.version_no=? ORDER BY a.id",id,r.get("current_version_no")));
        r.put("artifacts",jdbc.queryForList("SELECT a.id,a.version_no,a.artifact_type,a.artifact_revision_no,a.sha256,a.created_at,a.file_id,a.generation_manifest,f.original_name,f.byte_size file_size FROM erp_service_report_artifact a JOIN erp_file_object f ON f.id=a.file_id WHERE a.report_id=? ORDER BY a.version_no DESC,a.artifact_revision_no DESC",id));
        r.put("events",jdbc.queryForList("SELECT e.*,i.display_name actor_name FROM erp_service_report_event e JOIN erp_identity i ON i.id=e.actor_identity_id WHERE e.report_id=? ORDER BY e.id",id));
        return r;
    }
    private Object parse(String s){try{return mapper.readValue(s,Object.class);}catch(Exception e){throw new IllegalStateException("Stored report JSON is invalid",e);}}
    private void requireEngineerReviewer(long identityId){
        Integer qualified=jdbc.queryForObject("SELECT COUNT(*) FROM erp_employee e JOIN erp_employee_qualification q ON q.employee_id=e.id WHERE e.identity_id=? AND e.employee_type='engineer' AND e.status='active' AND q.status='valid' AND (q.expires_on IS NULL OR q.expires_on>=CURRENT_DATE)",Integer.class,identityId);
        if(qualified==null||qualified==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"报告内部审核必须由具有有效工程师资格的员工完成");
    }
    private void checkContent(Map<String,Object> data){
        if(data==null||data.get("summary")==null||data.get("summary").toString().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"提交前必须填写报告摘要");
        if(data.get("conclusion")==null||data.get("conclusion").toString().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"提交前必须填写服务结论");
    }

    @GetMapping("/projects/{projectId}/reports") @PreAuthorize("hasAuthority('report:read')")
    public List<Map<String,Object>> byProject(@PathVariable long projectId){
        one("SELECT id FROM erp_project WHERE id=?",projectId);
        return jdbc.queryForList("SELECT r.id,r.report_no,r.title,r.report_type,r.status,r.revision_no,r.current_version_no,r.updated_at,w.package_no,w.title work_package_title FROM erp_service_report r JOIN erp_service_work_package w ON w.id=r.work_package_id WHERE w.project_id=? ORDER BY r.updated_at DESC",projectId);
    }
    @GetMapping("/work-packages/{workPackageId}/reports") @PreAuthorize("hasAuthority('report:read')")
    public List<Map<String,Object>> byWorkPackage(@PathVariable long workPackageId){
        one("SELECT id FROM erp_service_work_package WHERE id=?",workPackageId);
        return jdbc.queryForList("SELECT id,report_no,title,report_type,status,revision_no,current_version_no,updated_at FROM erp_service_report WHERE work_package_id=? ORDER BY updated_at DESC",workPackageId);
    }
    @PostMapping("/work-packages/{workPackageId}/reports") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> create(@PathVariable long workPackageId,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_service_work_package WHERE id=?",workPackageId);
        String no=req(b,"reportNo"),title=req(b,"title"),type=b.get("reportType")==null?"service":b.get("reportType").toString();
        if(!Set.of("service","inspection","maintenance").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效报告类型");
        Map<String,Object> data=b.get("content") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        data=bindInspectionProfile(b,data,Map.of(),workPackageId,type,a);
        jdbc.update("INSERT INTO erp_service_report(report_no,work_package_id,title,report_type,created_by) VALUES(?,?,?,?,?)",no,workPackageId,title,type,actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        insertVersion(id,1,data,actor(a));
        event(id,null,"draft",actor(a),"创建报告");
        return detail(id);
    }
    @GetMapping("/reports/{id}") @PreAuthorize("hasAuthority('report:read')")
    public Map<String,Object> get(@PathVariable long id){return detail(id);}
    @PutMapping("/reports/{id}") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);assertDraft(r);requireVersion(b,r);
        if(b.get("title")!=null)jdbc.update("UPDATE erp_service_report SET title=? WHERE id=?",req(b,"title"),id);
        Map<String,Object> data=b.get("content") instanceof Map<?,?> m?(Map<String,Object>)m:null;
        if(data==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"报告内容必须是对象");
        int old=((Number)r.get("current_version_no")).intValue(),next=old+1;
        Map<String,Object> previous=mapper.convertValue(parse(content(id,r).get("content_json").toString()),new TypeReference<Map<String,Object>>(){});
        data=bindInspectionProfile(b,data,previous,((Number)r.get("work_package_id")).longValue(),r.get("report_type").toString(),a);
        insertVersion(id,next,data,actor(a));
        jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) SELECT report_id,?,file_id,? FROM erp_service_report_attachment WHERE report_id=? AND version_no=?",next,actor(a),id,old);
        if("returned".equals(r.get("status"))){jdbc.update("UPDATE erp_service_report SET status='draft',current_version_no=?,return_reason=NULL,reviewer_id=NULL WHERE id=?",next,id);event(id,"returned","draft",actor(a),"根据退回意见修订");}
        else jdbc.update("UPDATE erp_service_report SET current_version_no=? WHERE id=?",next,id);
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('version',?))",actor(a),"report.version.updated","erp_service_report",Long.toString(id),UUID.randomUUID().toString(),next);
        return detail(id);
    }
    @PostMapping("/reports/{id}/attachments") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> attach(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);assertDraft(r);long fileId=Long.parseLong(req(b,"fileId"));
        Map<String,Object> f=one("SELECT id,uploaded_by FROM erp_file_object WHERE id=?",fileId);
        if(((Number)f.get("uploaded_by")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的文件");
        int v=((Number)r.get("current_version_no")).intValue();
        jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",id,v,fileId,actor(a));
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('fileId',?))",actor(a),"report.attachment.added","erp_service_report",Long.toString(id),UUID.randomUUID().toString(),fileId);
        return detail(id);
    }
    @PostMapping("/reports/{id}/submit") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> submit(@PathVariable long id,Authentication a){
        Map<String,Object> r=report(id);if(!"draft".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿报告可以提交");
        Map<String,Object> v=content(id,r);Map<String,Object> data=mapper.convertValue(parse(v.get("content_json").toString()),new TypeReference<Map<String,Object>>(){});
        checkContent(data);jdbc.update("UPDATE erp_service_report SET status='submitted',reviewer_id=NULL,return_reason=NULL WHERE id=?",id);
        event(id,"draft","submitted",actor(a),"提交审核");return detail(id);
    }
    @PostMapping("/reports/{id}/start-review") @PreAuthorize("hasAuthority('report:review')") @Transactional
    public Map<String,Object> startReview(@PathVariable long id,Authentication a){
        Map<String,Object> r=report(id);if(!"submitted".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已提交报告可以开始审核");
        requireEngineerReviewer(actor(a));
        if(versionAuthor(id,r)==actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"报告编制人不能审核自己的报告");
        jdbc.update("UPDATE erp_service_report SET status='reviewing',reviewer_id=? WHERE id=?",actor(a),id);event(id,"submitted","reviewing",actor(a),"开始审核");return detail(id);
    }
    @PostMapping("/reports/{id}/return") @PreAuthorize("hasAuthority('report:review')") @Transactional
    public Map<String,Object> returnReport(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);if(!"reviewing".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有审核中的报告可以退回");
        if(((Number)r.get("reviewer_id")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有当前审核人可以退回");
        String reason=req(b,"reason");jdbc.update("UPDATE erp_service_report SET status='returned',return_reason=? WHERE id=?",reason,id);event(id,"reviewing","returned",actor(a),reason);return detail(id);
    }
    @PostMapping("/reports/{id}/approve") @PreAuthorize("hasAuthority('report:review')") @Transactional
    public Map<String,Object> approve(@PathVariable long id,Authentication a){
        Map<String,Object> r=report(id);if(!"reviewing".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有审核中的报告可以通过");
        if(((Number)r.get("reviewer_id")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有当前审核人可以审核");
        jdbc.update("UPDATE erp_service_report SET status='approved' WHERE id=?",id);event(id,"reviewing","approved",actor(a),"审核通过");return detail(id);
    }
    @PostMapping("/reports/{id}/issue") @PreAuthorize("hasAuthority('report:issue')") @Transactional
    public Map<String,Object> issue(@PathVariable long id,Authentication a){
        Map<String,Object> r=report(id);if(!"approved".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有审核通过的报告可以签发");
        long author=versionAuthor(id,r),reviewer=((Number)r.get("reviewer_id")).longValue();
        if(actor(a)==author||actor(a)==reviewer)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"签发人必须独立于编制人和审核人");
        jdbc.update("UPDATE erp_service_report SET status='issued',issuer_id=?,issued_at=CURRENT_TIMESTAMP(3) WHERE id=?",actor(a),id);event(id,"approved","issued",actor(a),"报告签发");return detail(id);
    }
    @PostMapping("/reports/{id}/archive") @PreAuthorize("hasAuthority('report:issue')") @Transactional
    public Map<String,Object> archive(@PathVariable long id,Authentication a){
        Map<String,Object> r=report(id);if(!"issued".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已签发报告可以归档");
        Map<String,Object> v=content(id,r);
        List<Map<String,Object>> files=jdbc.queryForList("SELECT f.id,f.original_name,f.media_type,f.byte_size file_size,f.sha256 FROM erp_service_report_attachment a JOIN erp_file_object f ON f.id=a.file_id WHERE a.report_id=? AND a.version_no=? ORDER BY f.id",id,r.get("current_version_no"));
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("reportId",id);manifest.put("reportNo",r.get("report_no"));manifest.put("revisionNo",r.get("revision_no"));manifest.put("versionNo",r.get("current_version_no"));manifest.put("contentSha256",v.get("content_sha256"));manifest.put("attachments",files);manifest.put("issuedAt",r.get("issued_at"));
        jdbc.update("UPDATE erp_service_report_version SET archive_manifest=? WHERE report_id=? AND version_no=?",json(manifest),id,r.get("current_version_no"));
        jdbc.update("UPDATE erp_service_report SET status='archived',archived_at=CURRENT_TIMESTAMP(3),archive_manifest=? WHERE id=?",json(manifest),id);
        event(id,"issued","archived",actor(a),"归档快照已固定");return detail(id);
    }
    @PostMapping("/reports/{id}/withdraw") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> withdraw(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);if(!Set.of("submitted","reviewing").contains(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前状态不可撤回");
        if(versionAuthor(id,r)!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有本版本编制人可以撤回");
        String reason=req(b,"reason");String old=r.get("status").toString();jdbc.update("UPDATE erp_service_report SET status='withdrawn' WHERE id=?",id);event(id,old,"withdrawn",actor(a),reason);return detail(id);
    }
    @PostMapping("/reports/{id}/revise") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> revise(@PathVariable long id,Authentication a){
        Map<String,Object> r=report(id);if(!Set.of("issued","archived").contains(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已签发或已归档报告可以创建修订版");
        int old=((Number)r.get("current_version_no")).intValue(),next=old+1;
        Map<String,Object> previous=content(id,r);Map<String,Object> data=mapper.convertValue(parse(previous.get("content_json").toString()),new TypeReference<Map<String,Object>>(){});
        insertVersion(id,next,data,actor(a));
        jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) SELECT report_id,?,file_id,? FROM erp_service_report_attachment WHERE report_id=? AND version_no=?",next,actor(a),id,old);
        jdbc.update("UPDATE erp_service_report SET status='draft',revision_no=revision_no+1,current_version_no=?,reviewer_id=NULL,issuer_id=NULL,return_reason=NULL,issued_at=NULL,archived_at=NULL,archive_manifest=NULL WHERE id=?",next,id);
        event(id,r.get("status").toString(),"draft",actor(a),"创建新修订版");return detail(id);
    }
}
