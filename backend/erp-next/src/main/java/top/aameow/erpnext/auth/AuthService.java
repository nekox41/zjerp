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
            JOIN erp_role r ON r.id=ir.role_id WHERE r.code='admin' AND i.username='admin' AND i.status='active'
            """, Integer.class);
        return count != null && count > 0;
    }

    public static void validateNewPassword(String password){
        if(password==null||password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||password.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"密码至少12字符且不得超过72个UTF-8字节");
    }
    @Transactional
    public void bootstrapAdmin(String username, String displayName, String password) {
        username=username.trim();displayName=displayName.trim();validateNewPassword(password);
        if(!"admin".equals(username)||displayName.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"账号和姓名不能为空");
        Long roleId = jdbc.queryForObject("SELECT id FROM erp_role WHERE code='admin' FOR UPDATE", Long.class);
        if (isInitialized()) throw new ResponseStatusException(HttpStatus.CONFLICT, "管理员已初始化");
        jdbc.update("INSERT INTO erp_identity(username,password_hash,display_name,status) VALUES(?,?,?,'active')",
            username, passwords.encode(password), displayName);
        Long identityId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO erp_identity_role(identity_id,role_id) VALUES(?,?)", identityId, roleId);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String username, String password) {
        if(password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"账号或密码错误");
        username=username.trim();
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT id, username, password_hash, display_name, organization_id, status,
                   failed_login_count, locked_until
            FROM erp_identity WHERE username=? FOR UPDATE
            """, username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        Map<String,Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        if (!"active".equals(row.get("status")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        Timestamp lockedUntil = (Timestamp) row.get("locked_until");
        if (lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.LOCKED, "登录失败次数过多，请稍后再试");
        if(lockedUntil!=null&&!lockedUntil.toInstant().isAfter(Instant.now()))
            jdbc.update("UPDATE erp_identity SET failed_login_count=0,locked_until=NULL WHERE id=?",id);
        if (!passwords.matches(password, (String) row.get("password_hash"))) {
            jdbc.update("""
                UPDATE erp_identity SET locked_until=CASE WHEN failed_login_count >= ? THEN DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? MINUTE) ELSE NULL END,
                  failed_login_count=LEAST(failed_login_count+1,?)
                WHERE id=?
                """, Math.max(0, maxFailedLogins - 1), lockMinutes, Math.max(1,maxFailedLogins), id);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        jdbc.update("UPDATE erp_identity SET failed_login_count=0, locked_until=NULL WHERE id=?", id);
        String token = TokenUtil.newToken();
        String hash = TokenUtil.sha256(token);
        jdbc.update("INSERT INTO erp_auth_session(identity_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR))",
            id, hash, sessionHours);
        List<String> permissions = permissions(id);
        return new LoginResult(token, id, (String) row.get("username"), (String) row.get("display_name"),
            row.get("organization_id") == null ? null : ((Number) row.get("organization_id")).longValue(), permissions);
    }

    public void logout(CurrentUser user) {
        jdbc.update("UPDATE erp_auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE token_hash=? AND revoked_at IS NULL", user.tokenHash());
    }

    public List<String> permissions(long identityId) {
        return jdbc.query("SELECT DISTINCT p.code FROM erp_identity_role ir JOIN erp_role_permission rp ON rp.role_id=ir.role_id JOIN erp_permission p ON p.id=rp.permission_id WHERE ir.identity_id=? ORDER BY p.code",
            (rs, row) -> rs.getString(1), identityId);
    }

    public record LoginResult(String token, long id, String username, String displayName, Long organizationId, List<String> permissions) { }
}