package top.aameow.erpnext.business;

import jakarta.validation.constraints.NotBlank;
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
public class BusinessController {
    private final JdbcTemplate jdbc;
    public BusinessController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private long actor(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); }
    private void audit(Authentication a,String action,String type,long id,String summary) {
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor(a),action,type,Long.toString(id),UUID.randomUUID().toString(),summary);
    }
    private String required(Map<String,Object> b,String key) {
        Object v=b.get(key); if(v==null || v.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 不能为空"); return v.toString().trim();
    }
    private Long optionalLong(Map<String,Object>b,String k) { Object v=b.get(k); return v==null||v.toString().isBlank()?null:Long.valueOf(v.toString()); }
    private Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.get(0);
    }
    private List<Map<String,Object>> search(String sql,String q,Object... args) {
        return jdbc.queryForList(sql,args);
    }

    @GetMapping("/organizations") @PreAuthorize("hasAuthority('organization:read')")
    public List<Map<String,Object>> organizations() {
        return jdbc.queryForList("SELECT id,name,parent_id,status,created_at FROM erp_organization ORDER BY name");
    }
    @PostMapping("/organizations") @PreAuthorize("hasAuthority('organization:write')") @Transactional
    public Map<String,Object> createOrganization(@RequestBody Map<String,Object>b,Authentication a) {
        String name=required(b,"name"); Long parent=optionalLong(b,"parentId");
        if(parent!=null) one("SELECT id FROM erp_organization WHERE id=?",parent);
        jdbc.update("INSERT INTO erp_organization(name,parent_id,status) VALUES(?,?,?)",name,parent,"active");
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"organization.created","erp_organization",id,name);
        return one("SELECT id,name,parent_id,status FROM erp_organization WHERE id=?",id);
    }

    @PutMapping("/organizations/{id}") @PreAuthorize("hasAuthority('organization:write')") @Transactional
    public Map<String,Object> updateOrganization(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_organization WHERE id=?",id);
        Long parent=optionalLong(b,"parentId");
        if(parent!=null) { if(parent==id) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"部门不能设为自身上级"); one("SELECT id FROM erp_organization WHERE id=?",parent); }
        jdbc.update("UPDATE erp_organization SET name=COALESCE(?,name),parent_id=?,status=COALESCE(?,status) WHERE id=?",b.get("name"),parent,b.get("status"),id);
        audit(a,"organization.updated","erp_organization",id,"组织资料更新"); return one("SELECT * FROM erp_organization WHERE id=?",id);
    }

    @PutMapping("/employees/{id}") @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> updateEmployee(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_employee WHERE id=?",id);
        jdbc.update("UPDATE erp_employee SET full_name=COALESCE(?,full_name),employee_type=COALESCE(?,employee_type),phone=COALESCE(?,phone),email=COALESCE(?,email),position_name=COALESCE(?,position_name),status=COALESCE(?,status) WHERE id=?",
          b.get("fullName"),b.get("employeeType"),b.get("phone"),b.get("email"),b.get("positionName"),b.get("status"),id);
        audit(a,"employee.updated","erp_employee",id,"员工资料更新"); return one("SELECT * FROM erp_employee WHERE id=?",id);
    }

    @GetMapping("/employees") @PreAuthorize("hasAuthority('employee:read')")
    public List<Map<String,Object>> employees(@RequestParam(defaultValue="") String q) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT e.id,e.employee_code,e.full_name,e.organization_id,o.name organization_name,e.identity_id,
                 e.status,e.employee_type,e.phone,e.email,e.position_name,e.joined_on,e.left_on
          FROM erp_employee e LEFT JOIN erp_organization o ON o.id=e.organization_id
          WHERE ?='' OR e.full_name LIKE ? OR e.employee_code LIKE ? OR e.phone LIKE ?
          ORDER BY e.id DESC LIMIT 500
          """,q.trim(),term,term,term);
    }
    @PostMapping("/employees") @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> createEmployee(@RequestBody Map<String,Object>b,Authentication a) {
        String name=required(b,"fullName"); Long org=optionalLong(b,"organizationId"), identity=optionalLong(b,"identityId");
        if(org!=null) one("SELECT id FROM erp_organization WHERE id=?",org);
        if(identity!=null) one("SELECT id FROM erp_identity WHERE id=?",identity);
        jdbc.update("INSERT INTO erp_employee(employee_code,full_name,employee_type,organization_id,identity_id,phone,email,position_name,status) VALUES(?,?,?,?,?,?,?,?,'active')",
            b.get("employeeCode"),name,val(b,"employeeType","employee"),org,identity,b.get("phone"),b.get("email"),b.get("positionName"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"employee.created","erp_employee",id,name);
        return one("SELECT * FROM erp_employee WHERE id=?",id);
    }

    @GetMapping("/customers") @PreAuthorize("hasAuthority('customer:read')")
    public List<Map<String,Object>> customers(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT c.*,e.full_name owner_name,(SELECT COUNT(*) FROM erp_customer_contact cc WHERE cc.customer_id=c.id) contact_count,
                 (SELECT COUNT(*) FROM erp_contract ct WHERE ct.customer_id=c.id) contract_count
          FROM erp_customer c LEFT JOIN erp_employee e ON e.id=c.owner_employee_id
          WHERE (?='' OR c.name LIKE ? OR c.customer_code LIKE ? OR c.credit_code LIKE ?)
            AND (?='' OR c.status=?) ORDER BY c.id DESC LIMIT 500
          """,q.trim(),term,term,term,status,status);
    }
    @GetMapping("/customers/{id}") @PreAuthorize("hasAuthority('customer:read')")
    public Map<String,Object> customer(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT * FROM erp_customer WHERE id=?",id));
        result.put("contacts",jdbc.queryForList("SELECT * FROM erp_customer_contact WHERE customer_id=? ORDER BY is_primary DESC,id",id));
        result.put("contracts",jdbc.queryForList("SELECT id,contract_no,title,status,amount,currency FROM erp_contract WHERE customer_id=? ORDER BY id DESC",id));
        return result;
    }
    @PostMapping("/customers") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public Map<String,Object> createCustomer(@RequestBody Map<String,Object>b,Authentication a) {
        String code=required(b,"customerCode"), name=required(b,"name"); Long owner=optionalLong(b,"ownerEmployeeId");
        if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        jdbc.update("""
          INSERT INTO erp_customer(customer_code,name,credit_code,customer_type,industry,phone,email,address,status,owner_employee_id,notes)
          VALUES(?,?,?,?,?,?,?,?,?,?,?)
          """,code,name,b.get("creditCode"),val(b,"customerType","enterprise"),b.get("industry"),b.get("phone"),b.get("email"),b.get("address"),val(b,"status","active"),owner,b.get("notes"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"customer.created","erp_customer",id,code+" "+name);
        return customer(id);
    }
    @PutMapping("/customers/{id}") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public Map<String,Object> updateCustomer(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_customer WHERE id=?",id);
        jdbc.update("UPDATE erp_customer SET name=COALESCE(?,name),credit_code=COALESCE(?,credit_code),industry=COALESCE(?,industry),phone=COALESCE(?,phone),email=COALESCE(?,email),address=COALESCE(?,address),status=COALESCE(?,status),notes=COALESCE(?,notes) WHERE id=?",
            b.get("name"),b.get("creditCode"),b.get("industry"),b.get("phone"),b.get("email"),b.get("address"),b.get("status"),b.get("notes"),id);
        audit(a,"customer.updated","erp_customer",id,"客户资料更新"); return customer(id);
    }
    private String val(Map<String,Object>b,String k,String d) { Object v=b.get(k); return v==null?d:v.toString(); }

    @GetMapping("/customers/{id}/contacts") @PreAuthorize("hasAuthority('customer:read')")
    public List<Map<String,Object>> contacts(@PathVariable long id) { one("SELECT id FROM erp_customer WHERE id=?",id); return jdbc.queryForList("SELECT * FROM erp_customer_contact WHERE customer_id=? ORDER BY is_primary DESC,id",id); }
    @PostMapping("/customers/{id}/contacts") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public Map<String,Object> addContact(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_customer WHERE id=?",id); String name=required(b,"name");
        boolean primary=Boolean.parseBoolean(val(b,"isPrimary","false"));
        if(primary) jdbc.update("UPDATE erp_customer_contact SET is_primary=0 WHERE customer_id=?",id);
        jdbc.update("INSERT INTO erp_customer_contact(customer_id,name,position_name,phone,email,is_primary,notes) VALUES(?,?,?,?,?,?,?)",
            id,name,b.get("positionName"),b.get("phone"),b.get("email"),primary,b.get("notes"));
        long cid=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"customer.contact.created","erp_customer_contact",cid,name);
        return one("SELECT * FROM erp_customer_contact WHERE id=?",cid);
    }
    @PutMapping("/contacts/{id}") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public Map<String,Object> updateContact(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_customer_contact WHERE id=?",id);
        jdbc.update("UPDATE erp_customer_contact SET name=COALESCE(?,name),position_name=COALESCE(?,position_name),phone=COALESCE(?,phone),email=COALESCE(?,email),notes=COALESCE(?,notes) WHERE id=?",
          b.get("name"),b.get("positionName"),b.get("phone"),b.get("email"),b.get("notes"),id);
        audit(a,"customer.contact.updated","erp_customer_contact",id,"联系人资料更新"); return one("SELECT * FROM erp_customer_contact WHERE id=?",id);
    }

    @DeleteMapping("/contacts/{id}") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public void deleteContact(@PathVariable long id,Authentication a) {
        Map<String,Object> c=one("SELECT * FROM erp_customer_contact WHERE id=?",id);
        jdbc.update("DELETE FROM erp_customer_contact WHERE id=?",id); audit(a,"customer.contact.deleted","erp_customer_contact",id,c.get("name").toString());
    }

    @GetMapping("/contracts") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> contracts(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT ct.*,c.name customer_name,e.full_name owner_name FROM erp_contract ct
          JOIN erp_customer c ON c.id=ct.customer_id LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id
          WHERE (?='' OR ct.title LIKE ? OR ct.contract_no LIKE ? OR c.name LIKE ?)
            AND (?='' OR ct.status=?) ORDER BY ct.id DESC LIMIT 500
          """,q.trim(),term,term,term,status,status);
    }
    @PostMapping("/contracts") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> createContract(@RequestBody Map<String,Object>b,Authentication a) {
        String no=required(b,"contractNo"), title=required(b,"title"); Long customer=Long.valueOf(required(b,"customerId")), owner=optionalLong(b,"ownerEmployeeId");
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer); if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        BigDecimal amount=new BigDecimal(val(b,"amount","0")); if(amount.signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额不能为负数");
        jdbc.update("""
          INSERT INTO erp_contract(contract_no,title,customer_id,contract_type,signing_mode,intermediary_name,intermediary_share_percent,amount,currency,signed_on,starts_on,ends_on,status,owner_employee_id,notes)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,no,title,customer,val(b,"contractType","other"),val(b,"signingMode","direct"),b.get("intermediaryName"),b.get("intermediarySharePercent")==null?null:new BigDecimal(b.get("intermediarySharePercent").toString()),amount,val(b,"currency","CNY"),b.get("signedOn"),b.get("startsOn"),b.get("endsOn"),val(b,"status","draft"),owner,b.get("notes"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"contract.created","erp_contract",id,no+" "+title);
        return one("SELECT ct.*,c.name customer_name FROM erp_contract ct JOIN erp_customer c ON c.id=ct.customer_id WHERE ct.id=?",id);
    }
    @PutMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> updateContract(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_contract WHERE id=?",id);
        jdbc.update("UPDATE erp_contract SET title=COALESCE(?,title),contract_type=COALESCE(?,contract_type),signing_mode=COALESCE(?,signing_mode),intermediary_name=?,intermediary_share_percent=?,amount=COALESCE(?,amount),currency=COALESCE(?,currency),status=COALESCE(?,status),signed_on=COALESCE(?,signed_on),starts_on=COALESCE(?,starts_on),ends_on=COALESCE(?,ends_on),notes=COALESCE(?,notes) WHERE id=?",
            b.get("title"),b.get("contractType"),b.get("signingMode"),b.get("intermediaryName"),b.get("intermediarySharePercent"),b.get("amount"),b.get("currency"),b.get("status"),b.get("signedOn"),b.get("startsOn"),b.get("endsOn"),b.get("notes"),id);
        audit(a,"contract.updated","erp_contract",id,"合同资料更新"); return one("SELECT * FROM erp_contract WHERE id=?",id);
    }

    @GetMapping("/projects") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> projects(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT p.*,c.name customer_name,ct.contract_no,e.full_name manager_name,te.full_name technical_lead_name
          FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id LEFT JOIN erp_contract ct ON ct.id=p.contract_id
          LEFT JOIN erp_employee e ON e.id=p.manager_employee_id LEFT JOIN erp_employee te ON te.id=p.technical_lead_employee_id
          WHERE (?='' OR p.name LIKE ? OR p.project_no LIKE ? OR c.name LIKE ?)
            AND (?='' OR p.status=?) ORDER BY p.id DESC LIMIT 500
          """,q.trim(),term,term,term,status,status);
    }
    @PostMapping("/projects") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> createProject(@RequestBody Map<String,Object>b,Authentication a) {
        String no=required(b,"projectNo"), name=required(b,"name"); long customer=Long.parseLong(required(b,"customerId"));
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer); Long contract=optionalLong(b,"contractId");
        if(contract!=null) { Map<String,Object> ct=one("SELECT id,customer_id FROM erp_contract WHERE id=?",contract); if(((Number)ct.get("customer_id")).longValue()!=customer) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同客户与项目客户不一致"); }
        Long manager=optionalLong(b,"managerEmployeeId"); if(manager!=null) one("SELECT id FROM erp_employee WHERE id=?",manager);
        jdbc.update("INSERT INTO erp_project(project_no,name,contract_id,customer_id,description,manager_employee_id,technical_lead_employee_id,actual_start,actual_end,progress,status,honeybee_uploaded,documents_ready,maintenance_card_sent) VALUES(?,?,?,?,?,?,?,?,?,0,'planned',?,?,?)",
            no,name,contract,customer,b.get("description"),manager,optionalLong(b,"technicalLeadEmployeeId"),b.get("plannedStart"),b.get("plannedEnd"),Boolean.parseBoolean(val(b,"honeybeeUploaded","false")),Boolean.parseBoolean(val(b,"documentsReady","false")),Boolean.parseBoolean(val(b,"maintenanceCardSent","false")));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"project.created","erp_project",id,no+" "+name);
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'created',?)",id,actor(a),"项目已创建");
        return one("SELECT * FROM erp_project WHERE id=?",id);
    }
    @PutMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> updateProject(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        BigDecimal progress=b.get("progress")==null?null:new BigDecimal(b.get("progress").toString());
        if(progress!=null && (progress.compareTo(BigDecimal.ZERO)<0||progress.compareTo(BigDecimal.valueOf(100))>0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"进度必须在 0 到 100 之间");
        jdbc.update("UPDATE erp_project SET name=COALESCE(?,name),description=COALESCE(?,description),manager_employee_id=COALESCE(?,manager_employee_id),technical_lead_employee_id=COALESCE(?,technical_lead_employee_id),planned_start=COALESCE(?,planned_start),planned_end=COALESCE(?,planned_end),actual_start=COALESCE(?,actual_start),actual_end=COALESCE(?,actual_end),progress=COALESCE(?,progress),status=COALESCE(?,status),honeybee_uploaded=COALESCE(?,honeybee_uploaded),documents_ready=COALESCE(?,documents_ready),maintenance_card_sent=COALESCE(?,maintenance_card_sent) WHERE id=?",
            b.get("name"),b.get("description"),optionalLong(b,"managerEmployeeId"),optionalLong(b,"technicalLeadEmployeeId"),b.get("plannedStart"),b.get("plannedEnd"),b.get("actualStart"),b.get("actualEnd"),progress,b.get("status"),b.get("honeybeeUploaded"),b.get("documentsReady"),b.get("maintenanceCardSent"),id);
        audit(a,"project.updated","erp_project",id,"项目进度或资料更新");
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'updated',?)",id,actor(a),val(b,"log","项目资料更新"));
        return one("SELECT * FROM erp_project WHERE id=?",id);
    }
    @GetMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:read')")
    public Map<String,Object> contract(@PathVariable long id) {
        return one("SELECT ct.*,c.name customer_name,e.full_name owner_name FROM erp_contract ct JOIN erp_customer c ON c.id=ct.customer_id LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id WHERE ct.id=?",id);
    }

    @GetMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:read')")
    public Map<String,Object> project(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT p.*,c.name customer_name,ct.contract_no,ct.title contract_title,e.full_name manager_name,te.full_name technical_lead_name FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id LEFT JOIN erp_contract ct ON ct.id=p.contract_id LEFT JOIN erp_employee e ON e.id=p.manager_employee_id LEFT JOIN erp_employee te ON te.id=p.technical_lead_employee_id WHERE p.id=?",id));
        result.put("members",members(id)); result.put("milestones",milestones(id)); result.put("logs",logs(id)); return result;
    }

    @PutMapping("/projects/{id}/members/{employeeId}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public void updateMember(@PathVariable long id,@PathVariable long employeeId,@RequestBody Map<String,Object>b,Authentication a) {
        int changed=jdbc.update("UPDATE erp_project_member SET member_role=? WHERE project_id=? AND employee_id=?",b.get("role"),id,employeeId);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"项目成员不存在");
        audit(a,"project.member.updated","erp_project",id,"更新成员 "+employeeId);
    }
    @DeleteMapping("/projects/{id}/members/{employeeId}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public void removeMember(@PathVariable long id,@PathVariable long employeeId,Authentication a) {
        int changed=jdbc.update("DELETE FROM erp_project_member WHERE project_id=? AND employee_id=?",id,employeeId);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"项目成员不存在");
        audit(a,"project.member.removed","erp_project",id,"移除成员 "+employeeId);
    }
    @PutMapping("/milestones/{id}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> updateMilestone(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project_milestone WHERE id=?",id);
        jdbc.update("UPDATE erp_project_milestone SET title=COALESCE(?,title),description=COALESCE(?,description),due_on=COALESCE(?,due_on),sort_order=COALESCE(?,sort_order) WHERE id=?",
            b.get("title"),b.get("description"),b.get("dueOn"),b.get("sortOrder"),id);
        audit(a,"project.milestone.updated","erp_project_milestone",id,"更新里程碑"); return one("SELECT * FROM erp_project_milestone WHERE id=?",id);
    }
    @PutMapping("/milestones/{id}/skip") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> skipMilestone(@PathVariable long id,Authentication a) {
        Map<String,Object> m=one("SELECT * FROM erp_project_milestone WHERE id=?",id);
        jdbc.update("UPDATE erp_project_milestone SET status='skipped' WHERE id=?",id);
        audit(a,"project.milestone.skipped","erp_project_milestone",id,m.get("title").toString());
        return one("SELECT * FROM erp_project_milestone WHERE id=?",id);
    }

    @PostMapping("/projects/{id}/members") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public void addMember(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id); long employee=Long.parseLong(required(b,"employeeId")); one("SELECT id FROM erp_employee WHERE id=?",employee);
        jdbc.update("INSERT INTO erp_project_member(project_id,employee_id,member_role) VALUES(?,?,?) ON DUPLICATE KEY UPDATE member_role=VALUES(member_role)",id,employee,b.get("role"));
        audit(a,"project.member.assigned","erp_project",id,"分配员工 "+employee);
    }
    @GetMapping("/projects/{id}/members") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> members(@PathVariable long id) {
        return jdbc.queryForList("SELECT e.id,e.employee_code,e.full_name,pm.member_role FROM erp_project_member pm JOIN erp_employee e ON e.id=pm.employee_id WHERE pm.project_id=? ORDER BY e.full_name",id);
    }
    @PostMapping("/projects/{id}/milestones") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> addMilestone(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id); String title=required(b,"title");
        jdbc.update("INSERT INTO erp_project_milestone(project_id,title,description,due_on,sort_order) VALUES(?,?,?,?,?)",id,title,b.get("description"),b.get("dueOn"),b.get("sortOrder")==null?0:Integer.valueOf(b.get("sortOrder").toString()));
        long mid=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"project.milestone.created","erp_project_milestone",mid,title);
        return one("SELECT * FROM erp_project_milestone WHERE id=?",mid);
    }
    @GetMapping("/projects/{id}/milestones") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> milestones(@PathVariable long id) { return jdbc.queryForList("SELECT * FROM erp_project_milestone WHERE project_id=? ORDER BY sort_order,id",id); }
    @PutMapping("/milestones/{id}/complete") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> completeMilestone(@PathVariable long id,Authentication a) {
        Map<String,Object> m=one("SELECT * FROM erp_project_milestone WHERE id=?",id);
        jdbc.update("UPDATE erp_project_milestone SET status='completed',completed_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);
        audit(a,"project.milestone.completed","erp_project_milestone",id,m.get("title").toString());
        return one("SELECT * FROM erp_project_milestone WHERE id=?",id);
    }
    @GetMapping("/projects/{id}/logs") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> logs(@PathVariable long id) {
        return jdbc.queryForList("SELECT l.*,i.display_name actor_name FROM erp_project_log l LEFT JOIN erp_identity i ON i.id=l.actor_identity_id WHERE l.project_id=? ORDER BY l.id DESC LIMIT 100",id);
    }
}
