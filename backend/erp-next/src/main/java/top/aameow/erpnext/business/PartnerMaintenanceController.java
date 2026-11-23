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
public class PartnerMaintenanceController {
    private final JdbcTemplate jdbc;
    public PartnerMaintenanceController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private String req(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空");return v.toString().trim();}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> r=jdbc.queryForList(sql,args);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");return r.get(0);}
    private Long opt(Map<String,Object>b,String k){Object v=b.get(k);return v==null||v.toString().isBlank()?null:Long.valueOf(v.toString());}
    private void audit(long who,String action,String type,long id,String summary){jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",who,action,type,Long.toString(id),UUID.randomUUID().toString(),summary);}

    @GetMapping("/partners") @PreAuthorize("hasAuthority('partner:read')")
    public List<Map<String,Object>> partners(){return jdbc.queryForList("SELECT p.*,(SELECT COUNT(*) FROM erp_contract_partner cp WHERE cp.partner_id=p.id) contract_count FROM erp_partner p ORDER BY p.name");}
    @GetMapping("/partners/{id}") @PreAuthorize("hasAuthority('partner:read')")
    public Map<String,Object> partner(@PathVariable long id){
        Map<String,Object> p=new LinkedHashMap<>(one("SELECT * FROM erp_partner WHERE id=?",id));
        p.put("contracts",jdbc.queryForList("SELECT c.id,c.contract_no,c.title,c.service_type,c.amount,cp.share_percent,c.status FROM erp_contract_partner cp JOIN erp_contract c ON c.id=cp.contract_id WHERE cp.partner_id=? ORDER BY c.created_at DESC",id));
        return p;
    }
    @PostMapping("/partners") @PreAuthorize("hasAuthority('partner:write')") @Transactional
    public Map<String,Object> createPartner(@RequestBody Map<String,Object>b,Authentication a){
        String code=req(b,"partnerCode"),name=req(b,"name"),type=b.get("partnerType")==null?"introducer":b.get("partnerType").toString();
        jdbc.update("INSERT INTO erp_partner(partner_code,name,partner_type,contact_name,phone,email,address,notes) VALUES(?,?,?,?,?,?,?,?)",code,name,type,b.get("contactName"),b.get("phone"),b.get("email"),b.get("address"),b.get("notes"));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);audit(actor(a),"partner.created","erp_partner",id,name);return one("SELECT * FROM erp_partner WHERE id=?",id);
    }
    @PutMapping("/partners/{id}") @PreAuthorize("hasAuthority('partner:write')") @Transactional
    public Map<String,Object> updatePartner(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_partner WHERE id=?",id);
        jdbc.update("UPDATE erp_partner SET name=COALESCE(?,name),partner_type=COALESCE(?,partner_type),contact_name=COALESCE(?,contact_name),phone=COALESCE(?,phone),email=COALESCE(?,email),address=COALESCE(?,address),notes=COALESCE(?,notes),status=COALESCE(?,status) WHERE id=?",
            b.get("name"),b.get("partnerType"),b.get("contactName"),b.get("phone"),b.get("email"),b.get("address"),b.get("notes"),b.get("status"),id);
        audit(actor(a),"partner.updated","erp_partner",id,Objects.toString(b.get("name"),""));return partner(id);
    }
    @PostMapping("/contracts/{id}/partners") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> linkPartner(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_contract WHERE id=?",id);long partner=Long.parseLong(req(b,"partnerId"));one("SELECT id FROM erp_partner WHERE id=? AND status='active'",partner);
        java.math.BigDecimal share=new java.math.BigDecimal(req(b,"sharePercent"));
        if(share.signum()<0||share.compareTo(java.math.BigDecimal.valueOf(100))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分成比例必须在 0 到 100 之间");
        java.math.BigDecimal existing=jdbc.queryForObject("SELECT COALESCE(SUM(share_percent),0) FROM erp_contract_partner WHERE contract_id=? AND partner_id<>?",java.math.BigDecimal.class,id,partner);
        if(existing.add(share).compareTo(java.math.BigDecimal.valueOf(100))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"该合同合作方分成比例合计不能超过 100%");
        jdbc.update("INSERT INTO erp_contract_partner(contract_id,partner_id,share_percent,notes) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE share_percent=VALUES(share_percent),notes=VALUES(notes)",id,partner,share,b.get("notes"));
        jdbc.update("UPDATE erp_contract SET signing_source='partner',partner_share_percent=(SELECT COALESCE(SUM(share_percent),0) FROM erp_contract_partner WHERE contract_id=?) WHERE id=?",id,id);
        audit(actor(a),"contract.partner.linked","erp_contract",id,"partner="+partner+",share="+share);
        return one("SELECT * FROM erp_contract_partner WHERE contract_id=? AND partner_id=?",id,partner);
    }
    @DeleteMapping("/contracts/{id}/partners/{partnerId}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> unlinkPartner(@PathVariable long id,@PathVariable long partnerId,Authentication a){
        one("SELECT id FROM erp_contract WHERE id=?",id);
        int removed=jdbc.update("DELETE FROM erp_contract_partner WHERE contract_id=? AND partner_id=?",id,partnerId);
        if(removed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"合同合作关系不存在");
        java.math.BigDecimal remaining=jdbc.queryForObject(
            "SELECT COALESCE(SUM(share_percent),0) FROM erp_contract_partner WHERE contract_id=?",
            java.math.BigDecimal.class,id);
        jdbc.update("UPDATE erp_contract SET signing_source=?,partner_share_percent=? WHERE id=?",
            remaining.signum()>0?"partner":"direct",remaining,id);
        audit(actor(a),"contract.partner.unlinked","erp_contract",id,"partner="+partnerId);
        return one("SELECT id,signing_source,partner_share_percent FROM erp_contract WHERE id=?",id);
    }

    @GetMapping("/contracts/{id}/partners") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> contractPartners(@PathVariable long id){
        return jdbc.queryForList("SELECT p.*,cp.share_percent,cp.notes FROM erp_contract_partner cp JOIN erp_partner p ON p.id=cp.partner_id WHERE cp.contract_id=? ORDER BY p.name",id);
    }
    @PostMapping("/contracts/{id}/projects") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> linkProject(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> c=one("SELECT id,customer_id FROM erp_contract WHERE id=?",id);long project=Long.parseLong(req(b,"projectId"));
        Map<String,Object> p=one("SELECT id,customer_id,contract_id FROM erp_project WHERE id=?",project);
        if(!Objects.equals(((Number)c.get("customer_id")).longValue(),((Number)p.get("customer_id")).longValue()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同与项目必须属于同一客户");
        String type=b.get("relationType")==null?"service":b.get("relationType").toString();
        if(!Set.of("service","renewal","supplement","other").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效合同项目关系");
        jdbc.update("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(?,?,?) ON DUPLICATE KEY UPDATE relation_type=VALUES(relation_type)",id,project,type);
        if(p.get("contract_id")==null)jdbc.update("UPDATE erp_project SET contract_id=? WHERE id=?",id,project);
        audit(actor(a),"contract.project.linked","erp_contract",id,"project="+project);
        return one("SELECT * FROM erp_contract_project WHERE contract_id=? AND project_id=?",id,project);
    }
    @GetMapping("/projects/{id}/contracts") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> projectContracts(@PathVariable long id){
        return jdbc.queryForList("SELECT c.*,cp.relation_type FROM erp_contract_project cp JOIN erp_contract c ON c.id=cp.contract_id WHERE cp.project_id=? ORDER BY c.signed_on DESC,c.id DESC",id);
    }
    @PutMapping("/projects/{id}/leadership") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public Map<String,Object> leadership(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_project WHERE id=?",id);
        long manager=Long.parseLong(req(b,"managerEmployeeId")),technical=Long.parseLong(req(b,"technicalLeadEmployeeId"));
        if(manager==technical)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是不同人员");
        validateEngineer(manager);validateEngineer(technical);
        jdbc.update("UPDATE erp_project SET manager_employee_id=?,technical_lead_employee_id=? WHERE id=?",manager,technical,id);
        audit(actor(a),"project.leadership.updated","erp_project",id,"manager="+manager+",technicalLead="+technical);
        return one("SELECT p.*,m.full_name manager_name,t.full_name technical_lead_name FROM erp_project p LEFT JOIN erp_employee m ON m.id=p.manager_employee_id LEFT JOIN erp_employee t ON t.id=p.technical_lead_employee_id WHERE p.id=?",id);
    }
    private void validateEngineer(long id){
        Map<String,Object> e=one("SELECT id,employee_type,status FROM erp_employee WHERE id=?",id);
        if(!"active".equals(e.get("status"))||!"engineer".equals(e.get("employee_type")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是有效的工程师员工");
        Integer q=jdbc.queryForObject("SELECT COUNT(*) FROM erp_employee_qualification WHERE employee_id=? AND status='valid' AND (expires_on IS NULL OR expires_on>=CURRENT_DATE)",Integer.class,id);
        if(q==null||q==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"工程师员工必须有有效资格记录");
    }

    @GetMapping("/projects/{id}/maintenance") @PreAuthorize("hasAuthority('maintenance:read')")
    public Map<String,Object> maintenance(@PathVariable long id){
        Map<String,Object> p=one("SELECT * FROM erp_project WHERE id=?",id);
        Map<String,Object> state=jdbc.queryForList("SELECT m.*,c.contract_no current_contract_no,c.starts_on current_starts_on,c.ends_on current_ends_on FROM erp_maintenance_project m LEFT JOIN erp_contract c ON c.id=m.current_contract_id WHERE m.project_id=?",id).stream().findFirst().orElse(Map.of("project_id",id));
        state=new LinkedHashMap<>(state);
        state.put("visits",jdbc.queryForList("SELECT v.*,c.contract_no,(SELECT GROUP_CONCAT(e.full_name ORDER BY e.full_name SEPARATOR '、') FROM erp_maintenance_visit_staff vs JOIN erp_employee e ON e.id=vs.employee_id WHERE vs.visit_id=v.id) staff_names FROM erp_maintenance_visit v LEFT JOIN erp_contract c ON c.id=v.contract_id WHERE v.project_id=? ORDER BY v.visit_date DESC",id));
        state.put("contractHistory",jdbc.queryForList("SELECT c.id,c.contract_no,c.title,c.starts_on,c.ends_on,c.status FROM erp_contract_project cp JOIN erp_contract c ON c.id=cp.contract_id WHERE cp.project_id=? AND c.service_type='maintenance' ORDER BY c.signed_on DESC,c.id DESC",id));
        return state;
    }
    @PutMapping("/projects/{id}/maintenance") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> updateMaintenance(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_project WHERE id=?",id);
        Long current=opt(b,"currentContractId");
        if(current!=null){
            one("SELECT c.id FROM erp_contract c JOIN erp_contract_project cp ON cp.contract_id=c.id WHERE c.id=? AND cp.project_id=? AND c.service_type='maintenance'",current,id);
        }
        String card=b.get("publicCardStatus")==null?"not_started":b.get("publicCardStatus").toString();
        if(!Set.of("not_started","preparing","sent","delivered").contains(card))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效公示卡状态");
        jdbc.update("INSERT INTO erp_maintenance_project(project_id,honeybee_contract_uploaded,honeybee_project_created,project_materials_completed,public_card_status,current_contract_id,notes,updated_by) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE honeybee_contract_uploaded=VALUES(honeybee_contract_uploaded),honeybee_project_created=VALUES(honeybee_project_created),project_materials_completed=VALUES(project_materials_completed),public_card_status=VALUES(public_card_status),current_contract_id=VALUES(current_contract_id),notes=VALUES(notes),updated_by=VALUES(updated_by)",
            id,Boolean.parseBoolean(Objects.toString(b.get("honeybeeContractUploaded"),"false")),Boolean.parseBoolean(Objects.toString(b.get("honeybeeProjectCreated"),"false")),Boolean.parseBoolean(Objects.toString(b.get("projectMaterialsCompleted"),"false")),card,current,b.get("notes"),actor(a));
        audit(actor(a),"maintenance.project.updated","erp_maintenance_project",id,card);
        return maintenance(id);
    }
    @PostMapping("/projects/{id}/maintenance-visits") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> addMaintenanceVisit(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_project WHERE id=?",id);Long contract=opt(b,"contractId");
        if(contract!=null)one("SELECT c.id FROM erp_contract c JOIN erp_contract_project cp ON cp.contract_id=c.id WHERE c.id=? AND cp.project_id=? AND c.service_type='maintenance'",contract,id);
        jdbc.update("INSERT INTO erp_maintenance_visit(project_id,contract_id,visit_date,notes,created_by) VALUES(?,?,?,?,?)",id,contract,req(b,"visitDate"),b.get("notes"),actor(a));
        long visit=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        Object staff=b.get("employeeIds");
        if(staff instanceof List<?> ids)for(Object value:ids){long employee=Long.parseLong(value.toString());one("SELECT id FROM erp_employee WHERE id=?",employee);jdbc.update("INSERT INTO erp_maintenance_visit_staff(visit_id,employee_id) VALUES(?,?)",visit,employee);}
        audit(actor(a),"maintenance.visit.created","erp_maintenance_visit",visit,"project="+id);
        return one("SELECT * FROM erp_maintenance_visit WHERE id=?",visit);
    }
    @PostMapping("/contracts/{id}/files") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> addContractFile(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_contract WHERE id=?",id);long file=Long.parseLong(req(b,"fileId"));
        Map<String,Object> f=one("SELECT id,uploaded_by FROM erp_file_object WHERE id=?",file);
        if(((Number)f.get("uploaded_by")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的文件");
        String type=b.get("fileType")==null?"contract":b.get("fileType").toString();
        if(!Set.of("contract","maintenance_card","project_material","other").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效合同文件类型");
        jdbc.update("INSERT INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(?,?,?,?,?)",id,file,type,b.get("title"),actor(a));
        long record=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);audit(actor(a),"contract.file.added","erp_contract",id,type);
        return one("SELECT cf.*,f.original_name FROM erp_contract_file cf JOIN erp_file_object f ON f.id=cf.file_id WHERE cf.id=?",record);
    }
}
