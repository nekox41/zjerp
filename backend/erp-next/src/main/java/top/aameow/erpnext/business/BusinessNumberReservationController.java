package top.aameow.erpnext.business;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/business/number-reservations")
public class BusinessNumberReservationController {
    private final JdbcTemplate jdbc;
    public BusinessNumberReservationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String,Object> reserve(@RequestBody Map<String,Object> body, Authentication authentication) {
        String serviceType = required(body, "serviceType");
        String purpose = required(body, "purpose");
        authorize(purpose, authentication);
        BusinessNumberPool.Reservation reservation = BusinessNumberPool.reserve(
            jdbc, serviceType, purpose, actor(authentication));
        return Map.of(
            "id", reservation.id().toString(),
            "number", reservation.number(),
            "serviceType", reservation.serviceType(),
            "purpose", reservation.purpose(),
            "expiresInSeconds", BusinessNumberPool.RESERVATION_TTL_SECONDS
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Map<String,Boolean> release(@PathVariable String id, Authentication authentication) {
        return Map.of("released", BusinessNumberPool.release(jdbc, id, actor(authentication)));
    }

    private void authorize(String purpose, Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(a -> a.getAuthority()).collect(Collectors.toSet());
        boolean allowed = switch (purpose) {
            case "contract" -> authorities.contains("contract:write");
            case "project" -> authorities.contains("project:write");
            case "maintenance_renewal" -> authorities.contains("contract:write") && authorities.contains("maintenance:write");
            default -> false;
        };
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有预留此类业务编号的权限");
    }

    private long actor(Authentication authentication) {
        return ((CurrentUser)authentication.getPrincipal()).id();
    }

    private String required(Map<String,Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 不能为空");
        return value.toString().trim();
    }
}
