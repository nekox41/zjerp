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
        Map<String,Object> existing=one("SELECT id,identity_id,employee_type FROM erp_employee WHERE id=?",id);
        String employeeType=b.get("employeeType")==null?existing.get("employee_type").toString():b.get("employeeType").toString();
        if(!Set.of("engineer","operator","employee","manager").contains(employeeType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"员工类型无效");
        Long identity=b.containsKey("identityId")?optionalLong(b,"identityId"):(existing.get("identity_id")==null?null:((Number)existing.get("identity_id")).longValue());
        if(identity!=null) one("SELECT id FROM erp_identity WHERE id=?",identity);
        jdbc.update("UPDATE erp_employee SET full_name=COALESCE(?,full_name),employee_type=?,identity_id=CASE WHEN ? THEN ? ELSE identity_id END,phone=COALESCE(?,phone),email=COALESCE(?,email),position_name=COALESCE(?,position_name),status=COALESCE(?,status) WHERE id=?",
          b.get("fullName"),employeeType,b.containsKey("identityId"),identity,b.get("phone"),b.get("email"),b.get("positionName"),b.get("status"),id);
        if(identity!=null) syncEmployeeRole(identity,employeeType);
        audit(a,"employee.updated","erp_employee",id,"员工资料更新"); return employee(id);
    }

    private void syncEmployeeRole(long identityId,String employeeType) {
        Integer isAdmin=jdbc.queryForObject("""
          SELECT COUNT(*) FROM erp_identity_role ir JOIN erp_role r ON r.id=ir.role_id
          WHERE ir.identity_id=? AND r.code='admin'
          """,Integer.class,identityId);
        if(isAdmin!=null&&isAdmin>0) return;
        jdbc.update("""
          DELETE ir FROM erp_identity_role ir JOIN erp_role r ON r.id=ir.role_id
          WHERE ir.identity_id=? AND r.code IN ('employee','project_manager','finance','operator')
          """,identityId);
        String roleCode=switch(employeeType) {
            case "engineer" -> "project_manager";
            case "operator" -> "operator";
            case "manager" -> "finance";
            default -> "employee";
        };
        jdbc.update("INSERT IGNORE INTO erp_identity_role(identity_id,role_id) SELECT ?,id FROM erp_role WHERE code=?",identityId,roleCode);
    }

    @GetMapping("/employees") @PreAuthorize("hasAuthority('employee:read')")
    public List<Map<String,Object>> employees(@RequestParam(defaultValue="") String q) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT e.id,e.employee_code,e.full_name,e.employee_type,e.organization_id,o.name organization_name,e.identity_id,
                 e.status,e.phone,e.email,e.position_name,e.joined_on,e.left_on
          FROM erp_employee e LEFT JOIN erp_organization o ON o.id=e.organization_id
          WHERE ?='' OR e.full_name LIKE ? OR e.employee_code LIKE ? OR e.phone LIKE ?
          ORDER BY e.id DESC LIMIT 500
          """,q.trim(),term,term,term);
    }
    @GetMapping("/employees/{id}") @PreAuthorize("hasAuthority('employee:read')")
    public Map<String,Object> employee(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("""
          SELECT e.*,i.username,i.display_name account_display_name
          FROM erp_employee e LEFT JOIN erp_identity i ON i.id=e.identity_id WHERE e.id=?
          """,id));
        result.put("qualifications",jdbc.queryForList("""
          SELECT q.*,CASE WHEN q.expires_on<CURRENT_DATE THEN 'expired'
            WHEN q.expires_on<=DATE_ADD(CURRENT_DATE,INTERVAL 60 DAY) THEN 'expiring' ELSE 'valid' END effective_status
          FROM erp_employee_qualification q WHERE q.employee_id=? ORDER BY q.expires_on
          """,id));
        return result;
    }

    @PostMapping("/employees") @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> createEmployee(@RequestBody Map<String,Object>b,Authentication a) {
        String name=required(b,"fullName"); Long identity=optionalLong(b,"identityId");
        String employeeType=val(b,"employeeType","employee");
        if(!Set.of("engineer","operator","employee","manager").contains(employeeType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"员工类型无效");
        if(identity!=null) one("SELECT id FROM erp_identity WHERE id=?",identity);
        jdbc.update("INSERT INTO erp_employee(employee_code,full_name,employee_type,identity_id,phone,email,position_name,status) VALUES(?,?,?,?,?,?,?,'active')",
            b.get("employeeCode"),name,employeeType,identity,b.get("phone"),b.get("email"),b.get("positionName"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(identity!=null) syncEmployeeRole(identity,employeeType);
        audit(a,"employee.created","erp_employee",id,name);
        return employee(id);
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
        Map<String,Object> current=one("SELECT id,customer_id FROM erp_customer_contact WHERE id=?",id);
        if(Boolean.parseBoolean(val(b,"isPrimary","false")))
            jdbc.update("UPDATE erp_customer_contact SET is_primary=0 WHERE customer_id=? AND id<>?",current.get("customer_id"),id);
        jdbc.update("UPDATE erp_customer_contact SET name=COALESCE(?,name),position_name=COALESCE(?,position_name),phone=COALESCE(?,phone),email=COALESCE(?,email),is_primary=COALESCE(?,is_primary),notes=COALESCE(?,notes) WHERE id=?",
          b.get("name"),b.get("positionName"),b.get("phone"),b.get("email"),b.get("isPrimary"),b.get("notes"),id);
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
          SELECT ct.*,c.name customer_name,e.full_name owner_name,m.name intermediary_name
          FROM erp_contract ct
          JOIN erp_customer c ON c.id=ct.customer_id
          LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id
          LEFT JOIN erp_intermediary m ON m.id=ct.intermediary_id
          WHERE (?='' OR ct.title LIKE ? OR ct.contract_no LIKE ? OR c.name LIKE ? OR m.name LIKE ?)
            AND (?='' OR ct.status=?) ORDER BY ct.id DESC LIMIT 500
          """,q.trim(),term,term,term,term,status,status);
    }
    @PostMapping("/contracts") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> createContract(@RequestBody Map<String,Object>b,Authentication a) {
        String no=required(b,"contractNo"), title=required(b,"title"); Long customer=Long.valueOf(required(b,"customerId")), owner=optionalLong(b,"ownerEmployeeId");
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer); if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        String contractType=val(b,"contractType","other");
        if(!Set.of("maintenance","fire_inspection","fire_evaluation","fire_assessment","other").contains(contractType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同类型无效");
        String signingMode=val(b,"signingMode","direct");
        Long intermediary=optionalLong(b,"intermediaryId");
        BigDecimal share=b.get("intermediarySharePercent")==null?null:new BigDecimal(b.get("intermediarySharePercent").toString());
        String intermediaryName=null;
        if("intermediary".equals(signingMode)){
            if(intermediary==null||share==null||share.signum()<0||share.compareTo(BigDecimal.valueOf(100))>0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"中间人合同必须选择中间人并填写 0–100 的分成比例");
            intermediaryName=one("SELECT name FROM erp_intermediary WHERE id=? AND status='active'",intermediary).get("name").toString();
        } else if(!"direct".equals(signingMode)||intermediary!=null||share!=null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"直接签约合同不能关联中间人或分成比例");
        }
        BigDecimal amount=new BigDecimal(val(b,"amount","0")); if(amount.signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额不能为负数");
        jdbc.update("""
          INSERT INTO erp_contract(contract_no,title,customer_id,contract_type,signing_mode,intermediary_id,intermediary_name_snapshot,intermediary_share_percent,amount,currency,signed_on,starts_on,ends_on,status,owner_employee_id,notes)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,no,title,customer,contractType,signingMode,intermediary,intermediaryName,share,amount,val(b,"currency","CNY"),b.get("signedOn"),b.get("startsOn"),b.get("endsOn"),val(b,"status","draft"),owner,b.get("notes"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); audit(a,"contract.created","erp_contract",id,no+" "+title);
        return contractDetail(id);
    }
    @PutMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> updateContract(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_contract WHERE id=?",id);
        Map<String,Object> existing=one("SELECT signing_mode,intermediary_id,intermediary_share_percent FROM erp_contract WHERE id=?",id);
        String signingMode=b.containsKey("signingMode")?b.get("signingMode").toString():existing.get("signing_mode").toString();
        if(!Set.of("direct","intermediary").contains(signingMode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"签约方式无效");
        Long intermediary=b.containsKey("intermediaryId")?optionalLong(b,"intermediaryId"):(existing.get("intermediary_id")==null?null:((Number)existing.get("intermediary_id")).longValue());
        BigDecimal share=b.containsKey("intermediarySharePercent")?(b.get("intermediarySharePercent")==null?null:new BigDecimal(b.get("intermediarySharePercent").toString())):
            (existing.get("intermediary_share_percent")==null?null:new BigDecimal(existing.get("intermediary_share_percent").toString()));
        String intermediaryName=null;
        if("intermediary".equals(signingMode)){
            if(intermediary==null||share==null||share.signum()<0||share.compareTo(BigDecimal.valueOf(100))>0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"中间人合同必须完整填写中间人与分成比例");
            intermediaryName=one("SELECT name FROM erp_intermediary WHERE id=? AND status='active'",intermediary).get("name").toString();
        } else {
            intermediary=null;share=null;intermediaryName=null;
        }
        jdbc.update("UPDATE erp_contract SET title=COALESCE(?,title),contract_type=COALESCE(?,contract_type),signing_mode=COALESCE(?,signing_mode),intermediary_id=?,intermediary_name_snapshot=?,intermediary_share_percent=?,amount=COALESCE(?,amount),currency=COALESCE(?,currency),status=COALESCE(?,status),signed_on=COALESCE(?,signed_on),starts_on=COALESCE(?,starts_on),ends_on=COALESCE(?,ends_on),notes=COALESCE(?,notes) WHERE id=?",
            b.get("title"),b.get("contractType"),signingMode,intermediary,intermediaryName,share,b.get("amount"),b.get("currency"),b.get("status"),b.get("signedOn"),b.get("startsOn"),b.get("endsOn"),b.get("notes"),id);
        audit(a,"contract.updated","erp_contract",id,"合同资料更新"); return contractDetail(id);
    }

    @GetMapping("/projects") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> projects(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT p.*,c.name customer_name,ct.contract_no,e.full_name manager_name
          FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id LEFT JOIN erp_contract ct ON ct.id=p.contract_id
          LEFT JOIN erp_employee e ON e.id=p.manager_employee_id
          WHERE (?='' OR p.name LIKE ? OR p.project_no LIKE ? OR c.name LIKE ?)
            AND (?='' OR p.status=?) ORDER BY p.id DESC LIMIT 500
          """,q.trim(),term,term,term,status,status);
    }
    @PostMapping("/projects") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> createProject(@RequestBody Map<String,Object>b,Authentication a) {
        String no=required(b,"projectNo"), name=required(b,"name"); long customer=Long.parseLong(required(b,"customerId"));
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer); Long contract=optionalLong(b,"contractId");
        if(contract!=null) { Map<String,Object> ct=one("SELECT id,customer_id FROM erp_contract WHERE id=?",contract); if(((Number)ct.get("customer_id")).longValue()!=customer) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同客户与项目客户不一致"); }
        Long manager=optionalLong(b,"managerEmployeeId"), technicalLead=optionalLong(b,"technicalLeadEmployeeId");
        if(manager!=null) assertQualifiedEngineer(manager,"项目负责人");
        if(technicalLead!=null) assertQualifiedEngineer(technicalLead,"技术负责人");
        if(manager!=null&&manager.equals(technicalLead)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是不同人员");
        jdbc.update("INSERT INTO erp_project(project_no,name,contract_id,customer_id,description,manager_employee_id,technical_lead_employee_id,planned_start,planned_end,actual_start,actual_end,progress,status,honeybee_uploaded,documents_ready,maintenance_card_sent) VALUES(?,?,?,?,?,?,?,?,?,?,?,0,'planned',?,?,?)",
            no,name,contract,customer,b.get("description"),manager,technicalLead,b.get("plannedStart"),b.get("plannedEnd"),b.get("actualStart"),b.get("actualEnd"),
            Boolean.parseBoolean(val(b,"honeybeeUploaded","false")),Boolean.parseBoolean(val(b,"documentsReady","false")),Boolean.parseBoolean(val(b,"maintenanceCardSent","false")));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(contract!=null) jdbc.update("INSERT INTO erp_project_contract(project_id,contract_id,relation_type,is_current) VALUES(?,?,'initial',TRUE)",id,contract);
        audit(a,"project.created","erp_project",id,no+" "+name);
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'created',?)",id,actor(a),"项目已创建");
        return projectDetail(id);
    }
    @PutMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> updateProject(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        BigDecimal progress=b.get("progress")==null?null:new BigDecimal(b.get("progress").toString());
        if(progress!=null && (progress.compareTo(BigDecimal.ZERO)<0||progress.compareTo(BigDecimal.valueOf(100))>0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"进度必须在 0 到 100 之间");
        Long manager=optionalLong(b,"managerEmployeeId"),technicalLead=optionalLong(b,"technicalLeadEmployeeId");
        if(b.containsKey("managerEmployeeId")&&manager!=null) assertQualifiedEngineer(manager,"项目负责人");
        if(b.containsKey("technicalLeadEmployeeId")&&technicalLead!=null) assertQualifiedEngineer(technicalLead,"技术负责人");
        Long finalManager=b.containsKey("managerEmployeeId")?manager:optionalLong(one("SELECT manager_employee_id FROM erp_project WHERE id=?",id),"manager_employee_id");
        Long finalLead=b.containsKey("technicalLeadEmployeeId")?technicalLead:optionalLong(one("SELECT technical_lead_employee_id FROM erp_project WHERE id=?",id),"technical_lead_employee_id");
        if(finalManager!=null&&finalManager.equals(finalLead)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是不同人员");
        String nextStatus=b.get("status")==null?null:b.get("status").toString();
        if(nextStatus!=null&&!Set.of("planned","in_progress","paused","completed","cancelled").contains(nextStatus))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目状态无效");
        jdbc.update("UPDATE erp_project SET name=COALESCE(?,name),description=COALESCE(?,description),manager_employee_id=?,technical_lead_employee_id=?,planned_start=CASE WHEN ? THEN ? ELSE planned_start END,planned_end=CASE WHEN ? THEN ? ELSE planned_end END,actual_start=CASE WHEN ? THEN ? ELSE actual_start END,actual_end=CASE WHEN ? THEN ? ELSE actual_end END,progress=COALESCE(?,progress),status=COALESCE(?,status),honeybee_uploaded=COALESCE(?,honeybee_uploaded),documents_ready=COALESCE(?,documents_ready),maintenance_card_sent=COALESCE(?,maintenance_card_sent) WHERE id=?",
            b.get("name"),b.get("description"),finalManager,finalLead,b.containsKey("plannedStart"),blankToNull(b.get("plannedStart")),b.containsKey("plannedEnd"),blankToNull(b.get("plannedEnd")),
            b.containsKey("actualStart"),blankToNull(b.get("actualStart")),b.containsKey("actualEnd"),blankToNull(b.get("actualEnd")),
            progress,nextStatus,b.get("honeybeeUploaded"),b.get("documentsReady"),b.get("maintenanceCardSent"),id);
        audit(a,"project.updated","erp_project",id,"项目资料更新");
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'updated',?)",id,actor(a),val(b,"log","项目资料更新"));
        return projectDetail(id);
    }
    @GetMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:read')")
    public Map<String,Object> contract(@PathVariable long id) { return contractDetail(id); }

    private Map<String,Object> contractDetail(long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("""
          SELECT ct.*,c.name customer_name,e.full_name owner_name,m.name intermediary_name
          FROM erp_contract ct JOIN erp_customer c ON c.id=ct.customer_id
          LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id
          LEFT JOIN erp_intermediary m ON m.id=ct.intermediary_id WHERE ct.id=?
          """,id));
        result.put("projects",jdbc.queryForList("""
          SELECT p.id,p.project_no,p.name,pc.relation_type,pc.is_current
          FROM erp_project_contract pc JOIN erp_project p ON p.id=pc.project_id
          WHERE pc.contract_id=? ORDER BY pc.is_current DESC,p.id DESC
          """,id));
        result.put("attachments",jdbc.queryForList("""
          SELECT a.id attachment_id,a.attachment_type,a.title,a.created_at,f.id file_id,f.original_name,f.media_type,f.byte_size file_size,f.sha256
          FROM erp_contract_attachment a JOIN erp_file_object f ON f.id=a.file_id
          WHERE a.contract_id=? ORDER BY a.created_at DESC
          """,id));
        return result;
    }

    @GetMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:read')")
    public Map<String,Object> project(@PathVariable long id) { return projectDetail(id); }
    private Map<String,Object> projectDetail(long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("""
          SELECT p.*,c.name customer_name,ct.contract_no,ct.title contract_title,
                 e.full_name manager_name,te.full_name technical_lead_name
          FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id
          LEFT JOIN erp_contract ct ON ct.id=p.contract_id
          LEFT JOIN erp_employee e ON e.id=p.manager_employee_id
          LEFT JOIN erp_employee te ON te.id=p.technical_lead_employee_id WHERE p.id=?
          """,id));
        result.put("members",members(id)); result.put("logs",logs(id)); return result;
    }

    private void assertQualifiedEngineer(long employeeId,String label) {
        Map<String,Object> employee=one("SELECT id,employee_type,status FROM erp_employee WHERE id=?",employeeId);
        if(!"active".equals(employee.get("status"))||!"engineer".equals(employee.get("employee_type")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+"必须选择在职工程师");
        Integer count=jdbc.queryForObject("""
          SELECT COUNT(*) FROM erp_employee_qualification
          WHERE employee_id=? AND status='valid' AND (expires_on IS NULL OR expires_on>=CURRENT_DATE)
          """,Integer.class,employeeId);
        if(count==null||count==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+"必须具有有效的工程师资格记录");
    }
    private String blankToNull(Object value) { return value==null||value.toString().isBlank()?null:value.toString(); }

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
