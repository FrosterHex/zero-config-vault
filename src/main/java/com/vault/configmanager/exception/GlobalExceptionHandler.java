package com.vault.configmanager.exception;

import com.vault.configmanager.dto.response.ApiErrorResponse;
import com.vault.configmanager.security.ApiKeyAuthFilter;
import com.vault.configmanager.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * Centralized exception handler producing standardized ApiErrorResponse DTOs
 * and logging failures to the MongoDB capped audit log collection.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleConfigNotFound(ConfigNotFoundException ex, HttpServletRequest request) {
        recordFailureAudit(request, "CONFIG_NOT_FOUND", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(IntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrityViolation(IntegrityViolationException ex, HttpServletRequest request) {
        recordFailureAudit(request, "INTEGRITY_VIOLATION", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.CONFLICT.value(), "Integrity Violation", ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DuplicateVersionException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateVersion(DuplicateVersionException ex, HttpServletRequest request) {
        recordFailureAudit(request, "DUPLICATE_VERSION", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.CONFLICT.value(), "Version Conflict", ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        recordFailureAudit(request, "BAD_REQUEST", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(BulkTransactionException.class)
    public ResponseEntity<ApiErrorResponse> handleBulkTransactionException(BulkTransactionException ex, HttpServletRequest request) {
        recordFailureAudit(request, "BULK_TRANSACTION_ROLLBACK", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY.value(), "Atomic Bulk Transaction Rolled Back", ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(ScopeViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleScopeViolation(ScopeViolationException ex, HttpServletRequest request) {
        recordFailureAudit(request, "SCOPE_VIOLATION", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(SecurityException ex, HttpServletRequest request) {
        recordFailureAudit(request, "SECURITY_ERROR", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // Not audited: hosting health probes and crawlers request unknown paths such as "/" constantly
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found",
                "No endpoint at /" + ex.getResourcePath(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        // Details stay server-side: driver/crypto messages can reveal collection, index or key internals
        log.error("Unhandled exception on {}", request != null ? request.getRequestURI() : "unknown", ex);
        recordFailureAudit(request, "INTERNAL_ERROR", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                "An unexpected error occurred.", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private void recordFailureAudit(HttpServletRequest request, String action, String reason) {
        String ip = request != null ? request.getRemoteAddr() : "unknown";
        String maskedKey = request != null ? (String) request.getAttribute(ApiKeyAuthFilter.MASKED_KEY_ATTR) : "none";
        String uri = request != null ? request.getRequestURI() : "";
        auditService.log(action, uri, "", ip, maskedKey, false, reason);
    }
}
