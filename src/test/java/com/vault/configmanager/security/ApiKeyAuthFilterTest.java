package com.vault.configmanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private ApiKeyAuthFilter filter;
    private ObjectMapper objectMapper;
    private TestApiKeys keys;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        keys = TestApiKeys.withAdmin("sk-admin-token-001")
                .register("sk-read-token", List.of("auth-svc"), List.of("dev"), Set.of("READ"))
                .register("sk-write-token", List.of("auth-svc"), List.of("dev"), Set.of("WRITE"));
        filter = new ApiKeyAuthFilter(objectMapper, keys.service());
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, FilterChain chain) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);
        return response;
    }

    @Test
    void nonApiPath_shouldPassThroughWithoutAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingApiKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/app/dev");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing API key"));
    }

    @Test
    void invalidApiKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/app/dev");
        request.addHeader("X-API-Key", "invalid-key");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid API key"));
    }

    @Test
    void validApiKey_shouldPassAndMaskKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/app/dev");
        request.addHeader("X-API-Key", "sk-admin-token-001");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertEquals("sk-****-001", request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR));
        assertNotNull(request.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_KEY_ATTR));
    }

    @Test
    void wellKnownDevKey_notInDatabase_isRejected() throws ServletException, IOException {
        // Regression: keys used to fall back to a static list that bypassed RBAC entirely
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/vault/keys/some-id");
        request.addHeader("X-API-Key", "sk-dev-key-001");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void revokedKey_isRejected() throws ServletException, IOException {
        ApiKey revoked = keys.repository().findByHashedSecret(ApiKeyService.sha256Hex("sk-read-token")).orElseThrow();
        revoked.setActive(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/auth-svc/dev");
        request.addHeader("X-API-Key", "sk-read-token");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void scopedApiKey_withUnauthorizedRole_returns403Forbidden() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/vault/config/auth-svc/dev/rollback/1");
        request.addHeader("X-API-Key", "sk-read-token");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Forbidden"));
    }

    @Test
    void scopedApiKey_onOtherApp_returns403Forbidden() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/billing-svc/dev");
        request.addHeader("X-API-Key", "sk-read-token");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
    }

    @Test
    void scopedApiKey_withAuthorizedScope_passes() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/vault/config/auth-svc/dev");
        request.addHeader("X-API-Key", "sk-write-token");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertNotNull(request.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_KEY_ATTR));
    }

    @Test
    void globalStream_requiresAdmin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/stream");
        request.addHeader("X-API-Key", "sk-read-token");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletResponse response = run(request, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
    }

    @Test
    void queryParamKey_acceptedOnlyForStreamAndBinaryDownloads() throws ServletException, IOException {
        MockHttpServletRequest streamRequest = new MockHttpServletRequest("GET", "/api/v1/vault/stream/auth-svc/dev");
        streamRequest.setParameter("apiKey", "sk-read-token");
        FilterChain streamChain = mock(FilterChain.class);
        assertEquals(200, run(streamRequest, streamChain).getStatus());
        verify(streamChain).doFilter(any(), any());

        MockHttpServletRequest configRequest = new MockHttpServletRequest("GET", "/api/v1/vault/config/auth-svc/dev");
        configRequest.setParameter("apiKey", "sk-read-token");
        FilterChain configChain = mock(FilterChain.class);
        assertEquals(401, run(configRequest, configChain).getStatus());
        verify(configChain, never()).doFilter(any(), any());
    }

    @Test
    void ambiguousPaths_areRejectedBeforeScopeEvaluation() throws ServletException, IOException {
        for (String uri : List.of(
                "/api/v1/vault/config/auth-svc/dev/../../billing-svc/dev",
                "/api/v1/vault/config/auth-svc;x=1/dev",
                "/api/v1/vault/config/auth-svc%2fdev/x")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            request.addHeader("X-API-Key", "sk-read-token");
            FilterChain filterChain = mock(FilterChain.class);

            assertEquals(400, run(request, filterChain).getStatus(), uri);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }
}
