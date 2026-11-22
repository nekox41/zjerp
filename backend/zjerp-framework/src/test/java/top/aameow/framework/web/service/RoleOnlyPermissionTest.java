package top.aameow.framework.web.service;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.aameow.common.core.domain.entity.SysRole;
import top.aameow.common.core.domain.entity.SysUser;
import top.aameow.system.service.ISysMenuService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleOnlyPermissionTest {
    private SysRole role(long id, String key, String status, String deleted) {
        SysRole role = new SysRole(id);
        role.setRoleKey(key);
        role.setStatus(status);
        role.setDelFlag(deleted);
        return role;
    }

    private SysPermissionService service(ISysMenuService menu) {
        SysPermissionService result = new SysPermissionService();
        ReflectionTestUtils.setField(result, "menuService", menu);
        return result;
    }

    @Test void accountOneWithoutAnAdminRoleHasNoSpecialPermissions() {
        ISysMenuService menu = mock(ISysMenuService.class);
        SysUser user = new SysUser(1L);
        assertFalse(user.isAdmin());
        assertEquals(Set.of(), service(menu).getRolePermission(user));
        assertEquals(Set.of(), service(menu).getMenuPermission(user));
        verifyNoInteractions(menu);
    }

    @Test void adminRoleWorksForAnyAccountId() {
        ISysMenuService menu = mock(ISysMenuService.class);
        SysUser user = new SysUser(42L);
        user.setRoles(List.of(role(99, "admin", "0", "0")));
        assertTrue(user.isAdmin());
        assertEquals(Set.of("admin"), service(menu).getRolePermission(user));
        assertEquals(Set.of("*:*:*"), service(menu).getMenuPermission(user));
        verifyNoInteractions(menu);
    }

    @Test void disabledAndDeletedRolesNeverGrantPermissions() {
        ISysMenuService menu = mock(ISysMenuService.class);
        SysUser user = new SysUser(1L);
        user.setRoles(List.of(role(1, "admin", "1", "0"), role(2, "admin", "0", "2")));
        assertFalse(user.isAdmin());
        assertEquals(Set.of(), service(menu).getRolePermission(user));
        assertEquals(Set.of(), service(menu).getMenuPermission(user));
        verifyNoInteractions(menu);
    }

    @Test void permissionsAreTheUnionOfEnabledRoleMenus() {
        ISysMenuService menu = mock(ISysMenuService.class);
        when(menu.selectMenuPermsByRoleId(2L)).thenReturn(Set.of("management:contract:list"));
        when(menu.selectMenuPermsByRoleId(3L)).thenReturn(Set.of("management:report:list"));
        SysUser user = new SysUser(1L);
        user.setRoles(List.of(role(2, "contracts", "0", "0"), role(3, "reports", "0", "0"), role(4, "disabled", "1", "0")));
        assertEquals(Set.of("management:contract:list", "management:report:list"), service(menu).getMenuPermission(user));
        assertEquals(Set.of("contracts", "reports"), service(menu).getRolePermission(user));
        verify(menu, never()).selectMenuPermsByRoleId(4L);
    }
}
