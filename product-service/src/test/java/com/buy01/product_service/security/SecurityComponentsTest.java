package com.buy01.product_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class SecurityComponentsTest {

    private final UserContextFilter filter = new UserContextFilter();

    @Test
    void doFilter_setsAuthenticatedUserFromHeaders() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-User-Id")).thenReturn("u1");
        when(request.getHeader("X-User-Roles")).thenReturn("SELLER, CLIENT");

        filter.doFilter(request, response, chain);

        verify(request).setAttribute(eq(UserContextFilter.USER_ATTRIBUTE), any(AuthenticatedUser.class));
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_blankUserId_doesNotSetAttribute() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-User-Id")).thenReturn("  ");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_missingRoles_setsEmptyRoles() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-User-Id")).thenReturn("u2");
        when(request.getHeader("X-User-Roles")).thenReturn(" ");

        filter.doFilter(request, response, chain);

        verify(request).setAttribute(eq(UserContextFilter.USER_ATTRIBUTE), any(AuthenticatedUser.class));
    }

    @Test
    void isAOncePerRequestFilter() {
        assertThat(filter).isInstanceOf(OncePerRequestFilter.class);
    }

    @Test
    void authenticatedUser_hasRoleIsCaseInsensitive() {
        AuthenticatedUser user = new AuthenticatedUser("u", java.util.List.of("seller"));

        assertThat(user.hasRole("SELLER")).isTrue();
        assertThat(user.hasRole("ADMIN")).isFalse();
    }

    @Test
    void authenticatedUser_nullRoles_hasRoleFalse() {
        AuthenticatedUser user = new AuthenticatedUser("u", null);

        assertThat(user.hasRole("SELLER")).isFalse();
    }

    @Test
    void resolver_returnsAttributeWhenPresent() throws Exception {
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        MethodParameter parameter = mock(MethodParameter.class);
        CurrentUser annotation = mock(CurrentUser.class);
        AuthenticatedUser user = new AuthenticatedUser("u", java.util.List.of("SELLER"));
        when(parameter.getParameterAnnotation(CurrentUser.class)).thenReturn(annotation);
        when(annotation.requiired()).thenReturn(true);
        when(webRequest.getNativeRequest()).thenReturn(servletRequest);
        when(servletRequest.getAttribute(UserContextFilter.USER_ATTRIBUTE)).thenReturn(user);

        Object resolved = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(resolved).isSameAs(user);
    }

    @Test
    void resolver_missingUser_throwsUnauthorized() {
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        MethodParameter parameter = mock(MethodParameter.class);
        CurrentUser annotation = mock(CurrentUser.class);
        when(parameter.getParameterAnnotation(CurrentUser.class)).thenReturn(annotation);
        when(annotation.requiired()).thenReturn(true);
        when(webRequest.getNativeRequest()).thenReturn(servletRequest);
        when(servletRequest.getAttribute(UserContextFilter.USER_ATTRIBUTE)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> resolver.resolveArgument(parameter, null, webRequest, null));
    }

    @Test
    void resolver_supportsAuthenticatedUserParameter() {
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getParameterType()).thenReturn((Class) AuthenticatedUser.class);
        when(parameter.hasParameterAnnotation(CurrentUser.class)).thenReturn(true);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }
}