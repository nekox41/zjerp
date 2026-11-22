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
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    public TokenAuthenticationFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String hash = TokenUtil.sha256(header.substring(7).trim());
            List<CurrentUser> users = jdbc.query("""
                SELECT i.id, i.username, i.display_name, i.organization_id
                FROM erp_auth_session s
                JOIN erp_identity i ON i.id = s.identity_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP(3) AND i.status = 'active'
                """, (ResultSet rs, int row) -> {
                    List<String> permissions = jdbc.query(
                        "SELECT DISTINCT p.code FROM erp_identity_role ir JOIN erp_role_permission rp ON rp.role_id=ir.role_id JOIN erp_permission p ON p.id=rp.permission_id WHERE ir.identity_id=?",
                        (r, index) -> r.getString(1), rs.getLong("id"));
                    return new CurrentUser(rs.getLong("id"), rs.getString("username"),
                        rs.getString("display_name"), (Long) rs.getObject("organization_id"), hash, permissions);
                }, hash);
            if (!users.isEmpty()) {
                CurrentUser user = users.get(0);
                var authorities = user.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}