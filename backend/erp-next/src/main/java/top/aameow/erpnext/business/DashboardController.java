package top.aameow.erpnext.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/business/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;
    public DashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Map<String,Object> summary(String sql,Object...args){return jdbc.queryForMap(sql,args);}

    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public Map<String,Object> dashboard() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("summary",summary("""
          SELECT
            (SELECT COUNT(*) FROM erp_customer WHERE status='active') customers,
            (SELECT COUNT(*) FROM erp_partner WHERE status='active') partners,
            (SELECT COUNT(*) FROM erp_employee WHERE status='active') employees,
            (SELECT COUNT(*) FROM erp_contract WHERE status IN ('pending','active')) active_contracts,
            (SELECT COUNT(*) FROM erp_project WHERE status NOT IN ('completed','cancelled')) active_projects,
            (SELECT COUNT(*) FROM erp_site_visit WHERE status='planned') planned_visits,
            (SELECT COUNT(*) FROM erp_project_issue WHERE status IN ('open','in_progress')) open_issues,
            (SELECT COUNT(*) FROM erp_service_report WHERE status IN ('submitted','reviewing')) reports_in_review,
            (SELECT COUNT(*) FROM erp_service_report WHERE status='returned') returned_reports,
            (SELECT COUNT(*) FROM erp_employee_qualification WHERE qualification_type='engineer' AND status='valid'
              AND expires_on IS NOT NULL AND expires_on<=DATE_ADD(CURRENT_DATE,INTERVAL 60 DAY)) expiring_qualifications,
            (SELECT COUNT(*) FROM erp_project WHERE maintenance_card_sent=FALSE) maintenance_cards_pending
          """));
        result.put("projectStatuses",jdbc.queryForList("SELECT status,COUNT(*) count FROM erp_project GROUP BY status ORDER BY status"));
        result.put("issueStatuses",jdbc.queryForList("SELECT status,COUNT(*) count FROM erp_project_issue GROUP BY status ORDER BY FIELD(status,'open','in_progress','resolved','closed')"));
        result.put("reportStatuses",jdbc.queryForList("SELECT status,COUNT(*) count FROM erp_service_report GROUP BY status ORDER BY status"));
        result.put("upcomingVisits",jdbc.queryForList("""
          SELECT v.id,v.visit_no,v.title,v.visit_type,v.performed_on,v.status,p.project_no,p.name project_name
          FROM erp_site_visit v JOIN erp_project p ON p.id=v.project_id
          WHERE v.status='planned' ORDER BY v.performed_on LIMIT 12
          """));
        result.put("pendingIssues",jdbc.queryForList("""
          SELECT i.id,i.title,i.severity,i.status,i.due_on,p.project_no,p.name project_name,e.full_name owner_name
          FROM erp_project_issue i JOIN erp_project p ON p.id=i.project_id
          LEFT JOIN erp_employee e ON e.id=i.owner_employee_id
          WHERE i.status IN ('open','in_progress','resolved')
          ORDER BY CASE i.severity WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END,
                   i.due_on LIMIT 12
          """));
        result.put("reportsInQueue",jdbc.queryForList("""
          SELECT r.id,r.report_no,r.title,r.status,r.updated_at,p.name project_name,c.name customer_name
          FROM erp_service_report r JOIN erp_project p ON p.id=r.project_id
          JOIN erp_customer c ON c.id=p.customer_id
          WHERE r.status IN ('submitted','reviewing','returned')
          ORDER BY r.updated_at DESC LIMIT 12
          """));
        result.put("maintenanceAttention",jdbc.queryForList("""
          SELECT p.id,p.project_no,p.name,c.name customer_name,ct.ends_on current_contract_end,
                 p.honeybee_uploaded,p.documents_ready,p.maintenance_card_sent
          FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id
          LEFT JOIN erp_contract ct ON ct.id=p.contract_id
          WHERE p.status NOT IN ('completed','cancelled')
            AND (p.honeybee_uploaded=FALSE OR p.documents_ready=FALSE OR p.maintenance_card_sent=FALSE
              OR ct.ends_on<=DATE_ADD(CURRENT_DATE,INTERVAL 60 DAY))
          ORDER BY ct.ends_on LIMIT 12
          """));
        return result;
    }
}
