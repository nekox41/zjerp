package top.aameow.erpnext.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import top.aameow.erpnext.auth.CurrentUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminController(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc=jdbc; this.passwords=passwords; }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('identity:read')")
    public List<Map<String,Object>> roles() {
        return jdbc.queryForList("SELECT id, code, name FROM erp_role ORDER BY name");
    }

    @GetMapping("/identities")
    @PreAuthorize("hasAuthority('identity:read')")
    public List<Map<String,Object>> identities() {
        return jdbc.queryForList("""
            SELECT i.id,i.username,i.display_name,i.status,i.organization_id,
                   GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ',') AS roles
            FROM erp_identity i LEFT JOIN erp_identity_role ir ON ir.identity_id=i.id
            LEFT JOIN erp_role r ON r.id=ir.role_id
            GROUP BY i.id ORDER BY i.id DESC LIMIT 200
            """);
    }

    @PostMapping("/identities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('identity:write')")
    @Transactional
    public Map<String,Object> createIdentity(@Valid @RequestBody CreateIdentity request, Authentication authentication) {
        jdbc.update("INSERT INTO erp_identity(username,password_hash,display_name,organization_id,status) VALUES(?,?,?,?, 'active')",
            request.username(), passwords.encode(request.password()), request.displayName(), request.organizationId());
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        for (Long roleId : request.roleIds()) {
            Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM erp_role WHERE id=?",Integer.class,roleId);
            if(exists==null||exists==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"角色不存在: "+roleId);
            jdbc.update("INSERT INTO erp_identity_role(identity_id,role_id) VALUES(?,?)",id,roleId);
        }
        CurrentUser actor=(CurrentUser)authentication.getPrincipal();
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('username',?))",
            actor.id(),"identity.created","erp_identity",id.toString(),UUID.randomUUID().toString(),request.username());
        return Map.of("id",id,"username",request.username(),"displayName",request.displayName());
    }

    @PutMapping("/identities/{id}")
    @PreAuthorize("hasAuthority('identity:write')")
    @Transactional
    public Map<String,Object> updateIdentity(@PathVariable long id, @RequestBody Map<String,Object> request, Authentication authentication) {
        List<Map<String,Object>> found=jdbc.queryForList("SELECT id,username,display_name,status FROM erp_identity WHERE id=?",id);
        if(found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不存在");
        Map<String,Object> current=found.get(0);
        CurrentUser actor=(CurrentUser)authentication.getPrincipal();

        String displayName=request.get("displayName")==null?null:request.get("displayName").toString().trim();
        if(displayName!=null && (displayName.isBlank()||displayName.length()>200))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"显示名称不能为空且不能超过 200 个字符");
        String status=request.get("status")==null?null:request.get("status").toString();
        if(status!=null && !List.of("active","disabled").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"账号状态只能是 active 或 disabled");
        String nextStatus=status==null?current.get("status").toString():status;
        if(actor.id()==id && "disabled".equals(nextStatus))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不能停用当前登录账号");

        List<Long> nextRoles;
        boolean rolesChanged=request.containsKey("roleIds");
        if(rolesChanged) {
            Object raw=request.get("roleIds");
            if(!(raw instanceof List<?> values))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"roleIds 必须是数组");
            nextRoles=values.stream().map(v->{
                try{return Long.valueOf(v.toString());}
                catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"角色 ID 无效");}
            }).distinct().toList();
            for(Long roleId:nextRoles) {
                Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM erp_role WHERE id=?",Integer.class,roleId);
                if(exists==null||exists==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"角色不存在: "+roleId);
            }
        } else {
            nextRoles=jdbc.query("SELECT role_id FROM erp_identity_role WHERE identity_id=? ORDER BY role_id",
                (rs,index)->rs.getLong(1),id);
        }

        Integer otherActiveAdmins=jdbc.queryForObject("""
            SELECT COUNT(*) FROM erp_identity i
            JOIN erp_identity_role ir ON ir.identity_id=i.id
            JOIN erp_role r ON r.id=ir.role_id
            WHERE i.id<>? AND i.status='active' AND r.code='admin'
            """,Integer.class,id);
        boolean remainsAdmin=nextRoles.stream().anyMatch(roleId->{
            String code=jdbc.queryForObject("SELECT code FROM erp_role WHERE id=?",String.class,roleId);
            return "admin".equals(code);
        });
        if((otherActiveAdmins==null||otherActiveAdmins==0) &&
            !("active".equals(nextStatus)&&remainsAdmin))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"系统至少需要保留一个启用状态的系统管理员");

        jdbc.update("UPDATE erp_identity SET display_name=COALESCE(?,display_name),status=COALESCE(?,status) WHERE id=?",
            displayName,status,id);
        if(rolesChanged) {
            jdbc.update("DELETE FROM erp_identity_role WHERE identity_id=?",id);
            for(Long roleId:nextRoles) jdbc.update("INSERT INTO erp_identity_role(identity_id,role_id) VALUES(?,?)",id,roleId);
        }
        Object password=request.get("password");
        if(password!=null && !password.toString().isBlank()) {
            String rawPassword=password.toString();
            if(rawPassword.length()<12||rawPassword.length()>128)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"新密码长度必须为 12 到 128 个字符");
            jdbc.update("UPDATE erp_identity SET password_hash=? WHERE id=?",passwords.encode(rawPassword),id);
            jdbc.update("UPDATE erp_auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE identity_id=? AND revoked_at IS NULL",id);
        }
        if("disabled".equals(nextStatus))
            jdbc.update("UPDATE erp_auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE identity_id=? AND revoked_at IS NULL",id);

        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('username',?,'status',?,'roles_changed',?,'password_reset',?))",
            actor.id(),"identity.updated","erp_identity",Long.toString(id),UUID.randomUUID().toString(),
            current.get("username"),nextStatus,rolesChanged,password!=null&&!password.toString().isBlank());
        return jdbc.queryForList("""
            SELECT i.id,i.username,i.display_name,i.status,
                   GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ',') AS roles
            FROM erp_identity i LEFT JOIN erp_identity_role ir ON ir.identity_id=i.id
            LEFT JOIN erp_role r ON r.id=ir.role_id
            WHERE i.id=? GROUP BY i.id
            """,id).get(0);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('audit:read')")
    public List<Map<String,Object>> audit(@RequestParam(defaultValue="50") int limit) {
        int safeLimit=Math.max(1,Math.min(limit,100));
        return jdbc.queryForList("SELECT id,actor_identity_id,action,resource_type,resource_id,request_id,occurred_at,summary_json FROM erp_audit_event ORDER BY id DESC LIMIT "+safeLimit);
    }

    public record CreateIdentity(@NotBlank @Size(max=100) String username,
        @NotBlank @Size(max=200) String displayName, @NotBlank @Size(min=12,max=128) String password,
        Long organizationId, List<Long> roleIds) {
        public CreateIdentity { roleIds = roleIds == null ? List.of() : List.copyOf(roleIds); }
    }
}