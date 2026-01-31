package com.yourafterspace.yas_backend.exception;

/**
 * Exception thrown when a request is invalid or malformed.
 *
 * <p>This exception is typically used for validation errors or business rule violations. It results
 * in a 400 Bad Request HTTP response.
 */
public class BadRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public BadRequestException(String message) {
    super(message);
  }

  public BadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
