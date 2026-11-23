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
public class ReportDeliveryController {
    private final JdbcTemplate jdbc;
    public ReportDeliveryController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private long actor(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); }
    private String req(Map<String,Object>b,String k) {
        Object v=b.get(k); if(v==null||v.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空");
        return v.toString().trim();
    }
    private Map<String,Object> one(String sql,Object...args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.get(0);
    }
    private void audit(Authentication a,String action,long id,String summary) {
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor(a),action,"erp_report_delivery",Long.toString(id),UUID.randomUUID().toString(),summary);
    }

    @GetMapping("/reports/{reportId}/deliveries") @PreAuthorize("hasAuthority('report:read')")
    public List<Map<String,Object>> deliveries(@PathVariable long reportId) {
        one("SELECT id FROM erp_service_report WHERE id=?",reportId);
        return jdbc.queryForList("""
          SELECT d.*,i.display_name created_by_name
          FROM erp_report_delivery d LEFT JOIN erp_identity i ON i.id=d.created_by
          WHERE d.report_id=? ORDER BY d.version_no DESC,d.created_at DESC
          """,reportId);
    }

    @PostMapping("/reports/{reportId}/deliveries") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> create(@PathVariable long reportId,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> report=one("SELECT id,status,current_version_no FROM erp_service_report WHERE id=?",reportId);
        if(!Set.of("issued","archived").contains(report.get("status").toString()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"报告签发后才能登记邮寄交付");
        int version=Integer.parseInt(report.get("current_version_no").toString());
        jdbc.update("""
          INSERT INTO erp_report_delivery(report_id,version_no,recipient_name,contact_name,contact_phone,delivery_address,
            courier_name,tracking_no,sent_at,delivery_status,delivered_at,notes,created_by)
          VALUES(?,?,?,?,?,?,?,?,?, ?,?,?,?)
          """,reportId,version,req(b,"recipientName"),b.get("contactName"),b.get("contactPhone"),req(b,"deliveryAddress"),
            b.get("courierName"),b.get("trackingNo"),b.get("sentAt"),"prepared",null,b.get("notes"),actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"report.delivery.created",id,"登记报告邮寄："+req(b,"recipientName"));
        return one("SELECT * FROM erp_report_delivery WHERE id=?",id);
    }

    @PutMapping("/deliveries/{id}") @PreAuthorize("hasAuthority('report:write')") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> current=one("SELECT * FROM erp_report_delivery WHERE id=?",id);
        String status=b.get("deliveryStatus")==null?current.get("delivery_status").toString():b.get("deliveryStatus").toString();
        if(!Set.of("prepared","sent","delivered","returned","cancelled").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"邮寄状态无效");
        if("sent".equals(status)&&b.get("sentAt")==null&&current.get("sent_at")==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"登记已寄出时必须填写寄出时间");
        if("delivered".equals(status)&&b.get("deliveredAt")==null&&current.get("delivered_at")==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"登记已签收时必须填写签收时间");
        jdbc.update("""
          UPDATE erp_report_delivery SET recipient_name=COALESCE(?,recipient_name),contact_name=?,contact_phone=?,
            delivery_address=COALESCE(?,delivery_address),courier_name=?,tracking_no=?,sent_at=COALESCE(?,sent_at),
            delivery_status=?,delivered_at=COALESCE(?,delivered_at),notes=? WHERE id=?
          """,b.get("recipientName"),b.get("contactName"),b.get("contactPhone"),b.get("deliveryAddress"),
            b.get("courierName"),b.get("trackingNo"),b.get("sentAt"),status,b.get("deliveredAt"),b.get("notes"),id);
        audit(a,"report.delivery.updated",id,"邮寄状态更新为 "+status);
        return one("SELECT * FROM erp_report_delivery WHERE id=?",id);
    }
}
