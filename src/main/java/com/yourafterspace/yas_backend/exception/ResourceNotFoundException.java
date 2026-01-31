package com.yourafterspace.yas_backend.exception;

/**
 * Exception thrown when a requested resource is not found.
 *
 * <p>This exception results in a 404 Not Found HTTP response.
 */
public class ResourceNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
