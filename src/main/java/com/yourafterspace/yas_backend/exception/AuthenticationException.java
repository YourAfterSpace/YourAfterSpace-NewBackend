package com.yourafterspace.yas_backend.exception;

/** Exception thrown when authentication is required but not present. */
public class AuthenticationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AuthenticationException(String message) {
    super(message);
  }

  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
