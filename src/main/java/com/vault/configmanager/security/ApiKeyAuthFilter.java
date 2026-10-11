package com.vault.configmanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.dto.response.ApiErrorResponse;
import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security filter that validates the X-API-Key header on /api/** requests,
 * enforcing granular Role-Based Access Control (RBAC) and application/environment scopes.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    public static final String MASKED_KEY_ATTR = "vault.masked.api.key";
    public static final String AUTHENTICATED_KEY_ATTR = "vault.authenticated.api.key";

    private static final Pattern APP_ENV_PATTERN =
            Pattern.compile("^/api/v1/vault/(?:config|export|stream|binaries)/([^/?#]+)/([^/?#]+)(?:/.*)?$");

    private final List<String> validApiKeys;
    private final ObjectMapper objectMapper;
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(@Value("${vault.api.keys:sk-dev-key-001,sk-dev-key-002}") String apiKeysConfig,
                            ObjectMapper objectMapper) {
        this(apiKeysConfig, objectMapper, null);
    }

    @Autowired
    public ApiKeyAuthFilter(@Value("${vault.api.keys:sk-dev-key-001,sk-dev-key-002}") String apiKeysConfig,
                            ObjectMapper objectMapper,
                            @Autowired(required = false) ApiKeyService apiKeyService) {
        this.validApiKeys = Arrays.asList(apiKeysConfig.split(","));
        this.objectMapper = objectMapper;
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Set standard CORS headers for browser clients
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, X-API-Key, Authorization");
        response.setHeader("Access-Control-Max-Age", "3600");

        // Handle preflight OPTIONS requests immediately
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String requestUri = request.getRequestURI();

        // Only intercept /api/** paths, excluding API docs and swagger resources
        if (!requestUri.startsWith("/api/") || requestUri.contains("/v3/api-docs") || requestUri.contains("/swagger-ui")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawApiKey = request.getHeader(API_KEY_HEADER);
        if (rawApiKey == null || rawApiKey.isBlank()) {
            rawApiKey = request.getParameter("apiKey");
        }
        if (rawApiKey == null || rawApiKey.isBlank()) {
            rawApiKey = request.getParameter("key");
        }

        if (rawApiKey == null || rawApiKey.isBlank()) {
            writeUnauthorized(response, "Missing API key. Provide a valid X-API-Key header or apiKey query parameter.");
            return;
        }

        rawApiKey = rawApiKey.trim();

        ApiKey authenticatedKey = null;

        // 1. Authenticate via ApiKeyService from the api_keys collection
        if (apiKeyService != null) {
            Optional<ApiKey> keyOpt = apiKeyService.validateAndAuthenticate(rawApiKey);
            if (keyOpt.isPresent()) {
                authenticatedKey = keyOpt.get();
            }
        }

        // 2. Fall back to bootstrap/static configured keys if not found in MongoDB
        if (authenticatedKey == null) {
            if (!validApiKeys.contains(rawApiKey)) {
                writeUnauthorized(response, "Invalid API key.");
                return;
            }
        }

        // 3. Granular RBAC and Scope Authorization
        if (authenticatedKey != null && apiKeyService != null) {
            String requiredRole = determineRequiredRole(request.getMethod(), requestUri);
            String[] appAndEnv = extractAppAndEnv(requestUri);
            String app = appAndEnv[0];
            String env = appAndEnv[1];

            boolean authorized = apiKeyService.authorize(authenticatedKey, requiredRole, app, env);
            if (!authorized) {
                writeForbidden(response, "API key unauthorized for role '" + requiredRole +
                        "' on app='" + (app != null ? app : "*") +
                        "', env='" + (env != null ? env : "*") + "'");
                return;
            }
            request.setAttribute(AUTHENTICATED_KEY_ATTR, authenticatedKey);
        }

        // Store masked key for downstream audit logging
        request.setAttribute(MASKED_KEY_ATTR, maskApiKey(rawApiKey));
        filterChain.doFilter(request, response);
    }

    private String determineRequiredRole(String method, String uri) {
        if (uri.startsWith("/api/v1/vault/keys")) {
            return "ADMIN";
        }
        if (uri.contains("/export")) {
            return "EXPORT";
        }
        if (uri.contains("/rollback")) {
            return "ROLLBACK";
        }
        if ("POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method) ||
                "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return "WRITE";
        }
        return "READ";
    }

    private String[] extractAppAndEnv(String uri) {
        if (uri.startsWith("/api/v1/vault/config/bulk")) {
            return new String[]{null, null};
        }
        Matcher matcher = APP_ENV_PATTERN.matcher(uri);
        if (matcher.matches()) {
            String candidateApp = matcher.group(1);
            if (!"bulk".equalsIgnoreCase(candidateApp) && !"stats".equalsIgnoreCase(candidateApp)) {
                return new String[]{candidateApp, matcher.group(2)};
            }
        }
        return new String[]{null, null};
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                401,
                "Unauthorized",
                message,
                LocalDateTime.now()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                403,
                "Forbidden",
                message,
                LocalDateTime.now()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Masks the API key, showing only the first 3 and last 4 characters.
     * Example: "sk-dev-key-001" becomes "sk-****-001"
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 7) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
