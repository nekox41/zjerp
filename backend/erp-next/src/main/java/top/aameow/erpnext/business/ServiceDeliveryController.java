package top.aameow.erpnext.business;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/business")
public class ServiceDeliveryController {
    private final JdbcTemplate jdbc;
    public ServiceDeliveryController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private long actor(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); }
    private String req(Map<String,Object>b,String k) {
        Object v=b.get(k); if(v==null||v.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空"); return v.toString().trim();
    }
    private String val(Map<String,Object>b,String k,String d) { Object v=b.get(k); return v==null?d:v.toString(); }
    private Long opt(Map<String,Object>b,String k) { Object v=b.get(k); return v==null||v.toString().isBlank()?null:Long.valueOf(v.toString()); }
    private Map<String,Object> one(String sql,Object...args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在"); return rows.get(0);
    }
    private void audit(Authentication a,String action,String type,long id,String summary) {
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",actor(a),action,type,Long.toString(id),UUID.randomUUID().toString(),summary);
    }

    @GetMapping("/employees/{id}/qualifications") @PreAuthorize("hasAuthority('employee:read')")
    public List<Map<String,Object>> qualifications(@PathVariable long id) {
        one("SELECT id FROM erp_employee WHERE id=?",id);
        return jdbc.queryForList("SELECT *,CASE WHEN expires_on<CURRENT_DATE THEN 'expired' WHEN expires_on<=DATE_ADD(CURRENT_DATE,INTERVAL 60 DAY) THEN 'expiring' ELSE 'valid' END effective_status FROM erp_employee_qualification WHERE employee_id=? ORDER BY expires_on",id);
    }
    @PostMapping("/employees/{id}/qualifications") @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> addQualification(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_employee WHERE id=?",id);
        String type=val(b,"qualificationType","other");
        if(!Set.of("engineer","other").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"资格类别无效");
        jdbc.update("INSERT INTO erp_employee_qualification(employee_id,qualification_name,qualification_type,certificate_no,issuing_authority,issued_on,expires_on,notes) VALUES(?,?,?,?,?,?,?,?)",
            id,req(b,"qualificationName"),type,b.get("certificateNo"),b.get("issuingAuthority"),b.get("issuedOn"),b.get("expiresOn"),b.get("notes"));
        long qid=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"employee.qualification.created","erp_employee_qualification",qid,req(b,"qualificationName"));
        return one("SELECT * FROM erp_employee_qualification WHERE id=?",qid);
    }
    @PutMapping("/qualifications/{id}") @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> updateQualification(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_employee_qualification WHERE id=?",id);
        String type=b.get("qualificationType")==null?null:b.get("qualificationType").toString();
        String status=b.get("status")==null?null:b.get("status").toString();
        if(type!=null&&!Set.of("engineer","other").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"资格类别无效");
        if(status!=null&&!Set.of("valid","expired","revoked").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"资格状态无效");
        jdbc.update("UPDATE erp_employee_qualification SET qualification_name=COALESCE(?,qualification_name),qualification_type=COALESCE(?,qualification_type),certificate_no=COALESCE(?,certificate_no),issuing_authority=COALESCE(?,issuing_authority),issued_on=COALESCE(?,issued_on),expires_on=COALESCE(?,expires_on),status=COALESCE(?,status),notes=COALESCE(?,notes) WHERE id=?",
            b.get("qualificationName"),type,b.get("certificateNo"),b.get("issuingAuthority"),b.get("issuedOn"),b.get("expiresOn"),status,b.get("notes"),id);
        audit(a,"employee.qualification.updated","erp_employee_qualification",id,"更新员工资格");
        return one("SELECT * FROM erp_employee_qualification WHERE id=?",id);
    }

    @GetMapping("/qualifications/expiring") @PreAuthorize("hasAuthority('employee:read')")
    public List<Map<String,Object>> expiringQualifications(@RequestParam(defaultValue="60") int days) {
        int bounded=Math.max(0,Math.min(days,365));
        return jdbc.queryForList("SELECT q.*,e.full_name,e.employee_code FROM erp_employee_qualification q JOIN erp_employee e ON e.id=q.employee_id WHERE q.expires_on IS NOT NULL AND q.expires_on<=DATE_ADD(CURRENT_DATE,INTERVAL ? DAY) AND q.status='valid' ORDER BY q.expires_on",bounded);
    }

    @PostMapping("/contracts/{id}/amendments") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> addAmendment(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> ct=one("SELECT id,amount,status FROM erp_contract WHERE id=?",id);
        if(!Set.of("active","completed").contains(ct.get("status").toString())) throw new ResponseStatusException(HttpStatus.CONFLICT,"仅履行中或已完成合同允许登记变更");
        BigDecimal previous=(BigDecimal)ct.get("amount"), revised=new BigDecimal(req(b,"revisedAmount"));
        if(revised.signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"变更金额不能为负数");
        String no=req(b,"amendmentNo"),title=req(b,"title"),summary=req(b,"changeSummary");
        jdbc.update("INSERT INTO erp_contract_amendment(contract_id,amendment_no,title,change_summary,previous_amount,revised_amount,effective_on,created_by) VALUES(?,?,?,?,?,?,?,?)",id,no,title,summary,previous,revised,b.get("effectiveOn"),actor(a));
        long aid=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"contract.amendment.created","erp_contract_amendment",aid,no);
        return one("SELECT * FROM erp_contract_amendment WHERE id=?",aid);
    }
    @GetMapping("/contracts/{id}/amendments") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> amendments(@PathVariable long id) {
        one("SELECT id FROM erp_contract WHERE id=?",id); return jdbc.queryForList("SELECT * FROM erp_contract_amendment WHERE contract_id=? ORDER BY id DESC",id);
    }
    @PutMapping("/amendments/{id}/approve") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> approveAmendment(@PathVariable long id,Authentication a) {
        Map<String,Object> x=one("SELECT * FROM erp_contract_amendment WHERE id=?",id);
        if(!"draft".equals(x.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿变更可以审批");
        Map<String,Object> current=one("SELECT amount,status FROM erp_contract WHERE id=?",x.get("contract_id"));
        if(!Set.of("active","completed").contains(current.get("status").toString()) || ((BigDecimal)current.get("amount")).compareTo((BigDecimal)x.get("previous_amount"))!=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"合同已变化，请重新登记变更");
        jdbc.update("UPDATE erp_contract_amendment SET status='approved' WHERE id=?",id);
        jdbc.update("UPDATE erp_contract SET amount=? WHERE id=?",x.get("revised_amount"),x.get("contract_id"));
        audit(a,"contract.amendment.approved","erp_contract_amendment",id,x.get("amendment_no").toString());
        return one("SELECT * FROM erp_contract_amendment WHERE id=?",id);
    }

    @GetMapping("/projects/{id}/issues") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> issues(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("SELECT i.*,e.full_name owner_name FROM erp_project_issue i LEFT JOIN erp_employee e ON e.id=i.owner_employee_id WHERE i.project_id=? ORDER BY FIELD(i.status,'open','in_progress','resolved','closed'),i.due_on",id);
    }
    @PostMapping("/projects/{id}/issues") @PreAuthorize("hasAuthority('project_issue:write')") @Transactional
    public Map<String,Object> createIssue(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        Long owner=opt(b,"ownerEmployeeId"),visit=opt(b,"sourceSiteVisitId");
        if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        if(visit!=null) {
            Map<String,Object> v=one("SELECT project_id FROM erp_site_visit WHERE id=?",visit);
            if(((Number)v.get("project_id")).longValue()!=id) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"问题来源现场服务不属于当前项目");
        }
        String title=req(b,"title"),severity=val(b,"severity","medium");
        if(!Set.of("low","medium","high","critical").contains(severity)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效严重级别");
        jdbc.update("INSERT INTO erp_project_issue(project_id,source_site_visit_id,title,description,severity,owner_employee_id,due_on,reported_to_customer_on) VALUES(?,?,?,?,?,?,?,?)",
            id,visit,title,b.get("description"),severity,owner,b.get("dueOn"),b.get("reportedToCustomerOn"));
        long issueId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO erp_project_issue_event(issue_id,from_status,to_status,content,actor_identity_id) VALUES(?,NULL,'open',?,?)",
            issueId,val(b,"initialNote",title),actor(a));
        audit(a,"project.issue.created","erp_project_issue",issueId,title);
        return one("SELECT * FROM erp_project_issue WHERE id=?",issueId);
    }

    @PutMapping("/issues/{id}") @PreAuthorize("hasAuthority('project_issue:write')") @Transactional
    public Map<String,Object> updateIssue(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project_issue WHERE id=?",id);
        Long owner=opt(b,"ownerEmployeeId");
        if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        jdbc.update("UPDATE erp_project_issue SET title=COALESCE(?,title),description=COALESCE(?,description),severity=COALESCE(?,severity),owner_employee_id=COALESCE(?,owner_employee_id),due_on=COALESCE(?,due_on),reported_to_customer_on=COALESCE(?,reported_to_customer_on),rectification_note=COALESCE(?,rectification_note) WHERE id=?",
            b.get("title"),b.get("description"),b.get("severity"),owner,b.get("dueOn"),b.get("reportedToCustomerOn"),b.get("rectificationNote"),id);
        audit(a,"project.issue.updated","erp_project_issue",id,"问题整改资料更新");
        return one("SELECT * FROM erp_project_issue WHERE id=?",id);
    }

    @PostMapping("/issues/{id}/transition") @PreAuthorize("hasAuthority('project_issue:write')") @Transactional
    public Map<String,Object> transitionIssue(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> issue=one("SELECT * FROM erp_project_issue WHERE id=? FOR UPDATE",id);
        String from=issue.get("status").toString(),to=req(b,"toStatus"),note=req(b,"note");
        boolean allowed=(from.equals("open")&&to.equals("in_progress"))||
            (from.equals("in_progress")&&to.equals("resolved"))||
            (from.equals("resolved")&&to.equals("closed"))||
            (from.equals("closed")&&to.equals("open"));
        if(!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT,"当前整改状态不能流转到目标状态");
        Long visit=opt(b,"siteVisitId");
        if("resolved".equals(to)&&note.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"登记整改完成时必须填写整改结果");
        if("closed".equals(to)) {
            if(visit==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"复核关闭必须关联一次现场复核记录");
            Map<String,Object> v=one("SELECT project_id FROM erp_site_visit WHERE id=?",visit);
            if(((Number)v.get("project_id")).longValue()!=((Number)issue.get("project_id")).longValue())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"复核现场记录不属于当前项目");
            if(!"completed".equals(one("SELECT status FROM erp_site_visit WHERE id=?",visit).get("status").toString()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"现场复核记录尚未完成");
        }
        if("resolved".equals(to)) jdbc.update("UPDATE erp_project_issue SET status=?,rectification_note=?,resolved_at=CURRENT_TIMESTAMP(3) WHERE id=?",to,note,id);
        else if("closed".equals(to)) jdbc.update("UPDATE erp_project_issue SET status=?,verified_at=CURRENT_TIMESTAMP(3),verified_by=?,closed_at=CURRENT_TIMESTAMP(3) WHERE id=?",to,actor(a),id);
        else if("open".equals(to)) jdbc.update("UPDATE erp_project_issue SET status='open',verified_at=NULL,verified_by=NULL,closed_at=NULL WHERE id=?",id);
        else jdbc.update("UPDATE erp_project_issue SET status=? WHERE id=?",to,id);
        jdbc.update("INSERT INTO erp_project_issue_event(issue_id,from_status,to_status,content,actor_identity_id) VALUES(?,?,?,?,?)",id,from,to,note,actor(a));
        audit(a,"project.issue."+to,"erp_project_issue",id,note);
        return one("SELECT * FROM erp_project_issue WHERE id=?",id);
    }

    @GetMapping("/issues/{id}/events") @PreAuthorize("hasAuthority('project_issue:read')")
    public List<Map<String,Object>> issueEvents(@PathVariable long id) {
        one("SELECT id FROM erp_project_issue WHERE id=?",id);
        return jdbc.queryForList("SELECT e.*,i.display_name actor_name FROM erp_project_issue_event e JOIN erp_identity i ON i.id=e.actor_identity_id WHERE e.issue_id=? ORDER BY e.id",id);
    }

    @GetMapping("/projects/{id}/work-packages") @PreAuthorize("hasAuthority('service:read')")
    public List<Map<String,Object>> workPackages(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("SELECT w.*,e.full_name assignee_name FROM erp_service_work_package w LEFT JOIN erp_employee e ON e.id=w.assignee_employee_id WHERE w.project_id=? ORDER BY w.id DESC",id);
    }
    @PostMapping("/projects/{id}/work-packages") @PreAuthorize("hasAuthority('service:write')") @Transactional
    public Map<String,Object> createWorkPackage(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id); Long assignee=opt(b,"assigneeEmployeeId"); if(assignee!=null) one("SELECT id FROM erp_employee WHERE id=?",assignee);
        String no=req(b,"packageNo"),title=req(b,"title");
        jdbc.update("INSERT INTO erp_service_work_package(project_id,package_no,title,description,assignee_employee_id,planned_start,planned_end) VALUES(?,?,?,?,?,?,?)",id,no,title,b.get("description"),assignee,b.get("plannedStart"),b.get("plannedEnd"));
        long wid=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"service.work_package.created","erp_service_work_package",wid,no+" "+title);
        return one("SELECT * FROM erp_service_work_package WHERE id=?",wid);
    }
    @PutMapping("/work-packages/{id}") @PreAuthorize("hasAuthority('service:write')") @Transactional
    public Map<String,Object> updateWorkPackage(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_service_work_package WHERE id=?",id); String status=b.get("status")==null?null:b.get("status").toString();
        if(status!=null&&!Set.of("planned","in_progress","blocked","completed","cancelled").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效工作包状态");
        BigDecimal progress=b.get("progress")==null?null:new BigDecimal(b.get("progress").toString());
        if(progress!=null&&(progress.signum()<0||progress.compareTo(BigDecimal.valueOf(100))>0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"进度必须在 0 到 100 之间");
        jdbc.update("UPDATE erp_service_work_package SET title=COALESCE(?,title),description=COALESCE(?,description),assignee_employee_id=COALESCE(?,assignee_employee_id),planned_start=COALESCE(?,planned_start),planned_end=COALESCE(?,planned_end),status=COALESCE(?,status),progress=COALESCE(?,progress) WHERE id=?",b.get("title"),b.get("description"),opt(b,"assigneeEmployeeId"),b.get("plannedStart"),b.get("plannedEnd"),status,progress,id);
        audit(a,"service.work_package.updated","erp_service_work_package",id,status); return one("SELECT * FROM erp_service_work_package WHERE id=?",id);
    }
    @GetMapping("/work-packages/{id}/records") @PreAuthorize("hasAuthority('service:read')")
    public List<Map<String,Object>> records(@PathVariable long id) {
        one("SELECT id FROM erp_service_work_package WHERE id=?",id);
        return jdbc.queryForList("SELECT r.*,i.display_name recorder_name FROM erp_service_record r LEFT JOIN erp_identity i ON i.id=r.recorded_by WHERE r.work_package_id=? ORDER BY r.performed_on DESC",id);
    }
    @PostMapping("/work-packages/{id}/records") @PreAuthorize("hasAuthority('service:write')") @Transactional
    public Map<String,Object> addRecord(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_service_work_package WHERE id=?",id); String title=req(b,"title"),content=req(b,"content");
        jdbc.update("INSERT INTO erp_service_record(work_package_id,record_type,title,content,performed_on,recorded_by) VALUES(?,?,?,?,COALESCE(?,CURRENT_TIMESTAMP(3)),?)",id,val(b,"recordType","field"),title,content,b.get("performedOn"),actor(a));
        long rid=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"service.record.created","erp_service_record",rid,title);
        return one("SELECT * FROM erp_service_record WHERE id=?",rid);
    }
}
