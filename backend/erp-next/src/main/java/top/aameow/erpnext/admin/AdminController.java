package top.aameow.erpnext.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.AuthService;
import top.aameow.erpnext.auth.CurrentUser;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('identity:write')")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminController(JdbcTemplate jdbc,PasswordEncoder passwords){this.jdbc=jdbc;this.passwords=passwords;}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private void requireAdmin(Authentication a){
        CurrentUser user=(CurrentUser)a.getPrincipal();
        if(!"admin".equals(user.username())||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM erp_identity i JOIN erp_identity_role ir ON ir.identity_id=i.id JOIN erp_role r ON r.id=ir.role_id WHERE i.id=? AND i.username='admin' AND i.status='active' AND r.code='admin')",Boolean.class,user.id())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅超级账户 admin 可管理账号");
    }
    private long role(String role){
        if(!Set.of("employee","manager").contains(Objects.toString(role,"")))throw bad("权限只能选择普通员工或管理人员");
        return jdbc.queryForObject("SELECT id FROM erp_role WHERE code=?",Long.class,role);
    }
    private void lock(){jdbc.queryForObject("SELECT id FROM erp_role WHERE code='admin' FOR UPDATE",Long.class);}
    private String name(Object value,int max){
        if(!(value instanceof String text)||text.isBlank()||text.trim().length()>max)throw bad("姓名或账号无效");
        return text.trim();
    }
    private static final String LIST="SELECT i.id,i.username,i.display_name,i.status,r.code AS role FROM erp_identity i LEFT JOIN erp_identity_role ir ON ir.identity_id=i.id LEFT JOIN erp_role r ON r.id=ir.role_id ";
    @GetMapping("/identities")
    public List<Map<String,Object>> identities(Authentication a){requireAdmin(a);return jdbc.queryForList(LIST+"ORDER BY (i.username='admin') DESC,i.id");}
    @PostMapping("/identities") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> create(@Valid @RequestBody CreateIdentity b,Authentication a){
        lock();requireAdmin(a);
        String username=name(b.username(),100),display=name(b.displayName(),200);
        if("admin".equalsIgnoreCase(username))throw bad("admin 为唯一超级账户");
        long roleId=role(b.role());AuthService.validateNewPassword(b.password());
        try{jdbc.update("INSERT INTO erp_identity(username,password_hash,display_name,status) VALUES(?,?,?,'active')",username,passwords.encode(b.password()),display);}
        catch(DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"账号已存在");}
        long id=jdbc.queryForObject("SELECT lastval()",Long.class);
        jdbc.update("INSERT INTO erp_identity_role(identity_id,role_id) VALUES(?,?)",id,roleId);
        return jdbc.queryForList(LIST+"WHERE i.id=?",id).get(0);
    }
    @PutMapping("/identities/{id}") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        lock();requireAdmin(a);
        if(!Set.of("displayName","role","status","password").containsAll(b.keySet()))throw bad("仅支持姓名、固定权限、状态和密码");
        var rows=jdbc.queryForList(LIST+"WHERE i.id=?",id);
        if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不存在");
        var old=rows.get(0);boolean superAccount="admin".equals(old.get("username"));
        String display=b.containsKey("displayName")?name(b.get("displayName"),200):old.get("display_name").toString();
        String status=Objects.toString(b.get("status"),old.get("status").toString());
        if(!Set.of("active","disabled").contains(status)||superAccount&&!"active".equals(status))throw bad("不能停用超级账户");
        String nextRole=Objects.toString(b.get("role"),Objects.toString(old.get("role"),"employee"));
        if(superAccount&&!"admin".equals(nextRole))throw bad("不能修改超级账户权限");
        long roleId=superAccount?jdbc.queryForObject("SELECT id FROM erp_role WHERE code='admin'",Long.class):role(nextRole);
        jdbc.update("UPDATE erp_identity SET display_name=?,status=? WHERE id=?",display,status,id);
        boolean changed=!Objects.equals(old.get("role"),nextRole);
        if(changed){jdbc.update("DELETE FROM erp_identity_role WHERE identity_id=?",id);jdbc.update("INSERT INTO erp_identity_role(identity_id,role_id) VALUES(?,?)",id,roleId);}
        String password=Objects.toString(b.get("password"),"");
        if(!password.isEmpty()){
            AuthService.validateNewPassword(password);
            jdbc.update("UPDATE erp_identity SET password_hash=?,failed_login_count=0,locked_until=NULL WHERE id=?",passwords.encode(password),id);
        }
        if(!Objects.equals(old.get("status"),status))jdbc.update("UPDATE erp_identity SET failed_login_count=0,locked_until=NULL WHERE id=?",id);
        if(changed||!"active".equals(status)||!password.isEmpty())jdbc.update("UPDATE erp_auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE identity_id=? AND revoked_at IS NULL",id);
        return jdbc.queryForList(LIST+"WHERE i.id=?",id).get(0);
    }
    public record CreateIdentity(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=200) String displayName,@NotBlank @Size(min=12,max=128) String password,@NotBlank String role){}
}
