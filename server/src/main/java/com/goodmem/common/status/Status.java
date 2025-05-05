package com.goodmem.common.status;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Represents the status of an operation, possibly with additional error details. This class is
 * inspired by the gRPC Status concept and provides a unified way to represent success or various
 * error conditions.
 */
public class Status {
  private final StatusCode code;
  private final String message;
  private final Throwable cause;

  private Status(StatusCode code, String message, Throwable cause) {
    this.code = Objects.requireNonNull(code);
    this.message = message;
    this.cause = cause;
  }

  /** Creates a new status with the given code and message. */
  public static Status of(StatusCode code, String message) {
    return new Status(code, message, null);
  }

  /** Creates a new status with the given code, message, and cause. */
  public static Status of(StatusCode code, String message, Throwable cause) {
    return new Status(code, message, cause);
  }

  /** Creates a new OK status. */
  public static Status ok() {
    return new Status(StatusCode.OK, null, null);
  }

  /** Creates a new NOT_FOUND status with the given message. */
  public static Status notFound(String message) {
    return new Status(StatusCode.NOT_FOUND, message, null);
  }

  /** Creates a new INTERNAL status with the given message and cause. */
  public static Status internal(String message, Throwable cause) {
    return new Status(StatusCode.INTERNAL, message, cause);
  }

  /** Creates a new INVALID_ARGUMENT status with the given message. */
  public static Status invalidArgument(String message) {
    return new Status(StatusCode.INVALID_ARGUMENT, message, null);
  }
  
  /** Creates a new INVALID status with the given message. */
  public static Status invalid(String message) {
    return new Status(StatusCode.INVALID_ARGUMENT, message, null);
  }

  /** Creates a new ALREADY_EXISTS status with the given message. */
  public static Status alreadyExists(String message) {
    return new Status(StatusCode.ALREADY_EXISTS, message, null);
  }

  /** Creates a new UNIMPLEMENTED status with the given message. */
  public static Status unimplemented(String message) {
    return new Status(StatusCode.UNIMPLEMENTED, message, null);
  }

  /** Creates a new UNAUTHENTICATED status with the given message. */
  public static Status unauthenticated(String message) {
    return new Status(StatusCode.UNAUTHENTICATED, message, null);
  }

  /** Creates a new PERMISSION_DENIED status with the given message. */
  public static Status permissionDenied(String message) {
    return new Status(StatusCode.PERMISSION_DENIED, message, null);
  }

  /** Creates a new RESOURCE_EXHAUSTED status with the given message. */
  public static Status resourceExhausted(String message) {
    return new Status(StatusCode.RESOURCE_EXHAUSTED, message, null);
  }

  /** Creates a new DEADLINE_EXCEEDED status with the given message. */
  public static Status deadlineExceeded(String message) {
    return new Status(StatusCode.DEADLINE_EXCEEDED, message, null);
  }

  /** Returns the code for this status. */
  @Nonnull
  public StatusCode getCode() {
    return code;
  }

  /** Returns the HTTP status code corresponding to this status. */
  public int getHttpCode() {
    return code.getHttpCode();
  }

  /** Returns the message for this status, or null if there is no message. */
  public String getMessage() {
    return message;
  }

  /** Returns the cause of this status, or null if there is no cause. */
  public Throwable getCause() {
    return cause;
  }

  /** Returns true if this status represents an error (i.e., the code is not OK). */
  public boolean isError() {
    return code != StatusCode.OK;
  }

  /** Returns true if this status is OK. */
  public boolean isOk() {
    return code == StatusCode.OK;
  }

  @Override
  public String toString() {
    if (message == null) {
      return code.toString();
    }
    return code + ": " + message;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Status other = (Status) obj;
    return code == other.code
        && Objects.equals(message, other.message)
        && Objects.equals(cause, other.cause);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, cause);
  }
}
