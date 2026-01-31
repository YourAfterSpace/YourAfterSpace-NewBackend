package com.yourafterspace.yas_backend.exception;

import com.yourafterspace.yas_backend.dto.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String MDC_REQUEST_ID_KEY = "requestId";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });
    String requestId = MDC.get(MDC_REQUEST_ID_KEY);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("VALIDATION_ERROR", "Validation failed", errors, requestId));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
      ResourceNotFoundException ex) {
    String requestId = MDC.get(MDC_REQUEST_ID_KEY);
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error("NOT_FOUND", ex.getMessage(), null, requestId));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ApiResponse<Object>> handleBadRequestException(BadRequestException ex) {
    String requestId = MDC.get(MDC_REQUEST_ID_KEY);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("BAD_REQUEST", ex.getMessage(), null, requestId));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
      AuthenticationException ex) {
    String requestId = MDC.get(MDC_REQUEST_ID_KEY);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error("UNAUTHORIZED", ex.getMessage(), null, requestId));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Object>> handleIllegalStateException(IllegalStateException ex) {
    String requestId = MDC.get(MDC_REQUEST_ID_KEY);
    // Check if it's an authentication-related illegal state
    if (ex.getMessage() != null && ex.getMessage().contains("authenticated user")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.error("UNAUTHORIZED", "Authentication required", null, requestId));
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("BAD_REQUEST", ex.getMessage(), null, requestId));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
    // Never leak internal exception messages to clients; log with requestId for correlation.
    String requestId = MDC.get(MDC_REQUEST_ID_KEY);
    log.error("Unhandled exception (requestId={})", requestId, ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.", null, requestId));
  }
}
