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

    private void requireEngineer(long employeeId) {
        Map<String,Object> employee=one("SELECT id,employee_type,status FROM erp_employee WHERE id=?",employeeId);
        if(!"active".equals(employee.get("status"))||!"engineer".equals(employee.get("employee_type")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是有效的工程师员工");
        Integer qualifications=jdbc.queryForObject("SELECT COUNT(*) FROM erp_employee WHERE id=? AND NULLIF(BTRIM(certificate_no),'') IS NOT NULL",Integer.class,employeeId);
        if(qualifications==null||qualifications==0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"工程师员工必须登记证书编号");
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
        long id=jdbc.queryForObject("SELECT lastval()",Long.class); 
        return one("SELECT id,name,parent_id,status FROM erp_organization WHERE id=?",id);
    }

    @PutMapping("/organizations/{id}") @PreAuthorize("hasAuthority('organization:write')") @Transactional
    public Map<String,Object> updateOrganization(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_organization WHERE id=?",id);
        Long parent=optionalLong(b,"parentId");
        if(parent!=null) { if(parent==id) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"部门不能设为自身上级"); one("SELECT id FROM erp_organization WHERE id=?",parent); }
        jdbc.update("UPDATE erp_organization SET name=COALESCE(?,name),parent_id=?,status=COALESCE(?,status) WHERE id=?",b.get("name"),parent,b.get("status"),id);
         return one("SELECT * FROM erp_organization WHERE id=?",id);
    }

    @GetMapping("/customers") @PreAuthorize("hasAuthority('customer:read')")
    public List<Map<String,Object>> customers(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT c.*,e.full_name owner_name,(SELECT COUNT(*) FROM erp_customer_contact cc WHERE cc.customer_id=c.id) contact_count,
                 (SELECT COUNT(*) FROM erp_contract ct WHERE ct.customer_id=c.id) contract_count
          FROM erp_customer c LEFT JOIN erp_employee e ON e.id=c.owner_employee_id
          WHERE (?='' OR c.name ILIKE ? OR c.customer_code ILIKE ? OR c.credit_code ILIKE ?)
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
        String name=required(b,"name"); Long owner=optionalLong(b,"ownerEmployeeId");
        if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        jdbc.update("""
          INSERT INTO erp_customer(customer_code,name,credit_code,customer_type,phone,address,status,owner_employee_id,notes)
          VALUES(NULL,?,?,?,?,?,?,?,?)
          """,name,optionalText(b,"creditCode"),val(b,"customerType","enterprise"),b.get("phone"),b.get("address"),val(b,"status","active"),owner,b.get("notes"));
        long id=jdbc.queryForObject("SELECT lastval()",Long.class); 
        return customer(id);
    }
    @PutMapping("/customers/{id}") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public Map<String,Object> updateCustomer(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_customer WHERE id=?",id);
        Long owner=optionalLong(b,"ownerEmployeeId");
        if(b.containsKey("ownerEmployeeId")&&owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        jdbc.update("UPDATE erp_customer SET name=COALESCE(?,name),credit_code=COALESCE(?,credit_code),customer_type=COALESCE(?,customer_type),phone=COALESCE(?,phone),address=COALESCE(?,address),status=COALESCE(?,status),notes=COALESCE(?,notes) WHERE id=?",
            b.get("name"),optionalText(b,"creditCode"),b.get("customerType"),b.get("phone"),b.get("address"),b.get("status"),b.get("notes"),id);
        if(b.containsKey("ownerEmployeeId")) jdbc.update("UPDATE erp_customer SET owner_employee_id=? WHERE id=?",owner,id);
         return customer(id);
    }
    private String val(Map<String,Object>b,String k,String d) { Object v=b.get(k); return v==null?d:v.toString(); }

    @GetMapping("/customers/{id}/contacts") @PreAuthorize("hasAuthority('customer:read')")
    public List<Map<String,Object>> contacts(@PathVariable long id) { one("SELECT id FROM erp_customer WHERE id=?",id); return jdbc.queryForList("SELECT * FROM erp_customer_contact WHERE customer_id=? ORDER BY is_primary DESC,id",id); }
    @PostMapping("/customers/{id}/contacts") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public Map<String,Object> addContact(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_customer WHERE id=?",id); String name=required(b,"name");
        boolean primary=Boolean.parseBoolean(val(b,"isPrimary","false"));
        if(primary) jdbc.update("UPDATE erp_customer_contact SET is_primary=FALSE WHERE customer_id=?",id);
        jdbc.update("INSERT INTO erp_customer_contact(customer_id,name,position_name,phone,email,is_primary,notes) VALUES(?,?,?,?,?,?,?)",
            id,name,b.get("positionName"),b.get("phone"),b.get("email"),primary,b.get("notes"));
        long cid=jdbc.queryForObject("SELECT lastval()",Long.class); 
        return one("SELECT * FROM erp_customer_contact WHERE id=?",cid);
    }
    @PutMapping("/contacts/{id}") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public Map<String,Object> updateContact(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> existing=one("SELECT id,customer_id FROM erp_customer_contact WHERE id=?",id);
        if(b.containsKey("name")) required(b,"name");
        if(b.containsKey("isPrimary") && !(b.get("isPrimary") instanceof Boolean))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"主要联系人标记必须为布尔值");
        if(Boolean.TRUE.equals(b.get("isPrimary")))
            jdbc.update("UPDATE erp_customer_contact SET is_primary=FALSE WHERE customer_id=?",existing.get("customer_id"));
        jdbc.update("UPDATE erp_customer_contact SET name=COALESCE(?,name),position_name=?,phone=?,email=?,notes=?,is_primary=COALESCE(?,is_primary) WHERE id=?",
            b.get("name"),b.get("positionName"),b.get("phone"),b.get("email"),b.get("notes"),b.get("isPrimary"),id);
        
        return one("SELECT * FROM erp_customer_contact WHERE id=?",id);
    }

    @DeleteMapping("/contacts/{id}") @PreAuthorize("hasAuthority('customer:write')") @Transactional
    public void deleteContact(@PathVariable long id,Authentication a) {
        Map<String,Object> c=one("SELECT * FROM erp_customer_contact WHERE id=?",id);
        jdbc.update("DELETE FROM erp_customer_contact WHERE id=?",id); 
    }

    @GetMapping("/contracts") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> contracts(@RequestParam(defaultValue="") String q,
                                               @RequestParam(defaultValue="") String status,
                                               @RequestParam(defaultValue="") String serviceType) {
        if(!serviceType.isBlank()) ContractProjectCreation.requireType(serviceType);
        String keyword=q==null?"":q.trim();
        return jdbc.queryForList("""
          SELECT ct.*,c.name customer_name,e.full_name owner_name,
                 COALESCE((SELECT string_agg(p.name,'、' ORDER BY p.name)
                           FROM erp_contract_partner cp JOIN erp_partner p ON p.id=cp.partner_id
                           WHERE cp.contract_id=ct.id),'公司自主签订') intermediary_names,
                 (SELECT COUNT(*) FROM erp_contract_file cf WHERE cf.contract_id=ct.id) file_count,
                 CASE WHEN ct.status='active' AND ct.ends_on IS NOT NULL THEN (ct.ends_on - CURRENT_DATE) ELSE NULL END days_until_expiry
          FROM erp_contract ct
          JOIN erp_customer c ON c.id=ct.customer_id LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id
          WHERE (CAST(? AS TEXT)='' OR ct.title ILIKE CONCAT('%',CAST(? AS TEXT),'%')
                 OR ct.contract_no ILIKE CONCAT('%',CAST(? AS TEXT),'%')
                 OR COALESCE(ct.external_contract_no,'') ILIKE CONCAT('%',CAST(? AS TEXT),'%')
                 OR c.name ILIKE CONCAT('%',CAST(? AS TEXT),'%'))
            AND (CAST(? AS TEXT)='' OR ct.status=CAST(? AS TEXT))
            AND (CAST(? AS TEXT)='' OR ct.service_type=CAST(? AS TEXT))
          ORDER BY ct.id DESC LIMIT 500
          """,keyword,keyword,keyword,keyword,keyword,status,status,serviceType,serviceType);
    }
    private void validateContractFields(Map<String,Object> b,Map<String,Object> current){
        String status=Objects.toString(b.get("status"),Objects.toString(current.get("status"),"draft"));
        if(!Set.of("draft","pending","active","completed","terminated").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同状态无效");
        String type=Objects.toString(b.get("serviceType"),Objects.toString(current.get("service_type"),"other"));
        if(!Set.of("maintenance","fire_inspection","fire_assessment","fire_evaluation","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同服务类型无效");
        try{
            java.math.BigDecimal amount=new BigDecimal(Objects.toString(b.get("amount"),Objects.toString(current.get("amount"),"0")));
            if(amount.signum()<0||amount.scale()>2||amount.precision()-amount.scale()>16)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额必须为非负金额，最多两位小数");
        }catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额格式错误");}
        java.time.LocalDate start=contractDate(b,current,"startsOn","starts_on");
        java.time.LocalDate end=contractDate(b,current,"endsOn","ends_on");
        contractDate(b,current,"signedOn","signed_on");
        if("maintenance".equals(type)&&(start==null||end==null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"维保合同必须填写起止日期");
        if(start!=null&&end!=null&&end.isBefore(start))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同结束日期不能早于开始日期");
        Object external=b.get("externalContractNo");
        if(external!=null&&external.toString().trim().length()>160)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"外部合同编号不能超过160个字符");
    }

    private java.time.LocalDate contractDate(Map<String,Object>b,Map<String,Object>current,String key,String column){
        Object value=b.containsKey(key)?b.get(key):current.get(column);
        if(value==null||value.toString().isBlank())return null;
        try{return java.time.LocalDate.parse(value.toString());}
        catch(java.time.format.DateTimeParseException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同日期格式错误："+key);}
    }

    @PostMapping("/contracts") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> createContract(@RequestBody Map<String,Object>b,Authentication a) {
        validateContractFields(b,Map.of());
        if(!Set.of("draft","pending").contains(val(b,"status","draft")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请先创建草稿或待生效合同，关联项目后再生效");
        String serviceType=val(b,"serviceType","other");
        ContractProjectCreation.requireType(serviceType);
        String reservationId=required(b,"numberReservationId");
        String no=BusinessNumberPool.lock(jdbc,reservationId,serviceType,"contract",actor(a)).number(), title=required(b,"title");
        Long customer=Long.valueOf(required(b,"customerId")), owner=optionalLong(b,"ownerEmployeeId");
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer);
        if(owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        BigDecimal amount=new BigDecimal(val(b,"amount","0"));
        if(amount.signum()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额不能为负数");
        String externalNo=optionalText(b,"externalContractNo");
        jdbc.update("""
          INSERT INTO erp_contract(contract_no,external_contract_no,title,customer_id,contract_type,service_type,amount,currency,signed_on,starts_on,ends_on,status,owner_employee_id,notes)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,no,externalNo,title,customer,b.get("contractType"),serviceType,amount,val(b,"currency","CNY"),
          contractDate(b,Map.of(),"signedOn","signed_on"),contractDate(b,Map.of(),"startsOn","starts_on"),
          contractDate(b,Map.of(),"endsOn","ends_on"),val(b,"status","draft"),owner,b.get("notes"));
        long id=jdbc.queryForObject("SELECT lastval()",Long.class);
        Long partnerId=optionalLong(b,"partnerId");
        if(partnerId!=null){
            one("SELECT id FROM erp_partner WHERE id=?",partnerId);
            jdbc.update("INSERT INTO erp_contract_partner(contract_id,partner_id,share_percent) VALUES(?,?,0) ON CONFLICT (contract_id,partner_id) DO NOTHING",id,partnerId);
            jdbc.update("UPDATE erp_contract SET signing_source='partner',partner_share_percent=0 WHERE id=?",id);
        }
        LinkedHashSet<Long> fileIds=new LinkedHashSet<>();
        Long singleFile=optionalLong(b,"fileId");
        if(singleFile!=null)fileIds.add(singleFile);
        Object rawFiles=b.get("fileIds");
        if(rawFiles instanceof Collection<?> collection){
            for(Object value:collection)if(value!=null&&!value.toString().isBlank())fileIds.add(Long.valueOf(value.toString()));
        }
        if(!fileIds.isEmpty()){
            if(a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("file:upload")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"没有上传合同文件权限");
            for(Long fileId:fileIds){
                Map<String,Object> f=one("SELECT id,uploaded_by FROM erp_file_object WHERE id=? FOR UPDATE",fileId);
                if(!(f.get("uploaded_by") instanceof Number n)||n.longValue()!=actor(a))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的文件");
                jdbc.update("INSERT INTO erp_contract_file(contract_id,file_id,file_type,created_by) VALUES(?,?,'contract',?)",id,fileId,actor(a));
            }
        }
        Long projectId=null, existing=optionalLong(b,"existingProjectId");
        if(existing!=null){
            Map<String,Object> p=one("SELECT * FROM erp_project WHERE id=? FOR UPDATE",existing);
            if(((Number)p.get("customer_id")).longValue()!=customer||!Objects.equals(p.get("service_type"),serviceType))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"已有项目必须与合同的甲方及服务类型一致");
            String relation="maintenance".equals(serviceType)?"renewal":"service";
            jdbc.update("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(?,?,?)",id,existing,relation);
            jdbc.update("UPDATE erp_project SET contract_id=? WHERE id=? AND contract_id IS NULL",id,existing);
            projectId=existing;
        }else{
            projectId=ContractProjectCreation.createIfUnlinked(jdbc,id,actor(a));
        }
        BusinessNumberPool.consume(jdbc,reservationId,actor(a),id,projectId);
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT ct.*,c.name customer_name FROM erp_contract ct JOIN erp_customer c ON c.id=ct.customer_id WHERE ct.id=?",id));
        if(projectId!=null)result.put("projectId",projectId);
        return result;
    }
    private String optionalText(Map<String,Object>b,String key){
        Object value=b.get(key);
        if(value==null)return null;
        String text=value.toString().trim();
        return text.isEmpty()?null:text;
    }
    @PutMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> updateContract(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> current=one("SELECT * FROM erp_contract WHERE id=? FOR UPDATE",id);
        validateContractFields(b,current);
        if(b.containsKey("title"))required(b,"title");
        if(b.containsKey("serviceType")&&!"maintenance".equals(b.get("serviceType"))){
            Integer used=jdbc.queryForObject("SELECT COUNT(*) FROM erp_maintenance_project WHERE current_contract_id=?",Integer.class,id);
            if(used!=null&&used>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"当前维保合同不能改为其他服务类型");
        }
        Long owner=optionalLong(b,"ownerEmployeeId");
        if(b.containsKey("ownerEmployeeId")&&owner!=null) one("SELECT id FROM erp_employee WHERE id=?",owner);
        if(b.containsKey("serviceType")&&!Objects.equals(b.get("serviceType"),current.get("service_type")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同类别决定合同编号前缀，创建后不能修改");
        if("active".equals(b.get("status"))||"completed".equals(b.get("status"))) {
            Integer projects=jdbc.queryForObject("SELECT COUNT(*) FROM erp_contract_project WHERE contract_id=?",Integer.class,id);
            if(projects==null||projects==0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同生效前至少应关联一个项目");
        }
        jdbc.update("UPDATE erp_contract SET title=COALESCE(?,title),contract_type=COALESCE(?,contract_type),amount=COALESCE(?,amount),currency=COALESCE(?,currency),status=COALESCE(?,status),signed_on=?,starts_on=?,ends_on=?,notes=COALESCE(?,notes) WHERE id=?",
            b.get("title"),b.get("contractType"),b.get("amount"),b.get("currency"),b.get("status"),contractDate(b,current,"signedOn","signed_on"),contractDate(b,current,"startsOn","starts_on"),contractDate(b,current,"endsOn","ends_on"),b.get("notes"),id);
        if(b.containsKey("ownerEmployeeId")) jdbc.update("UPDATE erp_contract SET owner_employee_id=? WHERE id=?",owner,id);
        if(b.containsKey("externalContractNo")) jdbc.update("UPDATE erp_contract SET external_contract_no=? WHERE id=?",optionalText(b,"externalContractNo"),id);
        String service=current.get("service_type").toString();
        // Synchronize automatically created projects and the selected current annual maintenance contract.
        jdbc.update("""
            UPDATE erp_project p SET planned_start=?,planned_end=?
            WHERE EXISTS (SELECT 1 FROM erp_maintenance_project m WHERE m.project_id=p.id AND m.current_contract_id=?)
               OR (p.auto_from_contract=TRUE AND p.contract_id=? AND NOT EXISTS (SELECT 1 FROM erp_maintenance_project m WHERE m.project_id=p.id AND m.current_contract_id IS NOT NULL))
            """,contractDate(b,current,"startsOn","starts_on"),contractDate(b,current,"endsOn","ends_on"),id,id);
         return one("SELECT * FROM erp_contract WHERE id=?",id);
    }

    @GetMapping("/projects") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> projects(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String serviceType) {
        if(!serviceType.isBlank()) ContractProjectCreation.requireType(serviceType);
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT p.*,c.name customer_name,ct.contract_no,e.full_name manager_name
          FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id LEFT JOIN erp_contract ct ON ct.id=p.contract_id
          LEFT JOIN erp_employee e ON e.id=p.manager_employee_id
          WHERE (?='' OR p.name ILIKE ? OR p.project_no ILIKE ? OR c.name ILIKE ?)
            AND (?='' OR p.status=?) AND (?='' OR p.service_type=?) ORDER BY p.id DESC LIMIT 500
          """,q.trim(),term,term,term,status,status,serviceType,serviceType);
    }
    @PostMapping("/projects") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> createProject(@RequestBody Map<String,Object>b,Authentication a) {
        String name=required(b,"name"); long customer=Long.parseLong(required(b,"customerId"));
        one("SELECT id FROM erp_customer WHERE id=? AND status='active'",customer); Long contract=optionalLong(b,"contractId");
        String service=val(b,"serviceType","other");
        if(contract!=null) {
            Map<String,Object> ct=one("SELECT id,customer_id,service_type FROM erp_contract WHERE id=? FOR UPDATE",contract);
            if(((Number)ct.get("customer_id")).longValue()!=customer) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同客户与项目客户不一致");
            if(b.containsKey("serviceType")&&!Objects.equals(service,ct.get("service_type"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同与项目的服务类型不一致");
            service=ct.get("service_type").toString();
        }
        ContractProjectCreation.requireType(service);
        java.time.LocalDate start=contractDate(b,Map.of(),"plannedStart","planned_start"),end=contractDate(b,Map.of(),"plannedEnd","planned_end");
        if(start!=null&&end!=null&&end.isBefore(start))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目结束日期不能早于开始日期");
        Long manager=optionalLong(b,"managerEmployeeId"); if(manager!=null) requireEngineer(manager);
        String reservationId=required(b,"numberReservationId");
        String no=BusinessNumberPool.lock(jdbc,reservationId,service,"project",actor(a)).number();
        jdbc.update("INSERT INTO erp_project(project_no,name,contract_id,customer_id,description,manager_employee_id,planned_start,planned_end,service_type,progress,status) VALUES(?,?,?,?,?,?,?,?,?,0,'planned')",
            no,name,contract,customer,b.get("description"),manager,start,end,service);
        long id=jdbc.queryForObject("SELECT lastval()",Long.class); if(contract!=null) jdbc.update("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(?,?,'service')",contract,id); 
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'created',?)",id,actor(a),"项目已创建");
        BusinessNumberPool.consume(jdbc,reservationId,actor(a),contract,id);
        return one("SELECT * FROM erp_project WHERE id=?",id);
    }
    @PutMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> updateProject(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        Map<String,Object> existingProject=one("SELECT * FROM erp_project WHERE id=? FOR UPDATE",id);
        Long manager=optionalLong(b,"managerEmployeeId");
        if(b.containsKey("managerEmployeeId")&&manager!=null) {
            requireEngineer(manager);
            if(existingProject.get("technical_lead_employee_id")!=null&&((Number)existingProject.get("technical_lead_employee_id")).longValue()==manager)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是不同人员");
        }
        BigDecimal progress=b.get("progress")==null?null:new BigDecimal(b.get("progress").toString());
        if(progress!=null && (progress.compareTo(BigDecimal.ZERO)<0||progress.compareTo(BigDecimal.valueOf(100))>0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"进度必须在 0 到 100 之间");
        jdbc.update("UPDATE erp_project SET name=COALESCE(?,name),description=COALESCE(?,description),progress=COALESCE(?,progress),status=COALESCE(?,status) WHERE id=?",
            b.get("name"),b.get("description"),progress,b.get("status"),id);
        if(b.containsKey("managerEmployeeId")) jdbc.update("UPDATE erp_project SET manager_employee_id=? WHERE id=?",manager,id);
        if(b.containsKey("plannedStart")||b.containsKey("plannedEnd")){
            java.time.LocalDate start=contractDate(b,existingProject,"plannedStart","planned_start"),end=contractDate(b,existingProject,"plannedEnd","planned_end");
            if(start!=null&&end!=null&&end.isBefore(start))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目结束日期不能早于开始日期");
            if(Boolean.TRUE.equals(existingProject.get("auto_from_contract"))||"1".equals(Objects.toString(existingProject.get("auto_from_contract")))){
                if(!Objects.equals(start,contractDate(Map.of(),existingProject,"plannedStart","planned_start"))||!Objects.equals(end,contractDate(Map.of(),existingProject,"plannedEnd","planned_end")))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同自动建立的项目日期随合同调整，请编辑合同");
            }
            jdbc.update("UPDATE erp_project SET planned_start=?,planned_end=? WHERE id=?",start,end,id);
        }
        
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'updated',?)",id,actor(a),val(b,"log","项目资料更新"));
        return one("SELECT * FROM erp_project WHERE id=?",id);
    }
    @GetMapping("/contracts/{id}") @PreAuthorize("hasAuthority('contract:read')")
    public Map<String,Object> contract(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT ct.*,c.name customer_name,e.full_name owner_name FROM erp_contract ct JOIN erp_customer c ON c.id=ct.customer_id LEFT JOIN erp_employee e ON e.id=ct.owner_employee_id WHERE ct.id=?",id));
        result.put("partners",jdbc.queryForList("SELECT p.id,p.name,cp.share_percent,cp.notes FROM erp_contract_partner cp JOIN erp_partner p ON p.id=cp.partner_id WHERE cp.contract_id=? ORDER BY p.name",id));
        result.put("projects",jdbc.queryForList("SELECT p.id,p.project_no,p.name,p.status,p.service_type,cp.relation_type FROM erp_contract_project cp JOIN erp_project p ON p.id=cp.project_id WHERE cp.contract_id=? ORDER BY p.id DESC",id));
        result.put("files",jdbc.queryForList("SELECT cf.id,cf.file_id,cf.file_type,cf.title,f.original_name,f.byte_size,f.sha256 FROM erp_contract_file cf JOIN erp_file_object f ON f.id=cf.file_id WHERE cf.contract_id=? ORDER BY cf.id DESC",id));
        return result;
    }

    @GetMapping("/projects/{id}") @PreAuthorize("hasAuthority('project:read')")
    public Map<String,Object> project(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT p.*,c.name customer_name,ct.contract_no,ct.title contract_title,e.full_name manager_name,t.full_name technical_lead_name FROM erp_project p JOIN erp_customer c ON c.id=p.customer_id LEFT JOIN erp_contract ct ON ct.id=p.contract_id LEFT JOIN erp_employee e ON e.id=p.manager_employee_id LEFT JOIN erp_employee t ON t.id=p.technical_lead_employee_id WHERE p.id=?",id));
        result.put("contracts",jdbc.queryForList("SELECT c.id,c.contract_no,c.title,c.service_type,c.status,cp.relation_type,c.starts_on,c.ends_on FROM erp_contract_project cp JOIN erp_contract c ON c.id=cp.contract_id WHERE cp.project_id=? ORDER BY c.signed_on DESC,c.id DESC",id));
        result.put("members",members(id)); result.put("milestones",milestones(id)); result.put("logs",logs(id)); return result;
    }

    @PutMapping("/projects/{id}/members/{employeeId}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public void updateMember(@PathVariable long id,@PathVariable long employeeId,@RequestBody Map<String,Object>b,Authentication a) {
        int changed=jdbc.update("UPDATE erp_project_member SET member_role=? WHERE project_id=? AND employee_id=?",b.get("role"),id,employeeId);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"项目成员不存在");
        
    }
    @DeleteMapping("/projects/{id}/members/{employeeId}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public void removeMember(@PathVariable long id,@PathVariable long employeeId,Authentication a) {
        int changed=jdbc.update("DELETE FROM erp_project_member WHERE project_id=? AND employee_id=?",id,employeeId);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"项目成员不存在");
        
    }
    @PutMapping("/milestones/{id}") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> updateMilestone(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project_milestone WHERE id=?",id);
        jdbc.update("UPDATE erp_project_milestone SET title=COALESCE(?,title),description=COALESCE(?,description),due_on=COALESCE(?,due_on),sort_order=COALESCE(?,sort_order) WHERE id=?",
            b.get("title"),b.get("description"),b.get("dueOn"),b.get("sortOrder"),id);
         return one("SELECT * FROM erp_project_milestone WHERE id=?",id);
    }
    @PutMapping("/milestones/{id}/skip") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> skipMilestone(@PathVariable long id,Authentication a) {
        Map<String,Object> m=one("SELECT * FROM erp_project_milestone WHERE id=?",id);
        jdbc.update("UPDATE erp_project_milestone SET status='skipped' WHERE id=?",id);
        
        return one("SELECT * FROM erp_project_milestone WHERE id=?",id);
    }

    @PostMapping("/projects/{id}/members") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public void addMember(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id); long employee=Long.parseLong(required(b,"employeeId")); one("SELECT id FROM erp_employee WHERE id=?",employee);
        jdbc.update("INSERT INTO erp_project_member(project_id,employee_id,member_role) VALUES(?,?,?) ON CONFLICT (project_id,employee_id) DO UPDATE SET member_role=EXCLUDED.member_role",id,employee,b.get("role"));
        
    }
    @GetMapping("/projects/{id}/members") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> members(@PathVariable long id) {
        return jdbc.queryForList("SELECT e.id,e.full_name,pm.member_role FROM erp_project_member pm JOIN erp_employee e ON e.id=pm.employee_id WHERE pm.project_id=? ORDER BY e.full_name",id);
    }
    @PostMapping("/projects/{id}/milestones") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> addMilestone(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id); String title=required(b,"title");
        jdbc.update("INSERT INTO erp_project_milestone(project_id,title,description,due_on,sort_order) VALUES(?,?,?,?,?)",id,title,b.get("description"),b.get("dueOn"),b.get("sortOrder")==null?0:Integer.valueOf(b.get("sortOrder").toString()));
        long mid=jdbc.queryForObject("SELECT lastval()",Long.class); 
        return one("SELECT * FROM erp_project_milestone WHERE id=?",mid);
    }
    @GetMapping("/projects/{id}/milestones") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> milestones(@PathVariable long id) { return jdbc.queryForList("SELECT * FROM erp_project_milestone WHERE project_id=? ORDER BY sort_order,id",id); }
    @PutMapping("/milestones/{id}/complete") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> completeMilestone(@PathVariable long id,Authentication a) {
        Map<String,Object> m=one("SELECT * FROM erp_project_milestone WHERE id=?",id);
        jdbc.update("UPDATE erp_project_milestone SET status='completed',completed_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);
        
        return one("SELECT * FROM erp_project_milestone WHERE id=?",id);
    }
    @GetMapping("/projects/{id}/logs") @PreAuthorize("hasAuthority('project:read')")
    public List<Map<String,Object>> logs(@PathVariable long id) {
        return jdbc.queryForList("SELECT l.*,i.display_name actor_name FROM erp_project_log l LEFT JOIN erp_identity i ON i.id=l.actor_identity_id WHERE l.project_id=? ORDER BY l.id DESC LIMIT 100",id);
    }
}
