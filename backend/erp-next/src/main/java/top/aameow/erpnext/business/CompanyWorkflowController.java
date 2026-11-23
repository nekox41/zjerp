package top.aameow.erpnext.business;

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
public class CompanyWorkflowController {
    private final JdbcTemplate jdbc;
    public CompanyWorkflowController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private long actor(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); }
    private String req(Map<String,Object> b,String key) {
        Object value=b.get(key);
        if(value==null||value.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 不能为空");
        return value.toString().trim();
    }
    private String val(Map<String,Object> b,String key,String fallback) {
        Object value=b.get(key); return value==null?fallback:value.toString();
    }
    private Long opt(Map<String,Object> b,String key) {
        Object value=b.get(key); return value==null||value.toString().isBlank()?null:Long.valueOf(value.toString());
    }
    private Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.get(0);
    }
    private void audit(Authentication a,String action,String type,long id,String summary) {
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor(a),action,type,Long.toString(id),UUID.randomUUID().toString(),summary);
    }
    private List<Long> employeeIds(Object value) {
        if(value==null) return List.of();
        if(!(value instanceof List<?> values)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"employeeIds 必须是数组");
        LinkedHashSet<Long> ids=new LinkedHashSet<>();
        for(Object item:values) {
            long id;
            try { id=Long.parseLong(item.toString()); }
            catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"员工 ID 无效"); }
            one("SELECT id FROM erp_employee WHERE id=? AND status='active'",id);
            ids.add(id);
        }
        return new ArrayList<>(ids);
    }

    // Independent intermediary directory. Contracts reference this record instead of a free-text name.
    @GetMapping("/intermediaries") @PreAuthorize("hasAuthority('intermediary:read')")
    public List<Map<String,Object>> intermediaries(@RequestParam(defaultValue="") String q,
                                                    @RequestParam(defaultValue="") String status) {
        String term="%"+q.trim()+"%";
        return jdbc.queryForList("""
          SELECT m.*,(SELECT COUNT(*) FROM erp_contract c WHERE c.intermediary_id=m.id) contract_count
          FROM erp_intermediary m
          WHERE (?='' OR m.name LIKE ? OR m.intermediary_code LIKE ? OR m.contact_name LIKE ? OR m.phone LIKE ?)
            AND (?='' OR m.status=?)
          ORDER BY m.id DESC LIMIT 500
          """,q.trim(),term,term,term,term,status,status);
    }
    @GetMapping("/intermediaries/{id}") @PreAuthorize("hasAuthority('intermediary:read')")
    public Map<String,Object> intermediary(@PathVariable long id) {
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT * FROM erp_intermediary WHERE id=?",id));
        result.put("contracts",jdbc.queryForList("""
          SELECT c.id,c.contract_no,c.title,c.contract_type,c.amount,c.currency,c.starts_on,c.ends_on,c.status,
                 c.intermediary_share_percent,c.signing_mode,cu.name customer_name
          FROM erp_contract c JOIN erp_customer cu ON cu.id=c.customer_id
          WHERE c.intermediary_id=? ORDER BY c.id DESC
          """,id));
        return result;
    }
    @PostMapping("/intermediaries") @PreAuthorize("hasAuthority('intermediary:write')") @Transactional
    public Map<String,Object> createIntermediary(@RequestBody Map<String,Object> b,Authentication a) {
        String code=req(b,"intermediaryCode"),name=req(b,"name");
        jdbc.update("""
          INSERT INTO erp_intermediary(intermediary_code,name,contact_name,phone,email,address,status,notes,created_by)
          VALUES(?,?,?,?,?,?,?,?,?)
          """,code,name,b.get("contactName"),b.get("phone"),b.get("email"),b.get("address"),val(b,"status","active"),b.get("notes"),actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"intermediary.created","erp_intermediary",id,code+" "+name);
        return intermediary(id);
    }
    @PutMapping("/intermediaries/{id}") @PreAuthorize("hasAuthority('intermediary:write')") @Transactional
    public Map<String,Object> updateIntermediary(@PathVariable long id,@RequestBody Map<String,Object> b,Authentication a) {
        one("SELECT id FROM erp_intermediary WHERE id=?",id);
        jdbc.update("""
          UPDATE erp_intermediary SET name=COALESCE(?,name),contact_name=?,phone=?,email=?,address=?,status=COALESCE(?,status),notes=?
          WHERE id=?
          """,b.get("name"),b.get("contactName"),b.get("phone"),b.get("email"),b.get("address"),b.get("status"),b.get("notes"),id);
        audit(a,"intermediary.updated","erp_intermediary",id,"中间人档案更新");
        return intermediary(id);
    }

    // Project / contract many-to-many history and current-contract selection.
    @GetMapping("/projects/{id}/contracts") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> projectContracts(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT pc.id,pc.project_id,pc.contract_id,pc.relation_type,pc.is_current,pc.linked_at,
                 c.contract_no,c.title,c.contract_type,c.amount,c.currency,c.starts_on,c.ends_on,c.status,
                 c.signing_mode,c.intermediary_name_snapshot,c.intermediary_share_percent,cu.name customer_name
          FROM erp_project_contract pc JOIN erp_contract c ON c.id=pc.contract_id
          JOIN erp_customer cu ON cu.id=c.customer_id
          WHERE pc.project_id=? ORDER BY pc.is_current DESC,c.starts_on DESC,pc.id DESC
          """,id);
    }
    @PostMapping("/projects/{id}/contracts") @PreAuthorize("hasAuthority('project:write')") @Transactional
    public List<Map<String,Object>> linkContract(@PathVariable long id,@RequestBody Map<String,Object> b,Authentication a) {
        Map<String,Object> project=one("SELECT id,customer_id FROM erp_project WHERE id=?",id);
        long contractId=Long.parseLong(req(b,"contractId"));
        Map<String,Object> contract=one("SELECT id,customer_id FROM erp_contract WHERE id=?",contractId);
        if(((Number)project.get("customer_id")).longValue()!=((Number)contract.get("customer_id")).longValue())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同客户与项目客户不一致");
        String relation=val(b,"relationType","initial");
        if(!Set.of("initial","renewal","supplemental").contains(relation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同关系无效");
        boolean current=Boolean.parseBoolean(val(b,"isCurrent","false"));
        if(current) {
            jdbc.update("UPDATE erp_project_contract SET is_current=FALSE WHERE project_id=?",id);
        }
        jdbc.update("""
          INSERT INTO erp_project_contract(project_id,contract_id,relation_type,is_current) VALUES(?,?,?,?)
          ON DUPLICATE KEY UPDATE relation_type=VALUES(relation_type),is_current=VALUES(is_current)
          """,id,contractId,relation,current);
        if(current) jdbc.update("UPDATE erp_project SET contract_id=? WHERE id=?",contractId,id);
        audit(a,"project.contract.linked","erp_project",id,"关联合同 "+contractId+(current?"（当前合同）":""));
        return projectContracts(id);
    }

    @GetMapping("/contracts/{id}/attachments") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> contractAttachments(@PathVariable long id) {
        one("SELECT id FROM erp_contract WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT a.*,f.original_name,f.media_type,f.byte_size file_size,f.sha256,i.display_name created_by_name
          FROM erp_contract_attachment a JOIN erp_file_object f ON f.id=a.file_id
          LEFT JOIN erp_identity i ON i.id=a.created_by
          WHERE a.contract_id=? ORDER BY a.created_at DESC
          """,id);
    }
    @PostMapping("/contracts/{id}/attachments") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> attachContractFile(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a) {
        one("SELECT id FROM erp_contract WHERE id=?",id);
        long fileId=Long.parseLong(req(b,"fileId"));
        Map<String,Object> file=one("SELECT id,uploaded_by FROM erp_file_object WHERE id=?",fileId);
        if(!(file.get("uploaded_by") instanceof Number owner)||owner.longValue()!=actor(a))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的文件");
        String type=val(b,"attachmentType","other");
        if(!Set.of("signed_contract","amendment","maintenance_card","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同附件类型无效");
        jdbc.update("INSERT INTO erp_contract_attachment(contract_id,file_id,attachment_type,title,created_by) VALUES(?,?,?,?,?)",
            id,fileId,type,b.get("title"),actor(a));
        long attachmentId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"contract.attachment.added","erp_contract_attachment",attachmentId,type);
        return one("SELECT a.*,f.original_name,f.media_type,f.byte_size file_size,f.sha256 FROM erp_contract_attachment a JOIN erp_file_object f ON f.id=a.file_id WHERE a.id=?",attachmentId);
    }
    @DeleteMapping("/contracts/{id}/attachments/{attachmentId}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public void removeContractFile(@PathVariable long id,@PathVariable long attachmentId,Authentication a) {
        one("SELECT id FROM erp_contract_attachment WHERE id=? AND contract_id=?",attachmentId,id);
        jdbc.update("DELETE FROM erp_contract_attachment WHERE id=?",attachmentId);
        audit(a,"contract.attachment.removed","erp_contract_attachment",attachmentId,"移除合同附件");
    }

    // Buildings, parking phases and shared facilities can all be represented as project scope units.
    @GetMapping("/projects/{id}/scope-units") @PreAuthorize("hasAuthority('project:scope:read')")
    public List<Map<String,Object>> scopeUnits(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("SELECT * FROM erp_project_scope_unit WHERE project_id=? ORDER BY unit_type,id",id);
    }
    @PostMapping("/projects/{id}/scope-units") @PreAuthorize("hasAuthority('project:scope:write')") @Transactional
    public Map<String,Object> createScopeUnit(@PathVariable long id,@RequestBody Map<String,Object> b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        String type=val(b,"unitType","building");
        if(!Set.of("building","parking_phase","shared_facility","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"检测区域类型无效");
        jdbc.update("""
          INSERT INTO erp_project_scope_unit(project_id,unit_name,unit_type,structure_type,usage_name,fire_resistance_grade,
            above_ground_floors,underground_floors,building_height,total_area,above_ground_area,underground_area,
            location_description,is_shared_facility,service_scope,notes)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,id,req(b,"unitName"),type,b.get("structureType"),b.get("usageName"),b.get("fireResistanceGrade"),
            b.get("aboveGroundFloors"),b.get("undergroundFloors"),decimal(b,"buildingHeight"),decimal(b,"totalArea"),
            decimal(b,"aboveGroundArea"),decimal(b,"undergroundArea"),b.get("locationDescription"),
            Boolean.parseBoolean(val(b,"isSharedFacility","false")),b.get("serviceScope"),b.get("notes"));
        long unitId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit(a,"project.scope_unit.created","erp_project_scope_unit",unitId,req(b,"unitName"));
        return one("SELECT * FROM erp_project_scope_unit WHERE id=?",unitId);
    }
    @PutMapping("/scope-units/{id}") @PreAuthorize("hasAuthority('project:scope:write')") @Transactional
    public Map<String,Object> updateScopeUnit(@PathVariable long id,@RequestBody Map<String,Object> b,Authentication a) {
        one("SELECT id FROM erp_project_scope_unit WHERE id=?",id);
        String type=b.get("unitType")==null?null:b.get("unitType").toString();
        if(type!=null&&!Set.of("building","parking_phase","shared_facility","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"检测区域类型无效");
        jdbc.update("""
          UPDATE erp_project_scope_unit SET unit_name=COALESCE(?,unit_name),unit_type=COALESCE(?,unit_type),
            structure_type=?,usage_name=?,fire_resistance_grade=?,above_ground_floors=?,underground_floors=?,
            building_height=?,total_area=?,above_ground_area=?,underground_area=?,location_description=?,
            is_shared_facility=COALESCE(?,is_shared_facility),service_scope=?,notes=? WHERE id=?
          """,b.get("unitName"),type,b.get("structureType"),b.get("usageName"),b.get("fireResistanceGrade"),
            b.get("aboveGroundFloors"),b.get("undergroundFloors"),decimal(b,"buildingHeight"),decimal(b,"totalArea"),
            decimal(b,"aboveGroundArea"),decimal(b,"undergroundArea"),b.get("locationDescription"),
            b.get("isSharedFacility"),b.get("serviceScope"),b.get("notes"),id);
        audit(a,"project.scope_unit.updated","erp_project_scope_unit",id,"检测区域更新");
        return one("SELECT * FROM erp_project_scope_unit WHERE id=?",id);
    }
    @DeleteMapping("/scope-units/{id}") @PreAuthorize("hasAuthority('project:scope:write')") @Transactional
    public void deleteScopeUnit(@PathVariable long id,Authentication a) {
        one("SELECT id FROM erp_project_scope_unit WHERE id=?",id);
        jdbc.update("DELETE FROM erp_project_scope_unit WHERE id=?",id);
        audit(a,"project.scope_unit.deleted","erp_project_scope_unit",id,"删除检测区域");
    }
    private BigDecimal decimal(Map<String,Object> b,String key) {
        Object value=b.get(key); return value==null||value.toString().isBlank()?null:new BigDecimal(value.toString());
    }

    // Maintenance: manual Honeybee status and occasional visit records only.
    @PutMapping("/projects/{id}/maintenance") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> updateMaintenance(@PathVariable long id,@RequestBody Map<String,Object> b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        jdbc.update("""
          UPDATE erp_project SET honeybee_uploaded=COALESCE(?,honeybee_uploaded),
            documents_ready=COALESCE(?,documents_ready),maintenance_card_sent=COALESCE(?,maintenance_card_sent),
            actual_start=CASE WHEN ? THEN ? ELSE actual_start END,actual_end=CASE WHEN ? THEN ? ELSE actual_end END
          WHERE id=?
          """,b.get("honeybeeUploaded"),b.get("documentsReady"),b.get("maintenanceCardSent"),
            b.containsKey("actualStart"),blank(b.get("actualStart")),b.containsKey("actualEnd"),blank(b.get("actualEnd")),id);
        audit(a,"project.maintenance.updated","erp_project",id,"维护保养状态更新");
        return one("SELECT * FROM erp_project WHERE id=?",id);
    }
    private String blank(Object value) { return value==null||value.toString().isBlank()?null:value.toString(); }

    @GetMapping("/projects/{id}/maintenance-visits") @PreAuthorize("hasAuthority('maintenance:read')")
    public List<Map<String,Object>> maintenanceVisits(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT v.*,c.contract_no,
            (SELECT GROUP_CONCAT(e.full_name ORDER BY e.full_name SEPARATOR '、')
             FROM erp_maintenance_visit_employee ve JOIN erp_employee e ON e.id=ve.employee_id WHERE ve.visit_id=v.id) employee_names
          FROM erp_maintenance_visit v LEFT JOIN erp_contract c ON c.id=v.contract_id
          WHERE v.project_id=? ORDER BY v.visited_on DESC,v.id DESC
          """,id);
    }
    @PostMapping("/projects/{id}/maintenance-visits") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> createMaintenanceVisit(@PathVariable long id,@RequestBody Map<String,Object> b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        Long contractId=opt(b,"contractId");
        if(contractId!=null) one("SELECT id FROM erp_project_contract WHERE project_id=? AND contract_id=?",id,contractId);
        List<Long> employees=employeeIds(b.get("employeeIds"));
        jdbc.update("INSERT INTO erp_maintenance_visit(project_id,contract_id,visited_on,summary,created_by) VALUES(?,?,?,?,?)",
            id,contractId,req(b,"visitedOn"),b.get("summary"),actor(a));
        long visitId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        for(Long employee:employees) jdbc.update("INSERT INTO erp_maintenance_visit_employee(visit_id,employee_id) VALUES(?,?)",visitId,employee);
        audit(a,"maintenance.visit.created","erp_maintenance_visit",visitId,"维保现场记录");
        return one("SELECT * FROM erp_maintenance_visit WHERE id=?",visitId);
    }

    @GetMapping("/projects/{id}/service-visits") @PreAuthorize("hasAuthority('service:read')")
    public List<Map<String,Object>> serviceVisits(@PathVariable long id) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        return jdbc.queryForList("""
          SELECT v.*,
            (SELECT GROUP_CONCAT(e.full_name ORDER BY e.full_name SEPARATOR '、')
             FROM erp_service_visit_employee ve JOIN erp_employee e ON e.id=ve.employee_id WHERE ve.visit_id=v.id) employee_names
          FROM erp_service_visit v WHERE v.project_id=? ORDER BY v.visited_at DESC,v.id DESC
          """,id);
    }
    @PostMapping("/projects/{id}/service-visits") @PreAuthorize("hasAuthority('service:write')") @Transactional
    public Map<String,Object> createServiceVisit(@PathVariable long id,@RequestBody Map<String,Object> b,Authentication a) {
        one("SELECT id FROM erp_project WHERE id=?",id);
        String type=val(b,"visitType","onsite");
        if(!Set.of("onsite","remote","meeting","recheck").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"现场服务类型无效");
        List<Long> employees=employeeIds(b.get("employeeIds"));
        if(employees.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"至少选择一名现场执行人员");
        jdbc.update("INSERT INTO erp_service_visit(project_id,visit_no,visit_type,visited_at,summary,follow_up,created_by) VALUES(?,?,?,?,?,?,?)",
            id,req(b,"visitNo"),type,req(b,"visitedAt"),req(b,"summary"),b.get("followUp"),actor(a));
        long visitId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        for(Long employee:employees) jdbc.update("INSERT INTO erp_service_visit_employee(visit_id,employee_id) VALUES(?,?)",visitId,employee);
        audit(a,"service.visit.created","erp_service_visit",visitId,req(b,"visitNo")+" "+type);
        return one("SELECT * FROM erp_service_visit WHERE id=?",visitId);
    }
}
