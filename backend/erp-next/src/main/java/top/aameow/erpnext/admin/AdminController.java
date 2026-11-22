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
    public Map<String,Object> createIdentity(@Valid @RequestBody CreateIdentity request) {
        jdbc.update("INSERT INTO erp_identity(username,password_hash,display_name,organization_id,status) VALUES(?,?,?,?, 'active')",
            request.username(), passwords.encode(request.password()), request.displayName(), request.organizationId());
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        for (Long roleId : request.roleIds()) {
            Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM erp_role WHERE id=?",Integer.class,roleId);
            if(exists==null||exists==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"角色不存在: "+roleId);
            jdbc.update("INSERT INTO erp_identity_role(identity_id,role_id) VALUES(?,?)",id,roleId);
        }
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('username',?))",
            null,"identity.created","erp_identity",id.toString(),UUID.randomUUID().toString(),request.username());
        return Map.of("id",id,"username",request.username(),"displayName",request.displayName());
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