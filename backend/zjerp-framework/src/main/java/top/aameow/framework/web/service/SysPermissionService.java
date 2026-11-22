package top.aameow.framework.web.service;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.aameow.common.core.domain.entity.SysRole;
import top.aameow.common.core.domain.entity.SysUser;
import top.aameow.system.service.ISysMenuService;

/** Effective permissions come exclusively from enabled, assigned roles. */
@Component
public class SysPermissionService {
    @Autowired
    private ISysMenuService menuService;

    private boolean enabled(SysRole role) {
        return role != null && "0".equals(role.getStatus()) && "0".equals(role.getDelFlag());
    }

    public Set<String> getRolePermission(SysUser user) {
        Set<String> roles = new HashSet<>();
        if (user.getRoles() != null) {
            for (SysRole role : user.getRoles()) {
                if (enabled(role)) roles.add(role.getRoleKey());
            }
        }
        return roles;
    }

    public Set<String> getMenuPermission(SysUser user) {
        Set<String> permissions = new HashSet<>();
        if (user.isAdmin()) {
            permissions.add("*:*:*");
        } else if (user.getRoles() != null) {
            for (SysRole role : user.getRoles()) {
                if (enabled(role)) permissions.addAll(menuService.selectMenuPermsByRoleId(role.getRoleId()));
            }
        }
        return permissions;
    }
}
