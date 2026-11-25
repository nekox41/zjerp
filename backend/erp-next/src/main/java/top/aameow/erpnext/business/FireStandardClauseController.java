package top.aameow.erpnext.business;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import top.aameow.erpnext.auth.CurrentUser;

@RestController
@RequestMapping("/api/fire-standard-clauses")
public class FireStandardClauseController {
    private final JdbcTemplate jdbc;
    public FireStandardClauseController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private static String field(Map<String,Object>b,String key,int max){
        if(!(b.get(key) instanceof String s)||s.isBlank()||s.length()>max||s.contains("{{erp."))throw bad("无效字段："+key);
        return s.trim();
    }
    private static long id(Object value){try{long n=Long.parseLong(value.toString());if(n>0)return n;}catch(RuntimeException ignored){}throw bad("无效ID");}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    @GetMapping @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:standard:write') or hasAuthority('report:template:write')")
    public Map<String,Object> list(Authentication a,@RequestParam(required=false) Long standardId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean includeInactive,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="50") int limit){
        if(q.length()>200||offset<0||limit<1||limit>100)throw bad("查询参数无效");
        boolean manager=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("report:standard:write"));
        String like="%"+q.trim()+"%";
        var rows=jdbc.queryForList("SELECT c.*,s.standard_no,s.edition,s.title,s.source_url,s.access_url,s.active standard_active,s.verified FROM erp_fire_standard_clause c JOIN erp_fire_standard s ON s.id=c.standard_id WHERE (?=1 OR (c.active=1 AND s.active=1 AND s.verified=1)) AND (? IS NULL OR s.id=?) AND (c.clause_no LIKE ? OR c.requirement_summary LIKE ? OR s.standard_no LIKE ? OR s.title LIKE ?) ORDER BY s.standard_no,s.edition,c.clause_no,c.revision_no DESC LIMIT ? OFFSET ?",manager&&includeInactive?1:0,standardId,standardId,like,like,like,like,limit+1,offset);
        return Map.of("rows",rows.size()>limit?rows.subList(0,limit):rows,"hasMore",rows.size()>limit);
    }
    @PostMapping @PreAuthorize("hasAuthority('report:standard:write')") @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object>b,Authentication a){
        long sid=id(b.get("standardId"));
        if(!Boolean.TRUE.equals(b.get("confirmed")))throw bad("须确认已核对指定版本原文、条款位置及摘录或摘要");
        String no=field(b,"clauseNo",80),summary=field(b,"summary",4000),locator=field(b,"locator",500);
        if(jdbc.queryForList("SELECT id FROM erp_fire_standard WHERE id=? AND verified=1 AND active=1 FOR UPDATE",sid).isEmpty())throw bad("只能为已核验且启用的规范版本建立条款");
        int revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM erp_fire_standard_clause WHERE standard_id=? AND clause_no=?",Integer.class,sid,no);
        jdbc.update("INSERT INTO erp_fire_standard_clause(standard_id,clause_no,requirement_summary,source_locator,revision_no,created_by) VALUES(?,?,?,?,?,?)",sid,no,summary,locator,revision,actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        return Map.of("id",id,"revisionNo",revision);
    }
    @PutMapping("/{id}/active") @PreAuthorize("hasAuthority('report:standard:write')") @Transactional
    public Map<String,Object> active(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        if(!(b.get("active") instanceof Boolean active))throw bad("状态无效");
        if(jdbc.queryForList("SELECT id FROM erp_fire_standard_clause WHERE id=? FOR UPDATE",id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"条款不存在");
        jdbc.update("UPDATE erp_fire_standard_clause SET active=? WHERE id=?",active,id);
        return Map.of("id",id,"active",active);
    }
}
