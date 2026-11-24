package top.aameow.erpnext.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final int sessionHours;
    private final int maxFailedLogins;
    private final int lockMinutes;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords,
        @Value("${erp.auth.session-hours:12}") int sessionHours,
        @Value("${erp.auth.max-failed-logins:5}") int maxFailedLogins,
        @Value("${erp.auth.lock-minutes:10}") int lockMinutes) {
        this.jdbc = jdbc; this.passwords = passwords; this.sessionHours = sessionHours;
        this.maxFailedLogins = maxFailedLogins; this.lockMinutes = lockMinutes;
    }

    public boolean isInitialized() {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM erp_identity i
            JOIN erp_identity_role ir ON ir.identity_id=i.id
            JOIN erp_role r ON r.id=ir.role_id WHERE r.code='admin'
            """, Integer.class);
        return count != null && count > 0;
    }

    @Transactional
    public void bootstrapAdmin(String username, String displayName, String password) {
        Long roleId = jdbc.queryForObject("SELECT id FROM erp_role WHERE code='admin' FOR UPDATE", Long.class);
        if (isInitialized()) throw new ResponseStatusException(HttpStatus.CONFLICT, "管理员已初始化");
        jdbc.update("INSERT INTO erp_identity(username,password_hash,display_name,status) VALUES(?,?,?,'active')",
            username, passwords.encode(password), displayName);
        Long identityId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO erp_identity_role(identity_id,role_id) VALUES(?,?)", identityId, roleId);
        audit(identityId, "identity.bootstrap", "erp_identity", identityId.toString(), "管理员初始化");
    }

    @Transactional
    public void register(String username, String displayName, String password) {
        if (!isInitialized()) throw new ResponseStatusException(HttpStatus.CONFLICT,"请先初始化管理员");
        username=username.trim();
        displayName=displayName.trim();
        if(username.isBlank()||displayName.isBlank()||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"账号和姓名不能为空，密码不得超过72字节");
        try {
            jdbc.update("INSERT INTO erp_identity(username,password_hash,display_name,status) VALUES(?,?,?,'disabled')",
                username,passwords.encode(password),displayName);
        } catch(org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"账号不可用，请选择其他账号");
        }
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        // Public registrations never acquire roles, employee records or sessions.
        audit(id,"identity.registered","erp_identity",id.toString(),"自主注册，等待管理员启用及授权");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String username, String password) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT id, username, password_hash, display_name, organization_id, status,
                   failed_login_count, locked_until
            FROM erp_identity WHERE username=?
            """, username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        Map<String,Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        Timestamp lockedUntil = (Timestamp) row.get("locked_until");
        if (lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.LOCKED, "登录失败次数过多，请稍后再试");
        if (!"active".equals(row.get("status")) || !passwords.matches(password, (String) row.get("password_hash"))) {
            jdbc.update("""
                UPDATE erp_identity SET failed_login_count=failed_login_count+1,
                  locked_until=CASE WHEN failed_login_count >= ? THEN DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? MINUTE) ELSE locked_until END
                WHERE id=?
                """, Math.max(0, maxFailedLogins - 1), lockMinutes, id);
            audit(null, "auth.login.failed", "erp_identity", Long.toString(id), "登录失败");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        jdbc.update("UPDATE erp_identity SET failed_login_count=0, locked_until=NULL WHERE id=?", id);
        String token = TokenUtil.newToken();
        String hash = TokenUtil.sha256(token);
        jdbc.update("INSERT INTO erp_auth_session(identity_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR))",
            id, hash, sessionHours);
        audit(id, "auth.login", "erp_auth_session", hash.substring(0, 12), "用户登录");
        List<String> permissions = permissions(id);
        return new LoginResult(token, id, (String) row.get("username"), (String) row.get("display_name"),
            row.get("organization_id") == null ? null : ((Number) row.get("organization_id")).longValue(), permissions);
    }

    public void logout(CurrentUser user) {
        jdbc.update("UPDATE erp_auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE token_hash=? AND revoked_at IS NULL", user.tokenHash());
        audit(user.id(), "auth.logout", "erp_auth_session", user.tokenHash().substring(0, 12), "用户退出");
    }

    public List<String> permissions(long identityId) {
        return jdbc.query("SELECT DISTINCT p.code FROM erp_identity_role ir JOIN erp_role_permission rp ON rp.role_id=ir.role_id JOIN erp_permission p ON p.id=rp.permission_id WHERE ir.identity_id=? ORDER BY p.code",
            (rs, row) -> rs.getString(1), identityId);
    }

    private void audit(Long actor, String action, String type, String id, String summary) {
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('summary',?))",
            actor, action, type, id, UUID.randomUUID().toString(), summary);
    }

    public record LoginResult(String token, long id, String username, String displayName, Long organizationId, List<String> permissions) { }
}