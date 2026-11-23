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
        String type=b.get("employeeType")==null?null:b.get("employeeType").toString();
        if(type!=null&&!Set.of("engineer","operator","employee","manager").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"员工类型无效");
        jdbc.update("UPDATE erp_employee SET full_name=COALESCE(?,full_name),employee_type=COALESCE(?,employee_type),phone=COALESCE(?,phone),email=COALESCE(?,email),position_name=COALESCE(?,position_name),status=COALESCE(?,status) WHERE id=?",
          b.get("fullName"),type,b.get("phone"),b.get("email"),b.get("positionName"),b.get("status"),id);
        audit(a,"employee.updated","erp_employee",id,"员工资料更新");
        return one("SELECT * FROM erp_employee WHERE id=?",id);
    }

    @GetMapping("/employees") @PreAuthorize("hasAuthority('employee:read')")
    public List<Map<String,Object>> employees(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String employeeType) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT e.id,e.employee_code,e.full_name,e.employee_type,e.identity_id,e.status,e.phone,e.email,
                 e.position_name,e.joined_on,e.left_on,
                 (SELECT COUNT(*) FROM erp_employee_qualification q WHERE q.employee_id=e.id) qualification_count,
                 EXISTS(SELECT 1 FROM erp_employee_qualification q WHERE q.employee_id=e.id
                   AND q.qualification_type='engineer' AND q.status='valid'
                   AND (q.expires_on IS NULL OR q.expires_on>=CURRENT_DATE())) has_engineer_qualification
          FROM erp_employee e
          WHERE (?='' OR e.full_name LIKE ? OR e.employee_code LIKE ? OR e.phone LIKE ?)
            AND (?='' OR e.employee_type=?)
          ORDER BY e.id DESC LIMIT 500
          """,q.trim(),term,term,term,employeeType,employeeType);
    }
    @PostMapping("/employees") @PreAuthorize("hasAuthority('employee:write')") @Transactional
    public Map<String,Object> createEmployee(@RequestBody Map<String,Object>b,Authentication a) {
        String name=required(b,"fullName"),type=val(b,"employeeType","employee");
        Long identity=optionalLong(b,"identityId");
        if(!Set.of("engineer","operator","employee","manager").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"员工类型无效");
        if(identity!=null) one("SELECT id FROM erp_identity WHERE id=?",identity);
        jdbc.update("INSERT INTO erp_employee(employee_code,full_name,employee_type,identity_id,phone,email,position_name,status) VALUES(?,?,?,?,?,?,?,'active')",
            b.get("employeeCode"),name,type,identity,b.get("phone"),b.get("email"),b.get("positionName"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"employee.created","erp_employee",id,name);
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
          SELECT ct.*,c.name customer_name,e.full_name owner_name,p.name intermediary_name,
                 (SELECT COUNT(*) FROM erp_project_contract pc WHERE pc.contract_id=ct.id) project_count
          FROM erp_contract ct JOIN erp_customer c ON c.id=ct.customer_id
          LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id LEFT JOIN erp_partner p ON p.id=ct.intermediary_id
          WHERE (?='' OR ct.title LIKE ? OR ct.contract_no LIKE ? OR c.name LIKE ? OR p.name LIKE ?)
            AND (?='' OR ct.status=?) ORDER BY ct.id DESC LIMIT 500
          """,q.trim(),term,term,term,term,status,status);
    }

    @PostMapping("/contracts") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> createContract(@RequestBody Map<String,Object>b,Authentication a) {
        String no=required(b,"contractNo"),title=required(b,"title");
        long customer=Long.parseLong(required(b,"customerId"));
        Long owner=optionalLong(b,"ownerEmployeeId"),partner=optionalLong(b,"intermediaryId");
        String type=val(b,"contractType","other"),mode=val(b,"signingMode","direct");
        if(!Set.of("maintenance","inspection","rating","assessment","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同类型无效");
        if(!Set.of("direct","intermediary").contains(mode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"签约方式无效");
        if("intermediary".equals(mode)) {
            if(partner==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"中间人介绍合同必须选择合作方");
            one("SELECT id FROM erp_partner WHERE id=? AND status='active'",partner);
        } else partner=null;
        BigDecimal share=b.get("intermediarySharePercent")==null?null:new BigDecimal(b.get("intermediarySharePercent").toString());
        if(share!=null&&(share.compareTo(BigDecimal.ZERO)<0||share.compareTo(BigDecimal.valueOf(100))>0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分成比例必须在 0 到 100 之间");
        if("intermediary".equals(mode)&&share==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写中间人分成比例");
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer);
        if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        BigDecimal amount=new BigDecimal(val(b,"amount","0"));
        if(amount.signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额不能为负数");
        jdbc.update("""
          INSERT INTO erp_contract(contract_no,title,customer_id,contract_type,signing_mode,intermediary_id,intermediary_share_percent,
            amount,currency,signed_on,starts_on,ends_on,status,owner_employee_id,notes,revision_no)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)
          """,no,title,customer,type,mode,partner,share,amount,val(b,"currency","CNY"),b.get("signedOn"),b.get("startsOn"),
            b.get("endsOn"),val(b,"status","draft"),owner,b.get("notes"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("""
          INSERT INTO erp_contract_revision(contract_id,revision_no,snapshot_json,snapshot_sha256,change_reason,created_by)
          SELECT id,1,
            JSON_OBJECT('contractNo',contract_no,'title',title,'customerId',customer_id,'contractType',contract_type,
              'signingMode',signing_mode,'intermediaryId',intermediary_id,'intermediarySharePercent',intermediary_share_percent,
              'amount',amount,'currency',currency,'signedOn',signed_on,'startsOn',starts_on,'endsOn',ends_on,'status',status,'notes',notes),
            SHA2(CAST(JSON_OBJECT('contractNo',contract_no,'title',title,'customerId',customer_id,'contractType',contract_type,
              'signingMode',signing_mode,'intermediaryId',intermediary_id,'intermediarySharePercent',intermediary_share_percent,
              'amount',amount,'currency',currency,'signedOn',signed_on,'startsOn',starts_on,'endsOn',ends_on,'status',status,'notes',notes) AS CHAR),256),
            '合同初始版本',? FROM erp_contract WHERE id=?
          """,actor(a),id);
        audit(a,"contract.created","erp_contract",id,no+" "+title);
        return contract(id);
    }

    @PutMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> updateContract(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> old=one("SELECT * FROM erp_contract WHERE id=? FOR UPDATE",id);
        int revision=((Number)old.get("revision_no")).intValue();
        if(b.get("expectedRevisionNo")!=null&&Integer.parseInt(b.get("expectedRevisionNo").toString())!=revision)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"合同版本已更新，请刷新后重试");
        String type=b.get("contractType")==null?old.get("contract_type").toString():b.get("contractType").toString();
        String mode=b.get("signingMode")==null?old.get("signing_mode").toString():b.get("signingMode").toString();
        if(!Set.of("maintenance","inspection","rating","assessment","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同类型无效");
        if(!Set.of("direct","intermediary").contains(mode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"签约方式无效");
        Long partner=b.containsKey("intermediaryId")?optionalLong(b,"intermediaryId"):
            (old.get("intermediary_id")==null?null:((Number)old.get("intermediary_id")).longValue());
        BigDecimal share=b.containsKey("intermediarySharePercent")
            ?(b.get("intermediarySharePercent")==null?null:new BigDecimal(b.get("intermediarySharePercent").toString()))
            :(old.get("intermediary_share_percent")==null?null:new BigDecimal(old.get("intermediary_share_percent").toString()));
        if("intermediary".equals(mode)) {
            if(partner==null||share==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"中间人介绍合同必须选择合作方并填写分成比例");
            one("SELECT id FROM erp_partner WHERE id=? AND status='active'",partner);
            if(share.compareTo(BigDecimal.ZERO)<0||share.compareTo(BigDecimal.valueOf(100))>0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分成比例必须在 0 到 100 之间");
        } else { partner=null; share=null; }
        BigDecimal amount=b.get("amount")==null?(BigDecimal)old.get("amount"):new BigDecimal(b.get("amount").toString());
        if(amount.signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额不能为负数");
        jdbc.update("""
          UPDATE erp_contract SET title=?,contract_type=?,signing_mode=?,intermediary_id=?,intermediary_share_percent=?,
            amount=?,currency=?,signed_on=?,starts_on=?,ends_on=?,status=?,notes=?,revision_no=revision_no+1 WHERE id=?
          """,b.getOrDefault("title",old.get("title")),type,mode,partner,share,amount,
            b.getOrDefault("currency",old.get("currency")),b.getOrDefault("signedOn",old.get("signed_on")),
            b.getOrDefault("startsOn",old.get("starts_on")),b.getOrDefault("endsOn",old.get("ends_on")),
            b.getOrDefault("status",old.get("status")),b.getOrDefault("notes",old.get("notes")),id);
        int next=revision+1;
        jdbc.update("""
          INSERT INTO erp_contract_revision(contract_id,revision_no,snapshot_json,snapshot_sha256,change_reason,created_by)
          SELECT id,revision_no,
            JSON_OBJECT('contractNo',contract_no,'title',title,'customerId',customer_id,'contractType',contract_type,
              'signingMode',signing_mode,'intermediaryId',intermediary_id,'intermediarySharePercent',intermediary_share_percent,
              'amount',amount,'currency',currency,'signedOn',signed_on,'startsOn',starts_on,'endsOn',ends_on,'status',status,'notes',notes),
            SHA2(CAST(JSON_OBJECT('contractNo',contract_no,'title',title,'customerId',customer_id,'contractType',contract_type,
              'signingMode',signing_mode,'intermediaryId',intermediary_id,'intermediarySharePercent',intermediary_share_percent,
              'amount',amount,'currency',currency,'signedOn',signed_on,'startsOn',starts_on,'endsOn',ends_on,'status',status,'notes',notes) AS CHAR),256),
            ?,? FROM erp_contract WHERE id=?
          """,val(b,"changeReason","合同资料变更"),actor(a),id);
        audit(a,"contract.revised","erp_contract",id,"合同修订 v"+next);
        return contract(id);
    }

    @GetMapping("/projects") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> projects(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT p.*,c.name customer_name,ct.contract_no,e.full_name manager_name,te.full_name technical_lead_name
          FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id
          LEFT JOIN erp_contract ct ON ct.id=p.contract_id
          LEFT JOIN erp_employee e ON e.id=p.manager_employee_id
          LEFT JOIN erp_employee te ON te.id=p.technical_lead_employee_id
          WHERE (?='' OR p.name LIKE ? OR p.project_no LIKE ? OR c.name LIKE ?)
            AND (?='' OR p.status=?) ORDER BY p.id DESC LIMIT 500
          """,q.trim(),term,term,term,status,status);
    }

    @PostMapping("/projects") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> createProject(@RequestBody Map<String,Object>b,Authentication a) {
        String no=required(b,"projectNo"),name=required(b,"name");
        long customer=Long.parseLong(required(b,"customerId"));
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer);
        Long contract=optionalLong(b,"contractId"),manager=optionalLong(b,"managerEmployeeId"),technical=optionalLong(b,"technicalLeadEmployeeId");
        if(contract!=null) {
            Map<String,Object> ct=one("SELECT id,customer_id FROM erp_contract WHERE id=?",contract);
            if(((Number)ct.get("customer_id")).longValue()!=customer) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同客户与项目客户不一致");
        }
        if(manager!=null) assertQualifiedEngineer(manager);
        if(technical!=null) assertQualifiedEngineer(technical);
        if(manager!=null&&manager.equals(technical)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是不同员工");
        jdbc.update("""
          INSERT INTO erp_project(project_no,name,contract_id,customer_id,description,manager_employee_id,technical_lead_employee_id,
            planned_start,planned_end,progress,status,honeybee_uploaded,documents_ready,maintenance_card_sent)
          VALUES(?,?,?,?,?,?,?,?,?,0,'planned',?,?,?)
          """,no,name,contract,customer,b.get("description"),manager,technical,b.get("plannedStart"),b.get("plannedEnd"),
            Boolean.parseBoolean(val(b,"honeybeeUploaded","false")),Boolean.parseBoolean(val(b,"documentsReady","false")),
            Boolean.parseBoolean(val(b,"maintenanceCardSent","false")));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(contract!=null) jdbc.update("INSERT INTO erp_project_contract(project_id,contract_id,relation_type,is_current) VALUES(?,?,'initial',TRUE)",id,contract);
        audit(a,"project.created","erp_project",id,no+" "+name);
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'created',?)",id,actor(a),"项目已创建");
        return project(id);
    }

    @PutMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> updateProject(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> old=one("SELECT * FROM erp_project WHERE id=? FOR UPDATE",id);
        Long manager=b.containsKey("managerEmployeeId")?optionalLong(b,"managerEmployeeId"):
            (old.get("manager_employee_id")==null?null:((Number)old.get("manager_employee_id")).longValue());
        Long technical=b.containsKey("technicalLeadEmployeeId")?optionalLong(b,"technicalLeadEmployeeId"):
            (old.get("technical_lead_employee_id")==null?null:((Number)old.get("technical_lead_employee_id")).longValue());
        if(b.containsKey("managerEmployeeId")&&manager!=null) assertQualifiedEngineer(manager);
        if(b.containsKey("technicalLeadEmployeeId")&&technical!=null) assertQualifiedEngineer(technical);
        if(manager!=null&&manager.equals(technical)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是不同员工");
        BigDecimal progress=b.get("progress")==null?null:new BigDecimal(b.get("progress").toString());
        if(progress!=null&&(progress.compareTo(BigDecimal.ZERO)<0||progress.compareTo(BigDecimal.valueOf(100))>0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"进度必须在 0 到 100 之间");
        jdbc.update("""
          UPDATE erp_project SET name=COALESCE(?,name),description=COALESCE(?,description),
            manager_employee_id=?,technical_lead_employee_id=?,planned_start=COALESCE(?,planned_start),planned_end=COALESCE(?,planned_end),
            actual_start=COALESCE(?,actual_start),actual_end=COALESCE(?,actual_end),progress=COALESCE(?,progress),status=COALESCE(?,status),
            honeybee_uploaded=?,honeybee_uploaded_on=?,documents_ready=?,documents_ready_on=?,maintenance_card_sent=?,maintenance_card_sent_on=?
          WHERE id=?
          """,b.get("name"),b.get("description"),manager,technical,b.get("plannedStart"),b.get("plannedEnd"),b.get("actualStart"),b.get("actualEnd"),
            progress,b.get("status"),
            b.containsKey("honeybeeUploaded")?Boolean.parseBoolean(b.get("honeybeeUploaded").toString()):old.get("honeybee_uploaded"),
            b.containsKey("honeybeeUploadedOn")?b.get("honeybeeUploadedOn"):old.get("honeybee_uploaded_on"),
            b.containsKey("documentsReady")?Boolean.parseBoolean(b.get("documentsReady").toString()):old.get("documents_ready"),
            b.containsKey("documentsReadyOn")?b.get("documentsReadyOn"):old.get("documents_ready_on"),
            b.containsKey("maintenanceCardSent")?Boolean.parseBoolean(b.get("maintenanceCardSent").toString()):old.get("maintenance_card_sent"),
            b.containsKey("maintenanceCardSentOn")?b.get("maintenanceCardSentOn"):old.get("maintenance_card_sent_on"),id);
        audit(a,"project.updated","erp_project",id,"项目资料更新");
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'updated',?)",id,actor(a),val(b,"log","项目资料更新"));
        return project(id);
    }

    @GetMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:read')")
    public Map<String,Object> contract(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("""
          SELECT ct.*,c.name customer_name,e.full_name owner_name,p.name intermediary_name
          FROM erp_contract ct JOIN erp_customer c ON c.id=ct.customer_id
          LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id LEFT JOIN erp_partner p ON p.id=ct.intermediary_id WHERE ct.id=?
          """,id));
        result.put("projects",jdbc.queryForList("""
          SELECT pc.id,pc.relation_type,pc.is_current,pc.linked_at,p.id project_id,p.project_no,p.name project_name
          FROM erp_project_contract pc JOIN erp_project p ON p.id=pc.project_id WHERE pc.contract_id=? ORDER BY pc.is_current DESC,p.name
          """,id));
        result.put("revisions",jdbc.queryForList("SELECT revision_no,snapshot_json,snapshot_sha256,change_reason,created_by,created_at FROM erp_contract_revision WHERE contract_id=? ORDER BY revision_no DESC",id));
        result.put("documents",jdbc.queryForList("""
          SELECT d.id,d.file_id,d.document_type,d.label,d.created_at,f.original_name,f.byte_size file_size,f.sha256
          FROM erp_contract_document d JOIN erp_file_object f ON f.id=d.file_id WHERE d.contract_id=? ORDER BY d.id DESC
          """,id));
        return result;
    }

    @GetMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:read')")
    public Map<String,Object> project(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("""
          SELECT p.*,c.name customer_name,ct.contract_no,ct.title contract_title,e.full_name manager_name,te.full_name technical_lead_name
          FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id
          LEFT JOIN erp_contract ct ON ct.id=p.contract_id
          LEFT JOIN erp_employee e ON e.id=p.manager_employee_id
          LEFT JOIN erp_employee te ON te.id=p.technical_lead_employee_id WHERE p.id=?
          """,id));
        result.put("contracts",jdbc.queryForList("""
          SELECT pc.id,pc.relation_type,pc.is_current,pc.linked_at,ct.id contract_id,ct.contract_no,ct.title,ct.starts_on,ct.ends_on,
                 ct.status,ct.contract_type,ct.amount,ct.currency
          FROM erp_project_contract pc JOIN erp_contract ct ON ct.id=pc.contract_id
          WHERE pc.project_id=? ORDER BY pc.is_current DESC,ct.starts_on DESC,pc.id DESC
          """,id));
        result.put("members",members(id)); result.put("milestones",milestones(id)); result.put("logs",logs(id));
        return result;
    }

    @GetMapping("/projects/{id}/contracts") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> projectContracts(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT pc.id,pc.relation_type,pc.is_current,pc.linked_at,ct.id contract_id,ct.contract_no,ct.title,ct.starts_on,ct.ends_on,
                 ct.status,ct.contract_type,ct.amount,ct.currency
          FROM erp_project_contract pc JOIN erp_contract ct ON ct.id=pc.contract_id WHERE pc.project_id=?
          ORDER BY pc.is_current DESC,ct.starts_on DESC,pc.id DESC
          """,id);
    }

    @PostMapping("/projects/{id}/contracts") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> linkProjectContract(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> project=one("SELECT customer_id FROM erp_project WHERE id=? FOR UPDATE",id);
        long contractId=Long.parseLong(required(b,"contractId"));
        Map<String,Object> contract=one("SELECT customer_id FROM erp_contract WHERE id=?",contractId);
        if(((Number)project.get("customer_id")).longValue()!=((Number)contract.get("customer_id")).longValue())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同客户与项目客户不一致");
        String relation=val(b,"relationType","initial");
        if(!Set.of("initial","renewal","supplemental").contains(relation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同关系类型无效");
        boolean current=Boolean.parseBoolean(val(b,"isCurrent","true"));
        if(current) jdbc.update("UPDATE erp_project_contract SET is_current=FALSE WHERE project_id=?",id);
        jdbc.update("""
          INSERT INTO erp_project_contract(project_id,contract_id,relation_type,is_current) VALUES(?,?,?,?)
          ON DUPLICATE KEY UPDATE relation_type=VALUES(relation_type),is_current=VALUES(is_current)
          """,id,contractId,relation,current);
        if(current) jdbc.update("UPDATE erp_project SET contract_id=? WHERE id=?",contractId,id);
        audit(a,"project.contract.linked","erp_project",id,"关联合同 "+contractId+"（"+relation+"）");
        return project(id);
    }

    @GetMapping("/partners") @PreAuthorize("hasAuthority('partner:read')")
    public List<Map<String,Object>> partners(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT p.*,(SELECT COUNT(*) FROM erp_contract c WHERE c.intermediary_id=p.id) contract_count
          FROM erp_partner p WHERE (?='' OR p.name LIKE ? OR p.partner_code LIKE ? OR p.contact_name LIKE ?)
            AND (?='' OR p.status=?) ORDER BY p.id DESC LIMIT 500
          """,q.trim(),term,term,term,status,status);
    }
    @GetMapping("/partners/{id}") @PreAuthorize("hasAuthority('partner:read')")
    public Map<String,Object> partner(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT * FROM erp_partner WHERE id=?",id));
        result.put("contracts",jdbc.queryForList("SELECT id,contract_no,title,customer_id,status,intermediary_share_percent FROM erp_contract WHERE intermediary_id=? ORDER BY id DESC",id));
        return result;
    }
    @PostMapping("/partners") @PreAuthorize("hasAuthority('partner:write')") @Transactional
    public Map<String,Object> createPartner(@RequestBody Map<String,Object>b,Authentication a) {
        String code=required(b,"partnerCode"),name=required(b,"name");
        jdbc.update("INSERT INTO erp_partner(partner_code,name,contact_name,phone,email,address,status,notes) VALUES(?,?,?,?,?,?,?,?)",
            code,name,b.get("contactName"),b.get("phone"),b.get("email"),b.get("address"),val(b,"status","active"),b.get("notes"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"partner.created","erp_partner",id,name);
        return partner(id);
    }
    @PutMapping("/partners/{id}") @PreAuthorize("hasAuthority('partner:write')") @Transactional
    public Map<String,Object> updatePartner(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_partner WHERE id=?",id);
        jdbc.update("UPDATE erp_partner SET name=COALESCE(?,name),contact_name=COALESCE(?,contact_name),phone=COALESCE(?,phone),email=COALESCE(?,email),address=COALESCE(?,address),status=COALESCE(?,status),notes=COALESCE(?,notes) WHERE id=?",
            b.get("name"),b.get("contactName"),b.get("phone"),b.get("email"),b.get("address"),b.get("status"),b.get("notes"),id);
        audit(a,"partner.updated","erp_partner",id,"合作方资料更新");
        return partner(id);
    }

    private void assertQualifiedEngineer(long employeeId) {
        Integer count=jdbc.queryForObject("""
          SELECT COUNT(*) FROM erp_employee e
          WHERE e.id=? AND e.employee_type='engineer' AND e.status='active'
            AND EXISTS(SELECT 1 FROM erp_employee_qualification q WHERE q.employee_id=e.id
              AND q.qualification_type='engineer' AND q.status='valid'
              AND (q.expires_on IS NULL OR q.expires_on>=CURRENT_DATE()))
          """,Integer.class,employeeId);
        if(count==null||count==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是具有有效工程师资格的在职工程师");
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
