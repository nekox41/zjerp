package top.aameow.erpnext.business;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Set;

/** Invoked only from a transactional contract create or attachment operation. */
final class ContractProjectCreation {
    private ContractProjectCreation() {}
    static void requireType(String type){
        if(!Set.of("maintenance","fire_inspection","fire_assessment","fire_evaluation","other").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目服务类型无效");
    }
    static Long createIfUnlinked(JdbcTemplate jdbc,long contractId,long actor){
        Map<String,Object> c=jdbc.queryForMap("SELECT * FROM erp_contract WHERE id=? FOR UPDATE",contractId);
        Long existing=jdbc.queryForObject("SELECT MIN(project_id) FROM erp_contract_project WHERE contract_id=?",Long.class,contractId);
        if(existing!=null)return existing;
        String type=c.get("service_type").toString();
        requireType(type);
        if("maintenance".equals(type)&&(c.get("starts_on")==null||c.get("ends_on")==null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"维保合同必须填写起止日期");
        jdbc.update("""
            INSERT INTO erp_project(project_no,name,contract_id,customer_id,service_type,auto_from_contract,planned_start,planned_end)
            VALUES(?,?,?,?,?,TRUE,?,?)
            """,c.get("contract_no"),c.get("title"),contractId,c.get("customer_id"),type,c.get("starts_on"),c.get("ends_on"));
        long id=jdbc.queryForObject("SELECT lastval()",Long.class);
        jdbc.update("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(?,?,'service')",contractId,id);
        jdbc.update("INSERT INTO erp_project_log(project_id,actor_identity_id,log_type,content) VALUES(?,?,'created',?)",id,actor,"由新合同自动创建项目");
        if("maintenance".equals(type))jdbc.update("INSERT INTO erp_maintenance_project(project_id,updated_by) VALUES(?,?)",id,actor);
        return id;
    }
}
