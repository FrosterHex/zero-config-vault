package com.vault.configmanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.configmanager.dto.response.ApiErrorResponse;
import com.vault.configmanager.model.ApiKey;
import com.vault.configmanager.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
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

    // Path tricks that could make the raw URI disagree with the path Spring routes on
    private static final Pattern AMBIGUOUS_PATH_PATTERN =
            Pattern.compile("(?i)(/\\.{1,2}(/|$)|;|\\\\|%2e|%2f|%3b|%5c)");

    private final ObjectMapper objectMapper;
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ObjectMapper objectMapper, ApiKeyService apiKeyService) {
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

        if (AMBIGUOUS_PATH_PATTERN.matcher(requestUri).find()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request", "Ambiguous request path.");
            return;
        }

        String rawApiKey = request.getHeader(API_KEY_HEADER);
        if ((rawApiKey == null || rawApiKey.isBlank()) && allowsQueryParamKey(request.getMethod(), requestUri)) {
            // Browsers cannot attach headers to EventSource streams or plain download links
            rawApiKey = request.getParameter("apiKey");
        }

        if (rawApiKey == null || rawApiKey.isBlank()) {
            writeUnauthorized(response, "Missing API key. Provide a valid X-API-Key header.");
            return;
        }

        rawApiKey = rawApiKey.trim();

        // 1. Authenticate against the api_keys collection (the only source of credentials)
        Optional<ApiKey> keyOpt = apiKeyService.validateAndAuthenticate(rawApiKey);
        if (keyOpt.isEmpty()) {
            writeUnauthorized(response, "Invalid API key.");
            return;
        }
        ApiKey authenticatedKey = keyOpt.get();

        // 2. Granular RBAC and scope authorization. Endpoints whose app/env arrive in the
        //    request body or span many apps enforce scope again in the controller/service.
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

        // Store masked key for downstream audit logging
        request.setAttribute(MASKED_KEY_ATTR, maskApiKey(rawApiKey));
        filterChain.doFilter(request, response);
    }

    private boolean allowsQueryParamKey(String method, String uri) {
        return "GET".equalsIgnoreCase(method)
                && (uri.startsWith("/api/v1/vault/stream/") || uri.startsWith("/api/v1/vault/binaries/"));
    }

    private String determineRequiredRole(String method, String uri) {
        if (uri.startsWith("/api/v1/vault/keys")) {
            return "ADMIN";
        }
        if (uri.equals("/api/v1/vault/stream") || uri.equals("/api/v1/vault/stream/")) {
            // The global stream spans every app and environment
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
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", message);
    }

    private void writeError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                status,
                error,
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
