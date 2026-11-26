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

    @GetMapping("/partners") @PreAuthorize("hasAuthority('partner:read')")
    public List<Map<String,Object>> partners(@RequestParam(defaultValue="") String q){
        String keyword=q==null?"":q.trim();
        return jdbc.queryForList("SELECT p.*,(SELECT COUNT(*) FROM erp_contract_partner cp WHERE cp.partner_id=p.id) contract_count FROM erp_partner p WHERE ?='' OR p.name ILIKE CONCAT('%',?,'%') OR COALESCE(p.contact,'') ILIKE CONCAT('%',?,'%') OR COALESCE(p.region,'') ILIKE CONCAT('%',?,'%') ORDER BY p.name,p.id",keyword,keyword,keyword,keyword);
    }
    @GetMapping("/partners/{id}") @PreAuthorize("hasAuthority('partner:read')")
    public Map<String,Object> partner(@PathVariable long id){
        Map<String,Object> p=new LinkedHashMap<>(one("SELECT * FROM erp_partner WHERE id=?",id));
        p.put("contracts",jdbc.queryForList("SELECT c.id,c.contract_no,c.title,c.service_type,c.amount,c.signed_on,c.starts_on,c.ends_on,cp.share_percent,c.status FROM erp_contract_partner cp JOIN erp_contract c ON c.id=cp.contract_id WHERE cp.partner_id=? ORDER BY c.created_at DESC,c.id DESC",id));
        return p;
    }
    @PostMapping("/partners") @PreAuthorize("hasAuthority('partner:write')") @Transactional
    public Map<String,Object> createPartner(@RequestBody Map<String,Object>b){
        String name=req(b,"name");
        String contact=optional(b,"contact"),region=optional(b,"region");
        validatePartnerFields(name,contact,region);
        jdbc.update("INSERT INTO erp_partner(name,contact,region) VALUES(?,?,?)",name,contact,region);
        long id=jdbc.queryForObject("SELECT lastval()",Long.class);
        return partner(id);
    }
    @PutMapping("/partners/{id}") @PreAuthorize("hasAuthority('partner:write')") @Transactional
    public Map<String,Object> updatePartner(@PathVariable long id,@RequestBody Map<String,Object>b){
        one("SELECT id FROM erp_partner WHERE id=?",id);
        String name=req(b,"name");
        String contact=optional(b,"contact"),region=optional(b,"region");
        validatePartnerFields(name,contact,region);
        jdbc.update("UPDATE erp_partner SET name=?,contact=?,region=? WHERE id=?",name,contact,region,id);
        return partner(id);
    }
    private String optional(Map<String,Object>b,String key){
        Object value=b.get(key);
        if(value==null)return null;
        String text=value.toString().trim();
        return text.isEmpty()?null:text;
    }
    private void validatePartnerFields(String name,String contact,String region){
        if(name.length()>240)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"中间人姓名不能超过240个字符");
        if(contact!=null&&contact.length()>240)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"联系方式不能超过240个字符");
        if(region!=null&&region.length()>160)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"地区不能超过160个字符");
    }
    @PostMapping("/contracts/{id}/partners") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> linkPartner(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_contract WHERE id=? FOR UPDATE",id);long partner=Long.parseLong(req(b,"partnerId"));one("SELECT id FROM erp_partner WHERE id=?",partner);
        java.math.BigDecimal share=new java.math.BigDecimal(req(b,"sharePercent"));
        if(share.signum()<0||share.compareTo(java.math.BigDecimal.valueOf(100))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分成比例必须在 0 到 100 之间");
        java.math.BigDecimal existing=jdbc.queryForObject("SELECT COALESCE(SUM(share_percent),0) FROM erp_contract_partner WHERE contract_id=? AND partner_id<>?",java.math.BigDecimal.class,id,partner);
        if(existing.add(share).compareTo(java.math.BigDecimal.valueOf(100))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"该合同合作方分成比例合计不能超过 100%");
        jdbc.update("INSERT INTO erp_contract_partner(contract_id,partner_id,share_percent,notes) VALUES(?,?,?,?) ON CONFLICT (contract_id,partner_id) DO UPDATE SET share_percent=EXCLUDED.share_percent,notes=EXCLUDED.notes",id,partner,share,b.get("notes"));
        jdbc.update("UPDATE erp_contract SET signing_source='partner',partner_share_percent=(SELECT COALESCE(SUM(share_percent),0) FROM erp_contract_partner WHERE contract_id=?) WHERE id=?",id,id);
        
        return one("SELECT * FROM erp_contract_partner WHERE contract_id=? AND partner_id=?",id,partner);
    }
    @DeleteMapping("/contracts/{id}/partners/{partnerId}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> unlinkPartner(@PathVariable long id,@PathVariable long partnerId,Authentication a){
        one("SELECT id FROM erp_contract WHERE id=? FOR UPDATE",id);
        int removed=jdbc.update("DELETE FROM erp_contract_partner WHERE contract_id=? AND partner_id=?",id,partnerId);
        if(removed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"合同合作关系不存在");
        java.math.BigDecimal remaining=jdbc.queryForObject(
            "SELECT COALESCE(SUM(share_percent),0) FROM erp_contract_partner WHERE contract_id=?",
            java.math.BigDecimal.class,id);
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM erp_contract_partner WHERE contract_id=?",Integer.class,id);
        jdbc.update("UPDATE erp_contract SET signing_source=?,partner_share_percent=? WHERE id=?",
            count!=null&&count>0?"partner":"direct",remaining,id);
        
        return one("SELECT id,signing_source,partner_share_percent FROM erp_contract WHERE id=?",id);
    }

    @GetMapping("/contracts/{id}/partners") @PreAuthorize("hasAuthority('contract:read')")
    public List<Map<String,Object>> contractPartners(@PathVariable long id){
        return jdbc.queryForList("SELECT p.*,cp.share_percent,cp.notes FROM erp_contract_partner cp JOIN erp_partner p ON p.id=cp.partner_id WHERE cp.contract_id=? ORDER BY p.name",id);
    }
    @PostMapping("/contracts/{id}/projects") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> linkProject(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> c=one("SELECT id,customer_id,service_type FROM erp_contract WHERE id=? FOR UPDATE",id);long project=Long.parseLong(req(b,"projectId"));
        Map<String,Object> p=one("SELECT id,customer_id,contract_id,service_type FROM erp_project WHERE id=? FOR UPDATE",project);
        if(!Objects.equals(((Number)c.get("customer_id")).longValue(),((Number)p.get("customer_id")).longValue()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同与项目必须属于同一客户");
        String type=b.get("relationType")==null?"service":b.get("relationType").toString();
        if(!Set.of("service","renewal","supplement","other").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效合同项目关系");
        jdbc.update("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(?,?,?) ON CONFLICT (contract_id,project_id) DO UPDATE SET relation_type=EXCLUDED.relation_type",id,project,type);
        if(p.get("contract_id")==null)jdbc.update("UPDATE erp_project SET contract_id=?,service_type=CASE WHEN service_type='other' THEN ? ELSE service_type END WHERE id=?",id,c.get("service_type"),project);
        
        return one("SELECT * FROM erp_contract_project WHERE contract_id=? AND project_id=?",id,project);
    }
    @DeleteMapping("/contracts/{id}/projects/{projectId}") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> unlinkProject(@PathVariable long id,@PathVariable long projectId,Authentication a){
        Map<String,Object> contract=one("SELECT id,status FROM erp_contract WHERE id=? FOR UPDATE",id);
        if(!Set.of("draft","pending").contains(contract.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"已履行、已完成或已终止的合同需保留项目历史，不能解除关联");
        one("SELECT id FROM erp_project WHERE id=? FOR UPDATE",projectId);
        Integer references=jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM erp_maintenance_project WHERE project_id=? AND current_contract_id=?)
                 + (SELECT COUNT(*) FROM erp_maintenance_visit WHERE project_id=? AND contract_id=?)
                 + (SELECT COUNT(*) FROM erp_invoice WHERE project_id=? AND contract_id=?)
                 + (SELECT COUNT(*) FROM erp_maintenance_card WHERE project_id=? AND contract_id=?)
                 + (SELECT COUNT(*) FROM erp_maintenance_material_archive WHERE project_id=? AND contract_id=?)
                 + (SELECT COUNT(*) FROM erp_maintenance_contract_state WHERE project_id=? AND contract_id=?)
            """,Integer.class,projectId,id,projectId,id,projectId,id,projectId,id,projectId,id,projectId,id);
        if(references!=null&&references>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"已有业务记录引用该合同项目关系，不能解除");
        if(jdbc.update("DELETE FROM erp_contract_project WHERE contract_id=? AND project_id=?",id,projectId)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"合同项目关系不存在");
        Long replacement=jdbc.queryForObject("SELECT MIN(contract_id) FROM erp_contract_project WHERE project_id=?",Long.class,projectId);
        jdbc.update("UPDATE erp_project SET contract_id=?,auto_from_contract=FALSE WHERE id=? AND contract_id=?",replacement,projectId,id);
        
        return Map.of("removed",true);
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
        
        return one("SELECT p.*,m.full_name manager_name,t.full_name technical_lead_name FROM erp_project p LEFT JOIN erp_employee m ON m.id=p.manager_employee_id LEFT JOIN erp_employee t ON t.id=p.technical_lead_employee_id WHERE p.id=?",id);
    }
    private void validateEngineer(long id){
        Map<String,Object> e=one("SELECT id,employee_type,status FROM erp_employee WHERE id=?",id);
        if(!"active".equals(e.get("status"))||!"engineer".equals(e.get("employee_type")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目负责人和技术负责人必须是有效的工程师员工");
        Integer q=jdbc.queryForObject("SELECT COUNT(*) FROM erp_employee WHERE id=? AND NULLIF(BTRIM(certificate_no),'') IS NOT NULL",Integer.class,id);
        if(q==null||q==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"工程师员工必须登记证书编号");
    }

    @PostMapping("/projects/{id}/maintenance-renewals")
    @PreAuthorize("hasAuthority('contract:write') and hasAuthority('maintenance:write')")
    @Transactional
    public Map<String,Object> renewMaintenance(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object> project=one("SELECT id,customer_id FROM erp_project WHERE id=?",id);
        Integer previous=jdbc.queryForObject("""
            SELECT COUNT(*) FROM erp_contract_project cp JOIN erp_contract c ON c.id=cp.contract_id
            WHERE cp.project_id=? AND c.service_type='maintenance'
            """,Integer.class,id);
        if(previous==null||previous==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"此项目尚无维保合同，首次签约请使用普通合同流程");

        String contractNo=req(b,"contractNo"),title=req(b,"title"),startsOn=req(b,"startsOn"),endsOn=req(b,"endsOn");
        java.time.LocalDate start,end;
        try{
            start=java.time.LocalDate.parse(startsOn);
            end=java.time.LocalDate.parse(endsOn);
        }catch(java.time.format.DateTimeParseException ex){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同起止日期格式必须为 YYYY-MM-DD");
        }
        if(end.isBefore(start))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同结束日期不能早于开始日期");
        java.math.BigDecimal amount;
        try{amount=new java.math.BigDecimal(Objects.toString(b.get("amount"),"0"));}
        catch(NumberFormatException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额格式错误");}
        if(amount.signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同金额不能为负数");
        Object signedOn=b.get("signedOn");
        if(signedOn!=null&&!signedOn.toString().isBlank()){
            try{java.time.LocalDate.parse(signedOn.toString());}
            catch(java.time.format.DateTimeParseException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"签订日期格式错误");}
        }
        jdbc.update("""
            INSERT INTO erp_contract(contract_no,title,customer_id,contract_type,service_type,amount,currency,signed_on,starts_on,ends_on,status,notes,signing_source,partner_share_percent)
            VALUES(?,?,?,'renewal','maintenance',?,'CNY',?,?,?,'draft',?,'direct',0)
            """,contractNo,title,project.get("customer_id"),amount,
            signedOn==null||signedOn.toString().isBlank()?null:signedOn,start,end,b.get("notes"));
        long contractId=jdbc.queryForObject("SELECT lastval()",Long.class);
        jdbc.update("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(?,?,'renewal')",contractId,id);
        
        return one("SELECT * FROM erp_contract WHERE id=?",contractId);
    }

    @GetMapping("/projects/{id}/maintenance") @PreAuthorize("hasAuthority('maintenance:read')")
    public Map<String,Object> maintenance(@PathVariable long id){
        Map<String,Object> p=one("SELECT * FROM erp_project WHERE id=?",id);
        Map<String,Object> state=jdbc.queryForList("SELECT m.*,c.contract_no current_contract_no,c.starts_on current_starts_on,c.ends_on current_ends_on FROM erp_maintenance_project m LEFT JOIN erp_contract c ON c.id=m.current_contract_id WHERE m.project_id=?",id).stream().findFirst().orElse(Map.of("project_id",id));
        state=new LinkedHashMap<>(state);
        state.put("visits",jdbc.queryForList("SELECT v.*,c.contract_no,(SELECT string_agg(e.full_name, '、' ORDER BY e.full_name) FROM erp_maintenance_visit_staff vs JOIN erp_employee e ON e.id=vs.employee_id WHERE vs.visit_id=v.id) staff_names FROM erp_maintenance_visit v LEFT JOIN erp_contract c ON c.id=v.contract_id WHERE v.project_id=? ORDER BY v.visit_date DESC",id));
        state.put("contractHistory",jdbc.queryForList("""
            SELECT c.id,c.contract_no,c.title,c.starts_on,c.ends_on,c.status,
                   s.honeybee_contract_uploaded,s.honeybee_project_created,s.project_materials_completed,s.public_card_status,s.notes,
                   s.updated_at state_updated_at
            FROM erp_contract_project cp JOIN erp_contract c ON c.id=cp.contract_id
            LEFT JOIN erp_maintenance_contract_state s ON s.contract_id=cp.contract_id AND s.project_id=cp.project_id
            WHERE cp.project_id=? AND c.service_type='maintenance' ORDER BY c.signed_on DESC,c.id DESC
            """,id));
        return state;
    }
    @PutMapping("/projects/{id}/maintenance") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> updateMaintenance(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_project WHERE id=? FOR UPDATE",id);
        Map<String,Object> projectState=jdbc.queryForList("SELECT * FROM erp_maintenance_project WHERE project_id=?",id)
            .stream().findFirst().orElse(Map.of());
        Long oldCurrent=opt(projectState,"current_contract_id");
        Long current=b.containsKey("currentContractId")?opt(b,"currentContractId"):oldCurrent;
        if(current!=null){
            Map<String,Object> currentContract=one("SELECT c.id,c.status,c.starts_on,c.ends_on FROM erp_contract c JOIN erp_contract_project cp ON cp.contract_id=c.id WHERE c.id=? AND cp.project_id=? AND c.service_type='maintenance'",current,id);
            if(!"active".equals(currentContract.get("status")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前有效维保合同必须处于履行中状态");
            if(currentContract.get("starts_on")!=null&&currentContract.get("ends_on")!=null)
                jdbc.update("UPDATE erp_project SET planned_start=?,planned_end=? WHERE id=?",
                    currentContract.get("starts_on"),currentContract.get("ends_on"),id);
        }
        // The state of an unselected annual contract must never be copied from the previous year.
        Map<String,Object> previous=current==null
            ?(oldCurrent==null?projectState:Map.of())
            :jdbc.queryForList("SELECT * FROM erp_maintenance_contract_state WHERE project_id=? AND contract_id=?",id,current)
                 .stream().findFirst().orElse(Map.of());
        String card=b.get("publicCardStatus")==null?Objects.toString(previous.get("public_card_status"),"not_started"):b.get("publicCardStatus").toString();
        boolean uploaded=b.containsKey("honeybeeContractUploaded")
            ?Boolean.parseBoolean(Objects.toString(b.get("honeybeeContractUploaded"),"false"))
            :Boolean.TRUE.equals(previous.get("honeybee_contract_uploaded"))||Integer.valueOf(1).equals(previous.get("honeybee_contract_uploaded"));
        boolean created=b.containsKey("honeybeeProjectCreated")
            ?Boolean.parseBoolean(Objects.toString(b.get("honeybeeProjectCreated"),"false"))
            :Boolean.TRUE.equals(previous.get("honeybee_project_created"))||Integer.valueOf(1).equals(previous.get("honeybee_project_created"));
        boolean materials=b.containsKey("projectMaterialsCompleted")
            ?Boolean.parseBoolean(Objects.toString(b.get("projectMaterialsCompleted"),"false"))
            :Boolean.TRUE.equals(previous.get("project_materials_completed"))||Integer.valueOf(1).equals(previous.get("project_materials_completed"));
        if(!Set.of("not_started","preparing","sent","delivered").contains(card))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效公示卡状态");
        Object notes=b.containsKey("notes")?b.get("notes"):previous.get("notes");
        if(current!=null)jdbc.update("""
            INSERT INTO erp_maintenance_contract_state(contract_id,project_id,honeybee_contract_uploaded,honeybee_project_created,project_materials_completed,public_card_status,notes,updated_by)
            VALUES(?,?,?,?,?,?,?,?) ON CONFLICT (contract_id,project_id) DO UPDATE SET honeybee_contract_uploaded=EXCLUDED.honeybee_contract_uploaded,
            honeybee_project_created=EXCLUDED.honeybee_project_created,project_materials_completed=EXCLUDED.project_materials_completed,
            public_card_status=EXCLUDED.public_card_status,notes=EXCLUDED.notes,updated_by=EXCLUDED.updated_by
            """,current,id,uploaded,created,materials,card,notes,actor(a));
        jdbc.update("INSERT INTO erp_maintenance_project(project_id,honeybee_contract_uploaded,honeybee_project_created,project_materials_completed,public_card_status,current_contract_id,notes,updated_by) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT (project_id) DO UPDATE SET honeybee_contract_uploaded=EXCLUDED.honeybee_contract_uploaded,honeybee_project_created=EXCLUDED.honeybee_project_created,project_materials_completed=EXCLUDED.project_materials_completed,public_card_status=EXCLUDED.public_card_status,current_contract_id=EXCLUDED.current_contract_id,notes=EXCLUDED.notes,updated_by=EXCLUDED.updated_by",
            id,uploaded,created,materials,card,current,notes,actor(a));
        
        return maintenance(id);
    }
    @PostMapping("/projects/{id}/maintenance-visits") @PreAuthorize("hasAuthority('maintenance:write')") @Transactional
    public Map<String,Object> addMaintenanceVisit(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_project WHERE id=?",id);Long contract=opt(b,"contractId");
        if(contract!=null)one("SELECT c.id FROM erp_contract c JOIN erp_contract_project cp ON cp.contract_id=c.id WHERE c.id=? AND cp.project_id=? AND c.service_type='maintenance'",contract,id);
        jdbc.update("INSERT INTO erp_maintenance_visit(project_id,contract_id,visit_date,notes,created_by) VALUES(?,?,?,?,?)",id,contract,req(b,"visitDate"),b.get("notes"),actor(a));
        long visit=jdbc.queryForObject("SELECT lastval()",Long.class);
        Object staff=b.get("employeeIds");
        if(staff instanceof List<?> ids)for(Object value:ids){long employee=Long.parseLong(value.toString());one("SELECT id FROM erp_employee WHERE id=?",employee);jdbc.update("INSERT INTO erp_maintenance_visit_staff(visit_id,employee_id) VALUES(?,?)",visit,employee);}
        
        return one("SELECT * FROM erp_maintenance_visit WHERE id=?",visit);
    }
    @PostMapping("/contracts/{id}/files") @PreAuthorize("hasAuthority('contract:write')") @Transactional
    public Map<String,Object> addContractFile(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        one("SELECT id FROM erp_contract WHERE id=? FOR UPDATE",id);long file=Long.parseLong(req(b,"fileId"));
        Map<String,Object> f=one("SELECT id,uploaded_by FROM erp_file_object WHERE id=?",file);
        if(((Number)f.get("uploaded_by")).longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的文件");
        String type=b.get("fileType")==null?"contract":b.get("fileType").toString();
        if(!Set.of("contract","maintenance_card","project_material","other").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效合同文件类型");
        jdbc.update("INSERT INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(?,?,?,?,?)",id,file,type,b.get("title"),actor(a));
        long record=jdbc.queryForObject("SELECT lastval()",Long.class);
        if("contract".equals(type))ContractProjectCreation.createIfUnlinked(jdbc,id,actor(a));
        return one("SELECT cf.*,f.original_name FROM erp_contract_file cf JOIN erp_file_object f ON f.id=cf.file_id WHERE cf.id=?",record);
    }
}
