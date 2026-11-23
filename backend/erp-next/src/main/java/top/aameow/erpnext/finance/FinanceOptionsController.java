package top.aameow.erpnext.finance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/finance/options")
public class FinanceOptionsController {
    private final JdbcTemplate jdbc;
    public FinanceOptionsController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping @PreAuthorize("hasAnyAuthority('invoice:write','expense:write','project:read')")
    public Map<String,Object> options(){
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("customers",jdbc.queryForList("SELECT id,customer_code,name FROM erp_customer WHERE status='active' ORDER BY name LIMIT 1000"));
        out.put("contracts",jdbc.queryForList("SELECT id,contract_no,title,customer_id FROM erp_contract WHERE status IN ('draft','pending','active') ORDER BY id DESC LIMIT 1000"));
        out.put("projects",jdbc.queryForList("SELECT id,project_no,name,customer_id,contract_id FROM erp_project WHERE status NOT IN ('cancelled') ORDER BY id DESC LIMIT 1000"));
        return out;
    }
}
