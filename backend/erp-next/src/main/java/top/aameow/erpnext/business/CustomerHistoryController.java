package top.aameow.erpnext.business;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/business/customers/{customerId}")
public class CustomerHistoryController {
    private final JdbcTemplate jdbc;
    public CustomerHistoryController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    private Map<String,Object> one(String sql,Object...args){
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.get(0);
    }
    private void require(Authentication a,String permission){
        if(a.getAuthorities().stream().noneMatch(x->permission.equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"没有查看或维护此类业务记录的权限");
    }

    @GetMapping("/history/{kind}") @PreAuthorize("hasAuthority('customer:read')")
    public Map<String,Object> history(@PathVariable long customerId,@PathVariable String kind,
        @RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset,Authentication a){
        one("SELECT id FROM erp_customer WHERE id=?",customerId);
        if(limit<1||limit>200||offset<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数无效");
        String permission,sql;
        switch(kind){
            case "contracts" -> {
                permission="contract:read";
                sql="""
                    SELECT c.id,c.contract_no,c.title,c.service_type,c.status,c.starts_on,c.ends_on
                    FROM erp_contract c WHERE c.customer_id=? ORDER BY c.id DESC
                    """;
            }
            case "projects" -> {
                permission="project:read";
                sql="""
                    SELECT p.id,p.project_no,p.name,p.status,p.planned_start,p.planned_end
                    FROM erp_project p WHERE p.customer_id=? ORDER BY p.id DESC
                    """;
            }
            case "visits" -> {
                permission="service:visit:read";
                sql="""
                    SELECT v.id,v.visit_date,v.visit_type,v.title,v.summary,v.result_summary,v.status,p.id project_id,p.name project_name,
                        (SELECT GROUP_CONCAT(e.full_name ORDER BY e.full_name SEPARATOR '、') FROM erp_service_visit_staff s JOIN erp_employee e ON e.id=s.employee_id WHERE s.visit_id=v.id) staff_names
                    FROM erp_service_visit v JOIN erp_service_work_package w ON w.id=v.work_package_id
                    JOIN erp_project p ON p.id=w.project_id WHERE p.customer_id=?
                    ORDER BY v.visit_date DESC,v.id DESC
                    """;
            }
            case "maintenance" -> {
                permission="maintenance:read";
                sql="""
                    SELECT v.id,v.visit_date,v.notes,p.id project_id,p.name project_name,c.contract_no,
                        (SELECT GROUP_CONCAT(e.full_name ORDER BY e.full_name SEPARATOR '、') FROM erp_maintenance_visit_staff s JOIN erp_employee e ON e.id=s.employee_id WHERE s.visit_id=v.id) staff_names
                    FROM erp_maintenance_visit v JOIN erp_project p ON p.id=v.project_id
                    LEFT JOIN erp_contract c ON c.id=v.contract_id WHERE p.customer_id=?
                    ORDER BY v.visit_date DESC,v.id DESC
                    """;
            }
            case "repairs" -> {
                permission="repair:read";
                sql="""
                    SELECT r.id,r.repair_date,r.title,r.description,r.result_summary,p.id project_id,p.name project_name,
                        (SELECT GROUP_CONCAT(e.full_name ORDER BY e.full_name SEPARATOR '、') FROM erp_repair_staff s JOIN erp_employee e ON e.id=s.employee_id WHERE s.repair_id=r.id) staff_names
                    FROM erp_repair_record r JOIN erp_project p ON p.id=r.project_id WHERE p.customer_id=?
                    ORDER BY r.repair_date DESC,r.id DESC
                    """;
            }
            case "reports" -> {
                permission="report:read";
                sql="""
                    SELECT r.id,r.report_no,r.title,r.report_type,r.status,r.current_version_no,r.issued_at,
                        p.id project_id,p.name project_name
                    FROM erp_service_report r JOIN erp_service_work_package w ON w.id=r.work_package_id
                    JOIN erp_project p ON p.id=w.project_id WHERE p.customer_id=? ORDER BY r.id DESC
                    """;
            }
            case "deliveries" -> {
                permission="delivery:read";
                sql="""
                    SELECT d.id,d.version_no,d.status,d.delivery_method,d.courier_name,d.tracking_no,d.dispatched_at,d.delivered_at,
                        r.report_no,r.title,p.id project_id,p.name project_name
                    FROM erp_service_delivery d JOIN erp_service_report r ON r.id=d.report_id
                    JOIN erp_service_work_package w ON w.id=r.work_package_id
                    JOIN erp_project p ON p.id=w.project_id WHERE p.customer_id=? ORDER BY d.id DESC
                    """;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"未知业务记录类型");
        }
        require(a,permission);
        List<Map<String,Object>> rows=jdbc.queryForList(sql+" LIMIT ? OFFSET ?",customerId,limit+1,offset);
        boolean more=rows.size()>limit;
        return Map.of("rows",more?rows.subList(0,limit):rows,"offset",offset,"limit",limit,"hasMore",more);
    }

    public record RepairRequest(Long projectId,String repairDate,String title,String description,String resultSummary,List<Long> employeeIds){}
    @PostMapping("/repairs")
    @PreAuthorize("hasAuthority('customer:read') and hasAuthority('repair:write')")
    @Transactional
    public Map<String,Object> createRepair(@PathVariable long customerId,@RequestBody RepairRequest b,Authentication a){
        long project=validate(customerId,b);
        long actor=((CurrentUser)a.getPrincipal()).id();
        jdbc.update("INSERT INTO erp_repair_record(project_id,repair_date,title,description,result_summary,created_by) VALUES(?,?,?,?,?,?)",
            project,LocalDate.parse(b.repairDate()),b.title().trim(),b.description().trim(),b.resultSummary(),actor);
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        saveStaff(id,b.employeeIds());
        audit(actor,id,"repair.created","项目="+project+"；"+b.title());
        return detail(customerId,id);
    }
    @GetMapping("/repairs/{id}")
    @PreAuthorize("hasAuthority('customer:read') and hasAuthority('repair:read')")
    public Map<String,Object> detail(@PathVariable long customerId,@PathVariable long id){
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT r.*,p.name project_name FROM erp_repair_record r JOIN erp_project p ON p.id=r.project_id WHERE r.id=? AND p.customer_id=?",id,customerId));
        result.put("employeeIds",jdbc.query("SELECT employee_id FROM erp_repair_staff WHERE repair_id=? ORDER BY employee_id",(rs,n)->rs.getLong(1),id));
        return result;
    }
    @PutMapping("/repairs/{id}")
    @PreAuthorize("hasAuthority('customer:read') and hasAuthority('repair:write')")
    @Transactional
    public Map<String,Object> updateRepair(@PathVariable long customerId,@PathVariable long id,@RequestBody RepairRequest b,Authentication a){
        Map<String,Object> existing=one("SELECT r.* FROM erp_repair_record r JOIN erp_project p ON p.id=r.project_id WHERE r.id=? AND p.customer_id=? FOR UPDATE",id,customerId);
        long project=validate(customerId,b);
        if(((Number)existing.get("project_id")).longValue()!=project)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"维修记录所属项目不可修改");
        long actor=((CurrentUser)a.getPrincipal()).id();
        jdbc.update("UPDATE erp_repair_record SET repair_date=?,title=?,description=?,result_summary=? WHERE id=?",
            LocalDate.parse(b.repairDate()),b.title().trim(),b.description().trim(),b.resultSummary(),id);
        saveStaff(id,b.employeeIds());
        audit(actor,id,"repair.updated","更正维修记录："+b.title());
        return detail(customerId,id);
    }
    private long validate(long customerId,RepairRequest b){
        if(b.projectId()==null||b.title()==null||b.title().isBlank()||b.title().length()>240||b.description()==null||b.description().isBlank()||b.description().length()>10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择项目并填写维修标题与内容");
        if(b.resultSummary()!=null&&b.resultSummary().length()>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"维修结果过长");
        try{LocalDate.parse(b.repairDate());}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"维修日期无效");}
        one("SELECT id FROM erp_project WHERE id=? AND customer_id=?",b.projectId(),customerId);
        if(b.employeeIds()==null||b.employeeIds().isEmpty()||b.employeeIds().size()>100||b.employeeIds().contains(null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择维修人员");
        for(Long emp:new LinkedHashSet<>(b.employeeIds()))one("SELECT id FROM erp_employee WHERE id=?",emp);
        return b.projectId();
    }
    private void saveStaff(long id,List<Long> employees){
        jdbc.update("DELETE FROM erp_repair_staff WHERE repair_id=?",id);
        for(Long emp:new LinkedHashSet<>(employees))jdbc.update("INSERT INTO erp_repair_staff(repair_id,employee_id) VALUES(?,?)",id,emp);
    }
    private void audit(long actor,long id,String action,String summary){
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor,action,"erp_repair_record",Long.toString(id),UUID.randomUUID().toString(),summary);
    }
}
