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
        "general",Set.of("projectName","address","projectCategory","constructionUnit","constructionQualification","designUnit","designQualification","contractor","contractorQualification","supervisionUnit","supervisionQualification","totalArea","commissioningUnit","contact","phone","commissionedOn","inspectionDates","reportNo","reportDate","inspectionCategory","overview","introduction","scope","basis","designDate","designReviewDate","completionDate","acceptanceDate","renovationDate","inspectionDate","applicabilityNotes"),
        "buildings",Set.of("name","structure","use","fireResistance","aboveGroundFloors","belowGroundFloors","height","length","footprint","aboveGroundArea","belowGroundArea","constructionYear","designReviewDate","acceptanceDate","renovationDate"),
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
    private void validateObject(String section,JsonNode row,Set<String> allowedSystems){
        if(!row.isObject())throw bad("无效数据结构："+section);
        row.fields().forEachRemaining(e->{
            if(!FIELDS.get(section).contains(e.getKey())||!e.getValue().isTextual()||e.getValue().textValue().length()>12000)
                throw bad("无效字段或内容超长："+section+"."+e.getKey());
            if("system".equals(e.getKey())&&!e.getValue().textValue().isBlank()&&!allowedSystems.contains(e.getValue().textValue()))
                throw bad("无效消防系统分类");
        });
    }
    private void validate(JsonNode content){
        if(content==null||!content.isObject()||content.toString().length()>2_000_000)throw bad("检测资料为空或超过大小限制");
        Set<String> allowedSystems=new LinkedHashSet<>(systems.supportedSystems());
        allowedSystems.addAll(jdbc.query("SELECT DISTINCT system_name FROM erp_inspection_template WHERE template_kind='system'",(rs,i)->rs.getString(1)));
        content.fields().forEachRemaining(e->{
            String key=e.getKey();JsonNode value=e.getValue();
            if(!FIELDS.containsKey(key))throw bad("未知检测资料分区："+key);
            if("general".equals(key)||"conclusion".equals(key)){validateObject(key,value,allowedSystems);return;}
            int limit="records".equals(key)?3000:300;
            if(!value.isArray()||value.size()>limit)throw bad("无效记录数量："+key);
            value.forEach(row->validateObject(key,row,allowedSystems));
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
    public record BasisChoice(Long id,String scope,String reason){}
    public record SaveRequest(Integer expectedVersionNo,JsonNode content,List<BasisChoice> basisChoices){}
    private com.fasterxml.jackson.databind.node.ObjectNode withBasis(SaveRequest request,JsonNode previous){
        if(request.content()==null||!request.content().isObject())throw bad("检测资料无效");
        var result=(com.fasterxml.jackson.databind.node.ObjectNode)request.content().deepCopy();
        // The client can only supply IDs and applicability explanations, never bibliographic snapshots.
        result.remove("basisReferences");
        validate(result);
        var oldRefs=previous.path("basisReferences");
        if(request.basisChoices()==null){
            if(oldRefs.isArray()){
                result.set("basisReferences",oldRefs.deepCopy());
                if(!oldRefs.isEmpty())result.withObject("/general").put("basis",previous.path("general").path("basis").asText());
            }
            return result;
        }
        if(request.basisChoices().size()>100)throw bad("检测依据不得超过100项");
        var references=mapper.createArrayNode();Set<String> keys=new HashSet<>();List<String> lines=new ArrayList<>();
        for(BasisChoice choice:request.basisChoices()){
            if(choice==null||choice.id()==null||choice.id()<=0||choice.scope()==null||choice.scope().isBlank()||choice.scope().length()>500||choice.reason()==null||choice.reason().isBlank()||choice.reason().length()>2000)throw bad("每项依据需填写适用范围和选用说明");
            var rows=jdbc.queryForList("SELECT s.id,s.standard_no,s.edition,s.title,s.category,s.publication_year,s.effective_on,s.withdrawn_on,s.validity,s.source_url,s.access_url,s.notes,s.revision_no,s.verified_by,s.verified_at,s.file_id,f.sha256 file_sha256 FROM erp_fire_standard s LEFT JOIN erp_file_object f ON f.id=s.file_id WHERE s.id=? AND s.active=1 AND s.verified=1",choice.id());
            com.fasterxml.jackson.databind.node.ObjectNode ref=null;
            if(rows.size()==1){
                ref=mapper.createObjectNode();
                var snapshot=ref;
                rows.get(0).forEach((k,v)->{if(v==null)snapshot.putNull(k);else if(v instanceof Number n)snapshot.put(k,n.longValue());else snapshot.put(k,v.toString());});
            }else if(oldRefs.isArray()){
                for(JsonNode old:oldRefs)if(old.path("id").asLong()==choice.id()&&old.path("scope").asText().equals(choice.scope().trim())&&old.path("reason").asText().equals(choice.reason().trim())){
                    ref=(com.fasterxml.jackson.databind.node.ObjectNode)old.deepCopy();break;
                }
            }
            if(ref==null)throw bad("所选规范未核验或已停用；停用版本只允许原样保留历史引用："+choice.id());
            String key=ref.path("standard_no").asText().replaceAll("\\s+","")+"|"+ref.path("edition").asText();
            if(!keys.add(key))throw bad("同一标准版本不得重复引用");
            ref.put("scope",choice.scope().trim());ref.put("reason",choice.reason().trim());references.add(ref);
            String edition=ref.path("edition").asText("");
            lines.add((lines.size()+1)+".《"+ref.path("title").asText()+"》（"+ref.path("standard_no").asText()+(edition.isBlank()?"":"（"+edition+"）")+"）；");
        }
        if(!lines.isEmpty()||oldRefs.isArray()&&!oldRefs.isEmpty()){
            var general=result.withObject("/general");
            general.put("basis",String.join("\n",lines));
        }
        result.set("basisReferences",references);
        if(result.toString().length()>2_000_000)throw bad("检测资料与引用快照超过大小限制");
        return result;
    }
    @PutMapping @PreAuthorize("hasAuthority('report:write') and hasAuthority('report:read') and hasAuthority('service:read')") @Transactional
    public Map<String,Object> save(@PathVariable long id,@RequestBody SaveRequest request,Authentication a){
        scope(id,true);
        int latest=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM erp_inspection_profile_version WHERE work_package_id=?",Integer.class,id);
        if(request.expectedVersionNo()==null||request.expectedVersionNo()!=latest)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"资料版本已更新，请刷新后重试");
        JsonNode previous=latest==0?mapper.createObjectNode():(JsonNode)read(id,latest).get("content");
        var data=withBasis(request,previous);
        long actor=((CurrentUser)a.getPrincipal()).id();
        jdbc.update("INSERT INTO erp_inspection_profile_version(work_package_id,version_no,content_json,created_by) VALUES(?,?,?,?)",id,latest+1,data.toString(),actor);
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('version',?))",
            actor,"inspection.profile.saved","erp_service_work_package",Long.toString(id),UUID.randomUUID().toString(),latest+1);
        return read(id,latest+1);
    }
}
