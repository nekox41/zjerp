package top.aameow.erpnext.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    public TokenAuthenticationFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String rawToken = header.substring(7).trim();
            if (!rawToken.isEmpty()) {
                String hash = TokenUtil.sha256(rawToken);
                List<Map<String,Object>> rows = jdbc.queryForList("""
                    SELECT i.id, i.username, i.display_name, i.organization_id
                    FROM erp_auth_session s
                    JOIN erp_identity i ON i.id = s.identity_id
                    WHERE s.token_hash = ? AND s.revoked_at IS NULL
                      AND s.expires_at > CURRENT_TIMESTAMP(3) AND i.status = 'active'
                    """, hash);
                if (!rows.isEmpty()) {
                    Map<String,Object> row = rows.get(0);
                    long id = ((Number) row.get("id")).longValue();
                    List<String> permissions = jdbc.query(
                        "SELECT DISTINCT p.code FROM erp_identity_role ir JOIN erp_role_permission rp ON rp.role_id=ir.role_id JOIN erp_permission p ON p.id=rp.permission_id WHERE ir.identity_id=? ORDER BY p.code",
                        (rs, index) -> rs.getString(1), id);
                    Object org = row.get("organization_id");
                    CurrentUser user = new CurrentUser(id, (String) row.get("username"),
                        (String) row.get("display_name"), org == null ? null : ((Number) org).longValue(),
                        hash, permissions);
                    var authorities = permissions.stream().map(SimpleGrantedAuthority::new).toList();
                    SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
                }
            }
        }
        chain.doFilter(request, response);
    }
}