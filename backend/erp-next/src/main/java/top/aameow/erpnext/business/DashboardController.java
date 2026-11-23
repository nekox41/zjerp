package top.aameow.erpnext.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/business/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;
    public DashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Map<String,Object> summary(String sql,Object...args){return jdbc.queryForMap(sql,args);}
    @GetMapping @PreAuthorize("hasAuthority('project:read')")
    public Map<String,Object> dashboard(@RequestParam(required=false) Long customerId,@RequestParam(required=false) Long projectId,Authentication auth){
        Map<String,Object> result=new LinkedHashMap<>();
        String contractFilter=" WHERE (? IS NULL OR customer_id=?)";
        result.put("contracts",summary("SELECT COUNT(*) count,COALESCE(SUM(amount),0) total_amount FROM erp_contract"+contractFilter,customerId,customerId));
        result.put("projects",summary("SELECT COUNT(*) count,COALESCE(AVG(progress),0) avg_progress, SUM(CASE WHEN status='in_progress' THEN 1 ELSE 0 END) active_count FROM erp_project WHERE (? IS NULL OR customer_id=?) AND (? IS NULL OR id=?)",customerId,customerId,projectId,projectId));
        if(auth.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("invoice:read")))result.put("invoices",jdbc.queryForList("""
          SELECT direction,currency,COUNT(*) invoice_count,COALESCE(SUM(amount),0) total_amount,
                 COALESCE(SUM(settled_amount),0) settled_amount,
                 COALESCE(SUM(amount-settled_amount),0) outstanding_amount,
                 SUM(CASE WHEN due_on<CURRENT_DATE() AND status IN ('open','partial') THEN 1 ELSE 0 END) overdue_count
          FROM erp_invoice WHERE status IN ('open','partial','settled')
          AND (? IS NULL OR customer_id=?) AND (? IS NULL OR project_id=?)
          GROUP BY direction,currency ORDER BY direction,currency
          """,customerId,customerId,projectId,projectId));
        if(auth.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("expense:approve")||x.getAuthority().equals("expense:pay")))result.put("expenses",jdbc.queryForList("""
          SELECT currency,COUNT(*) claim_count,COALESCE(SUM(total_amount),0) total_amount,
                 COALESCE(SUM(CASE WHEN status='paid' THEN paid_amount ELSE 0 END),0) paid_amount
          FROM erp_expense_claim WHERE status IN ('approved','paid')
          AND (? IS NULL OR project_id=?) GROUP BY currency
          """,projectId,projectId));
        result.put("projectsByStatus",jdbc.queryForList("SELECT status,COUNT(*) count FROM erp_project WHERE (? IS NULL OR customer_id=?) AND (? IS NULL OR id=?) GROUP BY status ORDER BY status",customerId,customerId,projectId,projectId));
        return result;
    }
}
