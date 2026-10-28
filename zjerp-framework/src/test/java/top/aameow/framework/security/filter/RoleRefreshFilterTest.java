package top.aameow.framework.security.filter;

import java.util.List;
import java.util.Set;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import top.aameow.common.core.domain.entity.SysRole;
import top.aameow.common.core.domain.entity.SysUser;
import top.aameow.common.core.domain.model.LoginUser;
import top.aameow.framework.web.service.SysPermissionService;
import top.aameow.framework.web.service.TokenService;
import top.aameow.system.service.ISysUserService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleRefreshFilterTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void existingSessionUsesCurrentRolePermissions() throws Exception {
        SysUser current = new SysUser(1L);
        current.setStatus("0"); current.setDelFlag("0"); current.setRoles(List.of());
        LoginUser cached = new LoginUser(1L, current, Set.of("*:*:*"));
        TokenService tokens = mock(TokenService.class);
        ISysUserService users = mock(ISysUserService.class);
        SysPermissionService permissions = mock(SysPermissionService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(tokens.getLoginUser(request)).thenReturn(cached);
        when(users.selectUserById(1L)).thenReturn(current);
        when(permissions.getMenuPermission(current)).thenReturn(Set.of("management:contract:list"));
        JwtAuthenticationTokenFilter filter = new JwtAuthenticationTokenFilter();
        ReflectionTestUtils.setField(filter,"tokenService",tokens);
        ReflectionTestUtils.setField(filter,"userService",users);
        ReflectionTestUtils.setField(filter,"permissionService",permissions);
        filter.doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class));
        LoginUser authenticated=(LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(Set.of("management:contract:list"),authenticated.getPermissions());
        assertFalse(authenticated.getUser().isAdmin());
    }

    @Test void disabledUserCannotContinueUsingAnExistingToken() throws Exception {
        SysUser disabled = new SysUser(1L); disabled.setStatus("1"); disabled.setDelFlag("0");
        LoginUser cached = new LoginUser(1L,disabled,Set.of("*:*:*"));
        TokenService tokens=mock(TokenService.class);
        ISysUserService users=mock(ISysUserService.class);
        MockHttpServletRequest request=new MockHttpServletRequest();
        when(tokens.getLoginUser(request)).thenReturn(cached);
        when(users.selectUserById(1L)).thenReturn(disabled);
        JwtAuthenticationTokenFilter filter=new JwtAuthenticationTokenFilter();
        ReflectionTestUtils.setField(filter,"tokenService",tokens);
        ReflectionTestUtils.setField(filter,"userService",users);
        filter.doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokens,never()).verifyToken(any());
    }
}
