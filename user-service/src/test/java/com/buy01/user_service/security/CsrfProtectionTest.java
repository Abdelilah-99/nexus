package com.buy01.user_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({SecurityConfig.class, CsrfProtectionTest.WebSecurity.class})
class CsrfProtectionTest {
    @Configuration
    @EnableWebSecurity
    static class WebSecurity {
    }

    @Autowired
    private FilterChainProxy filters;

    @Test
    void unsafeRequestWithoutTokenIsRejected() throws Exception {
        var request = new MockHttpServletRequest("POST", "/auth/login");
        var response = new MockHttpServletResponse();
        filters.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Unsafe request must not reach the application");
        });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"", "\"details\":{}");
    }

    @Test
    void cookieAndMatchingHeaderAllowWriteWithoutAnAuthenticationSession() throws Exception {
        var read = new MockHttpServletRequest("GET", "/csrf");
        var tokenResponse = new MockHttpServletResponse();
        var tokenValue = new String[1];
        filters.doFilter(read, tokenResponse, (req, res) -> {
            var token = (CsrfToken) ((HttpServletRequest) req).getAttribute(CsrfToken.class.getName());
            tokenValue[0] = token.getToken();
        });
        assertThat(tokenResponse.getCookie("XSRF-TOKEN")).isNotNull();
        assertThat(read.getSession(false)).isNull();

        var write = new MockHttpServletRequest("POST", "/auth/login");
        write.setCookies(tokenResponse.getCookie("XSRF-TOKEN"));
        write.addHeader("X-XSRF-TOKEN", tokenValue[0]);
        var result = new MockHttpServletResponse();
        filters.doFilter(write, result, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        assertThat(result.getStatus()).isEqualTo(204);
        assertThat(write.getSession(false)).isNull();
    }

    @Test
    void cookieWithoutMatchingHeaderIsRejected() throws Exception {
        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "expected"));
        request.addHeader("X-XSRF-TOKEN", "incorrect");
        var response = new MockHttpServletResponse();
        filters.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Invalid token must not reach the application");
        });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"", "\"details\":{}");
    }
}
