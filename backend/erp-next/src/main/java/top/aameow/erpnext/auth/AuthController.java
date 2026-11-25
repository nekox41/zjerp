package top.aameow.erpnext.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final String bootstrapToken;

    public AuthController(AuthService auth, @Value("${erp.bootstrap.token:}") String bootstrapToken) {
        this.auth = auth; this.bootstrapToken = bootstrapToken;
    }

    @GetMapping("/bootstrap/status")
    public Map<String,Boolean> bootstrapStatus() { return Map.of("initialized", auth.isInitialized()); }

    @PostMapping("/bootstrap/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> bootstrap(@RequestHeader(value="X-Bootstrap-Token", required=false) String supplied,
        @Valid @RequestBody BootstrapRequest request) {
        if (bootstrapToken.isBlank() || supplied == null ||
            !MessageDigest.isEqual(bootstrapToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员初始化凭据无效");
        auth.bootstrapAdmin(request.username(), request.displayName(), request.password());
        return Map.of("message", "管理员账号已创建，请登录");
    }

    @PostMapping("/auth/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @GetMapping("/auth/me")
    public Map<String,Object> me(Authentication authentication) {
        CurrentUser user = (CurrentUser) authentication.getPrincipal();
        return Map.of("id", user.id(), "username", user.username(), "displayName", user.displayName(),
            "permissions", user.permissions());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) { auth.logout((CurrentUser) authentication.getPrincipal()); }

    public record BootstrapRequest(@NotBlank @Size(max=100) String username,
        @NotBlank @Size(max=200) String displayName, @NotBlank @Size(min=12,max=128) String password) { }
    public record LoginRequest(@NotBlank @Size(max=100) String username, @NotBlank String password) { }
}