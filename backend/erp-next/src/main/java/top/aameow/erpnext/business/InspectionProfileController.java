package top.aameow.erpnext.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;

/** Working data based on Issue #8 §12, never a certified finding or signed report. */
@RestController
@RequestMapping("/api/service-workflow/work-packages/{id}/inspection-profile")
public class InspectionProfileController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ReportDocxPhotoInserter systems;
    public InspectionProfileController(JdbcTemplate jdbc,ObjectMapper mapper,ReportDocxPhotoInserter systems){
        this.jdbc=jdbc;this.mapper=mapper;this.systems=systems;
    }
    private static final Map<String,Set<String>> FIELDS=Map.of(
        "general",Set.of("projectName","address","projectCategory","constructionUnit","constructionQualification","designUnit","designQualification","contractor","contractorQualification","supervisionUnit","supervisionQualification","totalArea","commissioningUnit","contact","phone","commissionedOn","inspectionDates","reportNo","reportDate","inspectionCategory","overview","introduction","scope","basis"),
        "buildings",Set.of("name","structure","use","fireResistance","aboveGroundFloors","belowGroundFloors","height","length","footprint","aboveGroundArea","belowGroundArea"),
        "facilities",Set.of("system","name","location","servedAreas","description"),
        "records",Set.of("system","item","standard","location","result","judgment"),
        "instruments",Set.of("name","model","serialNo","calibrationExpiry"),
        "summaries",Set.of("system","judgment","notes"),
        "conclusion",Set.of("text")
    );
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private void scope(long id,boolean lock){
        if(jdbc.queryForList("SELECT id FROM erp_service_work_package WHERE id=?"+(lock?" FOR UPDATE":""),id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"服务工作包不存在");
    }
    private void validateObject(String section,JsonNode row){
        if(!row.isObject())throw bad("无效数据结构："+section);
        row.fields().forEachRemaining(e->{
            if(!FIELDS.get(section).contains(e.getKey())||!e.getValue().isTextual()||e.getValue().textValue().length()>12000)
                throw bad("无效字段或内容超长："+section+"."+e.getKey());
            if("system".equals(e.getKey())&&!e.getValue().textValue().isBlank()&&!systems.supportedSystems().contains(e.getValue().textValue()))
                throw bad("无效消防系统分类");
        });
    }
    private void validate(JsonNode content){
        if(content==null||!content.isObject()||content.toString().length()>2_000_000)throw bad("检测资料为空或超过大小限制");
        content.fields().forEachRemaining(e->{
            String key=e.getKey();JsonNode value=e.getValue();
            if(!FIELDS.containsKey(key))throw bad("未知检测资料分区："+key);
            if("general".equals(key)||"conclusion".equals(key)){validateObject(key,value);return;}
            int limit="records".equals(key)?3000:300;
            if(!value.isArray()||value.size()>limit)throw bad("无效记录数量："+key);
            value.forEach(row->validateObject(key,row));
        });
    }
    private Map<String,Object> read(long id,Integer version){
        var rows=jdbc.queryForList("SELECT version_no,content_json,created_by,created_at FROM erp_inspection_profile_version WHERE work_package_id=?"+(version==null?" ORDER BY version_no DESC LIMIT 1":" AND version_no=?"),version==null?new Object[]{id}:new Object[]{id,version});
        if(rows.isEmpty()){
            if(version!=null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料版本不存在");
            return Map.of("versionNo",0,"content",Map.of());
        }
        var row=rows.get(0);
        try{return Map.of("versionNo",row.get("version_no"),"content",mapper.readTree(row.get("content_json").toString()),"createdBy",row.get("created_by"),"createdAt",row.get("created_at"));}
        catch(Exception e){throw new IllegalStateException("检测资料读取失败",e);}
    }
    @GetMapping @PreAuthorize("hasAuthority('report:read') and hasAuthority('service:read')")
    public Map<String,Object> get(@PathVariable long id){scope(id,false);return read(id,null);}
    @GetMapping("/versions") @PreAuthorize("hasAuthority('report:read') and hasAuthority('service:read')")
    public List<Map<String,Object>> versions(@PathVariable long id){
        scope(id,false);return jdbc.queryForList("SELECT version_no,created_by,created_at FROM erp_inspection_profile_version WHERE work_package_id=? ORDER BY version_no DESC LIMIT 200",id);
    }
    @GetMapping("/versions/{version}") @PreAuthorize("hasAuthority('report:read') and hasAuthority('service:read')")
    public Map<String,Object> version(@PathVariable long id,@PathVariable int version){scope(id,false);return read(id,version);}
    public record SaveRequest(Integer expectedVersionNo,JsonNode content){}
    @PutMapping @PreAuthorize("hasAuthority('report:write') and hasAuthority('report:read') and hasAuthority('service:read')") @Transactional
    public Map<String,Object> save(@PathVariable long id,@RequestBody SaveRequest request,Authentication a){
        scope(id,true);validate(request.content());
        int latest=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM erp_inspection_profile_version WHERE work_package_id=?",Integer.class,id);
        if(request.expectedVersionNo()==null||request.expectedVersionNo()!=latest)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"资料版本已更新，请刷新后重试");
        long actor=((CurrentUser)a.getPrincipal()).id();
        jdbc.update("INSERT INTO erp_inspection_profile_version(work_package_id,version_no,content_json,created_by) VALUES(?,?,?,?)",id,latest+1,request.content().toString(),actor);
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('version',?))",
            actor,"inspection.profile.saved","erp_service_work_package",Long.toString(id),UUID.randomUUID().toString(),latest+1);
        return read(id,latest+1);
    }
}
