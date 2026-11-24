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
        if(!Set.of("draft","returned","withdrawn").contains(s))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前报告状态不可编辑");
    }
    private void requireVersion(Map<String,Object>b,Map<String,Object>r){
        Object expected=b.get("expectedVersionNo");
        if(expected==null||positiveInt(expected)!=((Number)r.get("current_version_no")).intValue())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"报告版本已更新，请刷新后重试");
    }
    private int positiveInt(Object raw){
        try{int n=Integer.parseInt(raw.toString());if(n>0)return n;}catch(RuntimeException ignored){}
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"版本号无效");
    }
    private void checkActionVersion(Map<String,Object>b,Map<String,Object>r){
        if(b!=null&&b.containsKey("expectedVersionNo"))requireVersion(b,r);
    }
    private void requireNotContributor(long id,Map<String,Object> r,long actor){
        var v=content(id,r);
        if(((Number)v.get("created_by")).longValue()==actor||(v.get("submitted_by") instanceof Number submitter&&submitter.longValue()==actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"本版本编制人或提交人不能审核或签发自己的报告");
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM erp_service_report_artifact WHERE id=? AND created_by=?",Integer.class,v.get("submitted_artifact_id"),actor);
        if(n!=null&&n>0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"所提交文件的制作者不能审核或签发该报告");
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
    private Map<String,Object> insertVersion(long reportId,int no,Map<String,Object> data,long who,String title){
        String body=json(data);
        jdbc.update("INSERT INTO erp_service_report_version(report_id,version_no,content_json,content_sha256,created_by,title_snapshot) VALUES(?,?,?,?,?,?)",reportId,no,body,sha(body),who,title);
        return version(reportId,no);
    }
    private long versionAuthor(long id,Map<String,Object> r){
        return ((Number)content(id,r).get("created_by")).longValue();
    }
    private Map<String,Object> detail(long id){
        Map<String,Object> r=new LinkedHashMap<>(one("SELECT * FROM erp_service_report WHERE id=?",id));
        Map<String,Object> v=content(id,r);
        r.put("content",mapper.convertValue(parse(v.get("content_json").toString()),new TypeReference<Map<String,Object>>(){}));
        r.put("contentSha256",v.get("content_sha256"));
        r.put("versionCreatedBy",v.get("created_by"));
        r.put("versions",jdbc.queryForList("SELECT version_no,content_sha256,archive_manifest,created_by,created_at,title_snapshot,submitted_artifact_id,submitted_by,reviewed_by,issued_by,version_issued_at FROM erp_service_report_version WHERE report_id=? ORDER BY version_no DESC",id));
        r.put("selectedArtifactId",v.get("submitted_artifact_id"));r.put("submittedBy",v.get("submitted_by"));
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
        if(no.length()>100||title.length()>240)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"报告编号或标题过长");
        if(!Set.of("service","inspection","maintenance").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效报告类型");
        Map<String,Object> data=b.get("content") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        data=bindInspectionProfile(b,data,Map.of(),workPackageId,type,a);
        jdbc.update("INSERT INTO erp_service_report(report_no,work_package_id,title,report_type,created_by) VALUES(?,?,?,?,?)",no,workPackageId,title,type,actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        insertVersion(id,1,data,actor(a),title);
        event(id,null,"draft",actor(a),"创建报告");
        return detail(id);
    }
    @GetMapping("/reports/{id}") @PreAuthorize("hasAuthority('report:read')")
    @Transactional(readOnly=true)
    public Map<String,Object> get(@PathVariable long id){return detail(id);}
    @GetMapping("/reports/{id}/versions/{no}") @PreAuthorize("hasAuthority('report:read')") @Transactional(readOnly=true)
    public Map<String,Object> getVersion(@PathVariable long id,@PathVariable int no){
        var v=new LinkedHashMap<>(version(id,no));
        v.put("content",parse(v.remove("content_json").toString()));
        v.put("attachments",jdbc.queryForList("SELECT a.id attachment_id,f.id file_id,f.original_name,f.media_type,f.byte_size file_size,f.sha256 FROM erp_service_report_attachment a JOIN erp_file_object f ON f.id=a.file_id WHERE a.report_id=? AND a.version_no=? ORDER BY a.id",id,no));
        v.put("artifacts",jdbc.queryForList("SELECT a.id,a.file_id,a.artifact_revision_no,a.sha256,f.original_name FROM erp_service_report_artifact a JOIN erp_file_object f ON f.id=a.file_id WHERE a.report_id=? AND a.version_no=? ORDER BY a.artifact_revision_no DESC",id,no));
        return v;
    }
    @PutMapping("/reports/{id}") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);assertDraft(r);requireVersion(b,r);
        String title=b.get("title")!=null?req(b,"title"):r.get("title").toString();
        if(title.length()>240)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"报告标题过长");
        if(b.get("title")!=null)jdbc.update("UPDATE erp_service_report SET title=? WHERE id=?",title,id);
        Map<String,Object> data=b.get("content") instanceof Map<?,?> m?(Map<String,Object>)m:null;
        if(data==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"报告内容必须是对象");
        int old=((Number)r.get("current_version_no")).intValue(),next=old+1;
        Map<String,Object> previous=mapper.convertValue(parse(content(id,r).get("content_json").toString()),new TypeReference<Map<String,Object>>(){});
        data=bindInspectionProfile(b,data,previous,((Number)r.get("work_package_id")).longValue(),r.get("report_type").toString(),a);
        insertVersion(id,next,data,actor(a),title);
        jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) SELECT a.report_id,?,a.file_id,? FROM erp_service_report_attachment a WHERE a.report_id=? AND a.version_no=? AND NOT EXISTS(SELECT 1 FROM erp_service_report_artifact x WHERE x.report_id=a.report_id AND x.file_id=a.file_id)",next,actor(a),id,old);
        if(Set.of("returned","withdrawn").contains(r.get("status"))){jdbc.update("UPDATE erp_service_report SET status='draft',current_version_no=?,return_reason=NULL,reviewer_id=NULL WHERE id=?",next,id);event(id,r.get("status").toString(),"draft",actor(a),"重新编制报告");}
        else jdbc.update("UPDATE erp_service_report SET current_version_no=? WHERE id=?",next,id);
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('version',?))",actor(a),"report.version.updated","erp_service_report",Long.toString(id),UUID.randomUUID().toString(),next);
        return detail(id);
    }
    @PostMapping("/reports/{id}/attachments") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> attach(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);if(!"draft".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"仅草稿可添加附件");long fileId=Long.parseLong(req(b,"fileId"));
        Map<String,Object> f=one("SELECT id,uploaded_by FROM erp_file_object WHERE id=?",fileId);
        if(!(f.get("uploaded_by") instanceof Number owner)||owner.longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的文件");
        int v=((Number)r.get("current_version_no")).intValue();
        jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) VALUES(?,?,?,?)",id,v,fileId,actor(a));
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('fileId',?))",actor(a),"report.attachment.added","erp_service_report",Long.toString(id),UUID.randomUUID().toString(),fileId);
        return detail(id);
    }
    @PostMapping("/reports/{id}/submit") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> submit(@PathVariable long id,@RequestBody(required=false) Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);if(!"draft".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿报告可以提交");
        Map<String,Object> v=content(id,r);Map<String,Object> data=mapper.convertValue(parse(v.get("content_json").toString()),new TypeReference<Map<String,Object>>(){});
        checkActionVersion(b,r);checkContent(data);
        if("inspection".equals(r.get("report_type"))){
            var snapshot=mapper.valueToTree(data).path("inspectionProfile").path("content");
            Set<String> basis=new HashSet<>();for(var ref:snapshot.path("basisReferences"))basis.add(ref.path("id").asText());
            for(var row:snapshot.path("records"))if(!row.path("templateItemId").asText("").isBlank()){
                if(row.path("location").asText("").isBlank()||row.path("result").asText("").isBlank()||row.path("judgment").asText("").isBlank())
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"模板检测项目尚未填写检测部位、结果或人工判定："+row.path("item").asText());
                if(!basis.contains(row.path("standardId").asText()))throw new ResponseStatusException(HttpStatus.CONFLICT,"模板检测项目规范尚未选为检测依据："+row.path("clauseReference").asText());
            }
        }
        var artifacts=jdbc.queryForList("SELECT id FROM erp_service_report_artifact WHERE report_id=? AND version_no=? AND artifact_type='docx' ORDER BY artifact_revision_no DESC",id,r.get("current_version_no"));
        Long selected=null;
        if(b!=null&&b.get("artifactId")!=null){
            try{selected=Long.parseLong(b.get("artifactId").toString());}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件 ID 无效");}
            one("SELECT id FROM erp_service_report_artifact WHERE id=? AND report_id=? AND version_no=? AND artifact_type='docx'",selected,id,r.get("current_version_no"));
        }else if(artifacts.size()==1)selected=((Number)artifacts.get(0).get("id")).longValue();
        else if(artifacts.size()>1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"有多个生成文件，请明确选择本次送审的 DOCX");
        if("inspection".equals(r.get("report_type"))&&selected==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"消防检测报告必须先生成或登记本版本的 DOCX 文件");
        jdbc.update("UPDATE erp_service_report_version SET submitted_artifact_id=?,submitted_by=? WHERE report_id=? AND version_no=?",selected,actor(a),id,r.get("current_version_no"));
        jdbc.update("UPDATE erp_service_report SET status='submitted',reviewer_id=NULL,return_reason=NULL WHERE id=?",id);
        event(id,"draft","submitted",actor(a),"提交审核");return detail(id);
    }
    @PostMapping("/reports/{id}/start-review") @PreAuthorize("hasAuthority('report:review')") @Transactional
    public Map<String,Object> startReview(@PathVariable long id,@RequestBody(required=false) Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);checkActionVersion(b,r);if(!"submitted".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已提交报告可以开始审核");
        requireEngineerReviewer(actor(a));
        requireNotContributor(id,r,actor(a));
        jdbc.update("UPDATE erp_service_report SET status='reviewing',reviewer_id=? WHERE id=?",actor(a),id);event(id,"submitted","reviewing",actor(a),"开始审核");return detail(id);
    }
    @PostMapping("/reports/{id}/return") @PreAuthorize("hasAuthority('report:review')") @Transactional
    public Map<String,Object> returnReport(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);checkActionVersion(b,r);if(!"reviewing".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有审核中的报告可以退回");
        if(((Number)r.get("reviewer_id")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有当前审核人可以退回");
        String reason=req(b,"reason");jdbc.update("UPDATE erp_service_report SET status='returned',return_reason=? WHERE id=?",reason,id);event(id,"reviewing","returned",actor(a),reason);return detail(id);
    }
    @PostMapping("/reports/{id}/approve") @PreAuthorize("hasAuthority('report:review')") @Transactional
    public Map<String,Object> approve(@PathVariable long id,@RequestBody(required=false) Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);checkActionVersion(b,r);if(!"reviewing".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有审核中的报告可以通过");
        if(((Number)r.get("reviewer_id")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有当前审核人可以审核");
        requireEngineerReviewer(actor(a));requireNotContributor(id,r,actor(a));
        jdbc.update("UPDATE erp_service_report_version SET reviewed_by=? WHERE report_id=? AND version_no=?",actor(a),id,r.get("current_version_no"));
        jdbc.update("UPDATE erp_service_report SET status='approved' WHERE id=?",id);event(id,"reviewing","approved",actor(a),"审核通过");return detail(id);
    }
    @PostMapping("/reports/{id}/issue") @PreAuthorize("hasAuthority('report:issue')") @Transactional
    public Map<String,Object> issue(@PathVariable long id,@RequestBody(required=false) Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);checkActionVersion(b,r);if(!"approved".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有审核通过的报告可以签发");
        requireNotContributor(id,r,actor(a));
        var v=content(id,r);
        if(v.get("reviewed_by")==null||v.get("submitted_by")==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"缺少本版本审核证据，请重新编制送审");
        if(((Number)v.get("reviewed_by")).longValue()==actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"签发人必须独立于审核人");
        jdbc.update("UPDATE erp_service_report_version SET issued_by=?,version_issued_at=CURRENT_TIMESTAMP(3) WHERE report_id=? AND version_no=?",actor(a),id,r.get("current_version_no"));
        jdbc.update("UPDATE erp_service_report SET status='issued',issuer_id=?,issued_at=CURRENT_TIMESTAMP(3) WHERE id=?",actor(a),id);event(id,"approved","issued",actor(a),"报告签发");return detail(id);
    }
    @PostMapping("/reports/{id}/archive") @PreAuthorize("hasAuthority('report:issue')") @Transactional
    public Map<String,Object> archive(@PathVariable long id,@RequestBody(required=false) Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);checkActionVersion(b,r);if(!"issued".equals(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已签发报告可以归档");
        Map<String,Object> v=content(id,r);
        List<Map<String,Object>> files=jdbc.queryForList("SELECT f.id,f.original_name,f.media_type,f.byte_size file_size,f.sha256 FROM erp_service_report_attachment a JOIN erp_file_object f ON f.id=a.file_id WHERE a.report_id=? AND a.version_no=? ORDER BY f.id",id,r.get("current_version_no"));
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("reportId",id);manifest.put("reportNo",r.get("report_no"));manifest.put("revisionNo",r.get("revision_no"));manifest.put("versionNo",r.get("current_version_no"));manifest.put("contentSha256",v.get("content_sha256"));manifest.put("attachments",files);manifest.put("issuedAt",r.get("issued_at"));manifest.put("title",v.get("title_snapshot"));manifest.put("issuedArtifactId",v.get("submitted_artifact_id"));manifest.put("submittedBy",v.get("submitted_by"));manifest.put("reviewedBy",v.get("reviewed_by"));manifest.put("issuedBy",v.get("issued_by"));
        jdbc.update("UPDATE erp_service_report_version SET archive_manifest=? WHERE report_id=? AND version_no=?",json(manifest),id,r.get("current_version_no"));
        jdbc.update("UPDATE erp_service_report SET status='archived',archived_at=CURRENT_TIMESTAMP(3),archive_manifest=? WHERE id=?",json(manifest),id);
        event(id,"issued","archived",actor(a),"归档快照已固定");return detail(id);
    }
    @PostMapping("/reports/{id}/withdraw") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> withdraw(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);checkActionVersion(b,r);if(!Set.of("submitted","reviewing","approved").contains(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前状态不可撤回");
        if(versionAuthor(id,r)!=actor(a)&&!(content(id,r).get("submitted_by") instanceof Number submitter&&submitter.longValue()==actor(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有本版本编制人或提交人可以撤回");
        String reason=req(b,"reason");String old=r.get("status").toString();jdbc.update("UPDATE erp_service_report SET status='withdrawn' WHERE id=?",id);event(id,old,"withdrawn",actor(a),reason);return detail(id);
    }
    @PostMapping("/reports/{id}/revise") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> revise(@PathVariable long id,@RequestBody(required=false) Map<String,Object>b,Authentication a){
        Map<String,Object> r=report(id);checkActionVersion(b,r);if(!Set.of("issued","archived").contains(r.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已签发或已归档报告可以创建修订版");
        int old=((Number)r.get("current_version_no")).intValue(),next=old+1;
        Map<String,Object> previous=content(id,r);Map<String,Object> data=mapper.convertValue(parse(previous.get("content_json").toString()),new TypeReference<Map<String,Object>>(){});
        insertVersion(id,next,data,actor(a),r.get("title").toString());
        jdbc.update("INSERT INTO erp_service_report_attachment(report_id,version_no,file_id,created_by) SELECT a.report_id,?,a.file_id,? FROM erp_service_report_attachment a WHERE a.report_id=? AND a.version_no=? AND NOT EXISTS(SELECT 1 FROM erp_service_report_artifact x WHERE x.report_id=a.report_id AND x.file_id=a.file_id)",next,actor(a),id,old);
        jdbc.update("UPDATE erp_service_report SET status='draft',revision_no=revision_no+1,current_version_no=?,reviewer_id=NULL,issuer_id=NULL,return_reason=NULL,issued_at=NULL,archived_at=NULL,archive_manifest=NULL WHERE id=?",next,id);
        event(id,r.get("status").toString(),"draft",actor(a),"创建新修订版");return detail(id);
    }
}
