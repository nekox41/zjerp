package top.aameow.erpnext.business;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;

import java.util.*;

@RestController
@RequestMapping("/api/business")
public class ServiceWorkflowController {
    private final JdbcTemplate jdbc;
    public ServiceWorkflowController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private long actor(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); }
    private String required(Map<String,Object>b,String k) {
        Object v=b.get(k);
        if(v==null||v.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空");
        return v.toString().trim();
    }
    private String value(Map<String,Object>b,String k,String fallback) {
        Object v=b.get(k); return v==null?fallback:v.toString();
    }
    private Long optionalLong(Map<String,Object>b,String k) {
        Object v=b.get(k); return v==null||v.toString().isBlank()?null:Long.valueOf(v.toString());
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
    private void assertOwnedFile(long fileId,long who) {
        Map<String,Object> f=one("SELECT uploaded_by FROM erp_file_object WHERE id=?",fileId);
        if(f.get("uploaded_by")==null||((Number)f.get("uploaded_by")).longValue()!=who)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的文件");
    }
    private Map<String,Object> visit(long id) {
        return one("SELECT * FROM erp_site_visit WHERE id=?",id);
    }
    private Map<String,Object> visitDetail(long id) {
        Map<String,Object> result=new LinkedHashMap<>(visit(id));
        result.put("participants",jdbc.queryForList("""
          SELECT e.id,e.employee_code,e.full_name,e.employee_type,ve.visit_role
          FROM erp_site_visit_employee ve JOIN erp_employee e ON e.id=ve.employee_id
          WHERE ve.site_visit_id=? ORDER BY e.full_name
          """,id));
        result.put("attachments",jdbc.queryForList("""
          SELECT a.id,a.file_id,a.label,a.created_at,f.original_name,f.media_type,f.byte_size file_size,f.sha256
          FROM erp_site_visit_attachment a JOIN erp_file_object f ON f.id=a.file_id
          WHERE a.site_visit_id=? ORDER BY a.id DESC
          """,id));
        result.put("issues",jdbc.queryForList("SELECT id,title,severity,status,due_on,resolution FROM erp_project_issue WHERE source_site_visit_id=? ORDER BY id",id));
        return result;
    }

    @GetMapping("/projects/{id}/site-visits")
    @PreAuthorize("hasAuthority('site_visit:read')")
    public List<Map<String,Object>> siteVisits(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT v.*,(SELECT COUNT(*) FROM erp_site_visit_employee ve WHERE ve.site_visit_id=v.id) participant_count,
                 (SELECT COUNT(*) FROM erp_project_issue i WHERE i.source_site_visit_id=v.id) issue_count
          FROM erp_site_visit v WHERE v.project_id=? ORDER BY v.performed_on DESC,v.id DESC
          """,id);
    }

    @PostMapping("/projects/{id}/site-visits")
    @PreAuthorize("hasAuthority('site_visit:write')")
    @Transactional
    public Map<String,Object> createSiteVisit(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        String type=value(b,"visitType","inspection");
        if(!Set.of("maintenance","inspection","rating","assessment","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场服务类型无效");
        String status=value(b,"status","completed");
        if(!Set.of("planned","completed","cancelled").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场服务状态无效");
        String title=required(b,"title"),performed=required(b,"performedOn");
        jdbc.update("""
          INSERT INTO erp_site_visit(project_id,visit_no,visit_type,title,performed_on,content,summary,status,created_by)
          VALUES(?,?,?,?,?,?,?,?,?)
          """,id,b.get("visitNo"),type,title,performed,b.get("content"),b.get("summary"),status,actor(a));
        long visitId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"site_visit.created","erp_site_visit",visitId,title);
        if(b.get("participants") instanceof List<?> participants) {
            for(Object item:participants) {
                if(!(item instanceof Map<?,?> row)) continue;
                long employeeId=Long.parseLong(Objects.toString(row.get("employeeId")));
                one("SELECT id FROM erp_employee WHERE id=? AND status='active'",employeeId);
                jdbc.update("INSERT INTO erp_site_visit_employee(site_visit_id,employee_id,visit_role) VALUES(?,?,?)",
                    visitId,employeeId,Objects.toString(row.get("role"),"现场人员"));
            }
        }
        return visitDetail(visitId);
    }

    @GetMapping("/site-visits/{id}")
    @PreAuthorize("hasAuthority('site_visit:read')")
    public Map<String,Object> getSiteVisit(@PathVariable long id) { return visitDetail(id); }

    @PutMapping("/site-visits/{id}")
    @PreAuthorize("hasAuthority('site_visit:write')")
    @Transactional
    public Map<String,Object> updateSiteVisit(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> old=visit(id);
        String type=b.get("visitType")==null?old.get("visit_type").toString():b.get("visitType").toString();
        String status=b.get("status")==null?old.get("status").toString():b.get("status").toString();
        if(!Set.of("maintenance","inspection","rating","assessment","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场服务类型无效");
        if(!Set.of("planned","completed","cancelled").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场服务状态无效");
        jdbc.update("""
          UPDATE erp_site_visit SET visit_no=?,visit_type=?,title=?,performed_on=?,content=?,summary=?,status=? WHERE id=?
          """,b.containsKey("visitNo")?b.get("visitNo"):old.get("visit_no"),type,b.getOrDefault("title",old.get("title")),
            b.getOrDefault("performedOn",old.get("performed_on")),b.getOrDefault("content",old.get("content")),
            b.getOrDefault("summary",old.get("summary")),status,id);
        audit(a,"site_visit.updated","erp_site_visit",id,"现场服务记录更新");
        return visitDetail(id);
    }

    @PostMapping("/site-visits/{id}/participants")
    @PreAuthorize("hasAuthority('site_visit:write')")
    @Transactional
    public Map<String,Object> addParticipant(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        visit(id);
        long employeeId=Long.parseLong(required(b,"employeeId"));
        one("SELECT id FROM erp_employee WHERE id=? AND status='active'",employeeId);
        jdbc.update("INSERT INTO erp_site_visit_employee(site_visit_id,employee_id,visit_role) VALUES(?,?,?) ON DUPLICATE KEY UPDATE visit_role=VALUES(visit_role)",
            id,employeeId,value(b,"role","现场人员"));
        audit(a,"site_visit.participant.added","erp_site_visit",id,"添加现场人员 "+employeeId);
        return visitDetail(id);
    }

    @DeleteMapping("/site-visits/{id}/participants/{employeeId}")
    @PreAuthorize("hasAuthority('site_visit:write')")
    @Transactional
    public Map<String,Object> removeParticipant(@PathVariable long id,@PathVariable long employeeId,Authentication a) {
        visit(id);
        int changed=jdbc.update("DELETE FROM erp_site_visit_employee WHERE site_visit_id=? AND employee_id=?",id,employeeId);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"现场服务人员不存在");
        audit(a,"site_visit.participant.removed","erp_site_visit",id,"移除现场人员 "+employeeId);
        return visitDetail(id);
    }

    @PostMapping("/site-visits/{id}/attachments")
    @PreAuthorize("hasAuthority('site_visit:write')")
    @Transactional
    public Map<String,Object> addVisitAttachment(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        visit(id);
        long fileId=Long.parseLong(required(b,"fileId"));
        assertOwnedFile(fileId,actor(a));
        jdbc.update("INSERT INTO erp_site_visit_attachment(site_visit_id,file_id,label,created_by) VALUES(?,?,?,?)",
            id,fileId,b.get("label"),actor(a));
        audit(a,"site_visit.attachment.added","erp_site_visit",id,"现场服务附件 "+fileId);
        return visitDetail(id);
    }

    @GetMapping("/contracts/{id}/documents")
    @PreAuthorize("hasAuthority('contract_document:read')")
    public List<Map<String,Object>> contractDocuments(@PathVariable long id) {
        one("SELECT id FROM erp_contract WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT d.id,d.contract_id,d.file_id,d.document_type,d.label,d.created_at,
                 f.original_name,f.media_type,f.byte_size file_size,f.sha256
          FROM erp_contract_document d JOIN erp_file_object f ON f.id=d.file_id
          WHERE d.contract_id=? ORDER BY d.id DESC
          """,id);
    }

    @PostMapping("/contracts/{id}/documents")
    @PreAuthorize("hasAuthority('contract_document:write')")
    @Transactional
    public Map<String,Object> addContractDocument(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_contract WHERE id=?",id);
        long fileId=Long.parseLong(required(b,"fileId"));
        assertOwnedFile(fileId,actor(a));
        String type=value(b,"documentType","other");
        if(!Set.of("contract","maintenance_card","project_material","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同资料类型无效");
        jdbc.update("INSERT INTO erp_contract_document(contract_id,file_id,document_type,label,created_by) VALUES(?,?,?,?,?)",
            id,fileId,type,b.get("label"),actor(a));
        long documentId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"contract.document.added","erp_contract_document",documentId,type);
        return one("""
          SELECT d.id,d.contract_id,d.file_id,d.document_type,d.label,d.created_at,
                 f.original_name,f.media_type,f.byte_size file_size,f.sha256
          FROM erp_contract_document d JOIN erp_file_object f ON f.id=d.file_id WHERE d.id=?
          """,documentId);
    }

    @GetMapping("/reports/{id}/site-visits")
    @PreAuthorize("hasAuthority('report:read')")
    public List<Map<String,Object>> reportVisits(@PathVariable long id) {
        one("SELECT id FROM erp_service_report WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT v.id,v.visit_no,v.visit_type,v.title,v.performed_on,v.status
          FROM erp_service_report_site_visit rv JOIN erp_site_visit v ON v.id=rv.site_visit_id
          WHERE rv.report_id=? ORDER BY v.performed_on
          """,id);
    }

    @PostMapping("/reports/{id}/site-visits")
    @PreAuthorize("hasAuthority('report:write')")
    @Transactional
    public List<Map<String,Object>> linkReportVisit(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> report=one("SELECT project_id,status FROM erp_service_report WHERE id=?",id);
        if(!Set.of("draft","returned").contains(report.get("status").toString()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿或退回报告可以关联现场记录");
        long visitId=Long.parseLong(required(b,"siteVisitId"));
        Map<String,Object> visit=one("SELECT project_id FROM erp_site_visit WHERE id=?",visitId);
        if(((Number)report.get("project_id")).longValue()!=((Number)visit.get("project_id")).longValue())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场服务记录与报告不属于同一项目");
        jdbc.update("INSERT IGNORE INTO erp_service_report_site_visit(report_id,site_visit_id) VALUES(?,?)",id,visitId);
        audit(a,"report.site_visit.linked","erp_service_report",id,"关联现场记录 "+visitId);
        return reportVisits(id);
    }

    @GetMapping("/reports/{id}/deliveries")
    @PreAuthorize("hasAuthority('report_delivery:read')")
    public List<Map<String,Object>> reportDeliveries(@PathVariable long id) {
        one("SELECT id FROM erp_service_report WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT d.*,e.display_name created_by_name
          FROM erp_service_report_delivery d JOIN erp_identity e ON e.id=d.created_by
          WHERE d.report_id=? ORDER BY d.id DESC
          """,id);
    }

    @PostMapping("/reports/{id}/deliveries")
    @PreAuthorize("hasAuthority('report_delivery:write')")
    @Transactional
    public Map<String,Object> createDelivery(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> report=one("SELECT status,current_version_no FROM erp_service_report WHERE id=?",id);
        if(!Set.of("issued","archived").contains(report.get("status").toString()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已签发或已归档的报告可以登记邮寄");
        int version=b.get("versionNo")==null?((Number)report.get("current_version_no")).intValue():Integer.parseInt(b.get("versionNo").toString());
        one("SELECT report_id FROM erp_service_report_version WHERE report_id=? AND version_no=?",id,version);
        String status=value(b,"deliveryStatus","pending");
        if(!Set.of("pending","sent","delivered","returned").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"邮寄状态无效");
        if("sent".equals(status)&&((b.get("trackingNo")==null||b.get("trackingNo").toString().isBlank())||
            (b.get("courierName")==null||b.get("courierName").toString().isBlank())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"发出时需要填写快递公司和单号");
        jdbc.update("""
          INSERT INTO erp_service_report_delivery(report_id,version_no,recipient_name,recipient_address,courier_name,
            tracking_no,sent_on,delivery_status,delivered_on,notes,created_by)
          VALUES(?,?,?,?,?,?,?,?,?,?,?)
          """,id,version,b.get("recipientName"),b.get("recipientAddress"),b.get("courierName"),b.get("trackingNo"),
            b.get("sentOn"),status,b.get("deliveredOn"),b.get("notes"),actor(a));
        long deliveryId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO erp_service_report_delivery_event(delivery_id,from_status,to_status,note,actor_identity_id) VALUES(?,NULL,?,?,?)",
            deliveryId,status,value(b,"notes","邮寄登记"),actor(a));
        audit(a,"report.delivery.created","erp_service_report_delivery",deliveryId,"报告邮寄登记");
        return one("SELECT * FROM erp_service_report_delivery WHERE id=?",deliveryId);
    }

    @PutMapping("/deliveries/{id}")
    @PreAuthorize("hasAuthority('report_delivery:write')")
    @Transactional
    public Map<String,Object> updateDelivery(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> old=one("SELECT * FROM erp_service_report_delivery WHERE id=? FOR UPDATE",id);
        String from=old.get("delivery_status").toString(),to=value(b,"deliveryStatus",from);
        if(!Set.of("pending","sent","delivered","returned").contains(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"邮寄状态无效");
        boolean allowed=(from.equals("pending")&&Set.of("sent","returned").contains(to))||
            (from.equals("sent")&&Set.of("delivered","returned").contains(to));
        if(!from.equals(to)&&!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT,"邮寄状态不能从 "+from+" 流转到 "+to);
        String courier=value(b,"courierName",Objects.toString(old.get("courier_name"),""));
        String tracking=value(b,"trackingNo",Objects.toString(old.get("tracking_no"),""));
        if("sent".equals(to)&&(courier.isBlank()||tracking.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"发出时需要填写快递公司和单号");
        jdbc.update("""
          UPDATE erp_service_report_delivery SET recipient_name=?,recipient_address=?,courier_name=?,tracking_no=?,
            sent_on=?,delivery_status=?,delivered_on=?,notes=? WHERE id=?
          """,b.getOrDefault("recipientName",old.get("recipient_name")),b.getOrDefault("recipientAddress",old.get("recipient_address")),
            courier,tracking,b.getOrDefault("sentOn",old.get("sent_on")),to,b.getOrDefault("deliveredOn",old.get("delivered_on")),
            b.getOrDefault("notes",old.get("notes")),id);
        if(!from.equals(to)) jdbc.update("INSERT INTO erp_service_report_delivery_event(delivery_id,from_status,to_status,note,actor_identity_id) VALUES(?,?,?,?,?)",
            id,from,to,b.get("notes"),actor(a));
        audit(a,"report.delivery.updated","erp_service_report_delivery",id,"邮寄状态更新 "+from+" → "+to);
        return one("SELECT * FROM erp_service_report_delivery WHERE id=?",id);
    }

    @GetMapping("/deliveries/{id}/events")
    @PreAuthorize("hasAuthority('report_delivery:read')")
    public List<Map<String,Object>> deliveryEvents(@PathVariable long id) {
        one("SELECT id FROM erp_service_report_delivery WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT e.*,i.display_name actor_name FROM erp_service_report_delivery_event e
          JOIN erp_identity i ON i.id=e.actor_identity_id WHERE e.delivery_id=? ORDER BY e.id
          """,id);
    }
}
