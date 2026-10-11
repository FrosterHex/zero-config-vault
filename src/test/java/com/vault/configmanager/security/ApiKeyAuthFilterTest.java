package com.vault.configmanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private ApiKeyAuthFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        filter = new ApiKeyAuthFilter("sk-dev-key-001,sk-dev-key-002", objectMapper);
    }

    @Test
    void nonApiPath_shouldPassThroughWithoutAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingApiKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/app/dev");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing API key"));
    }

    @Test
    void invalidApiKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/app/dev");
        request.addHeader("X-API-Key", "invalid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid API key"));
    }

    @Test
    void validApiKey_shouldPassAndMaskKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault/config/app/dev");
        request.addHeader("X-API-Key", "sk-dev-key-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertEquals("sk-****-001", request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR));
    }

    @Test
    void scopedApiKey_withUnauthorizedRole_returns403Forbidden() throws ServletException, IOException {
        com.vault.configmanager.service.ApiKeyService mockApiKeyService = mock(com.vault.configmanager.service.ApiKeyService.class);
        ApiKeyAuthFilter filterWithService = new ApiKeyAuthFilter("sk-dev-key-001", objectMapper, mockApiKeyService);

        com.vault.configmanager.model.ApiKey apiKey = new com.vault.configmanager.model.ApiKey(
                "Read Only Key", "sk-read-", "hash", java.util.List.of("auth-svc"), java.util.List.of("dev"),
                java.util.Set.of("READ"), null
        );

        when(mockApiKeyService.validateAndAuthenticate("sk-read-token")).thenReturn(java.util.Optional.of(apiKey));
        when(mockApiKeyService.authorize(apiKey, "WRITE", "auth-svc", "dev")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/vault/config/auth-svc/dev");
        request.addHeader("X-API-Key", "sk-read-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filterWithService.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Forbidden"));
    }

    @Test
    void scopedApiKey_withAuthorizedScope_passes() throws ServletException, IOException {
        com.vault.configmanager.service.ApiKeyService mockApiKeyService = mock(com.vault.configmanager.service.ApiKeyService.class);
        ApiKeyAuthFilter filterWithService = new ApiKeyAuthFilter("sk-dev-key-001", objectMapper, mockApiKeyService);

        com.vault.configmanager.model.ApiKey apiKey = new com.vault.configmanager.model.ApiKey(
                "Dev Writer Key", "sk-write-", "hash", java.util.List.of("auth-svc"), java.util.List.of("dev"),
                java.util.Set.of("WRITE"), null
        );

        when(mockApiKeyService.validateAndAuthenticate("sk-write-token")).thenReturn(java.util.Optional.of(apiKey));
        when(mockApiKeyService.authorize(apiKey, "WRITE", "auth-svc", "dev")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/vault/config/auth-svc/dev");
        request.addHeader("X-API-Key", "sk-write-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filterWithService.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertNotNull(request.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_KEY_ATTR));
    }
}
