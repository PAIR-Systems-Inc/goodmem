package com.goodmem.db.util;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents the status of an operation.
 * This can be used to communicate errors or other status conditions.
 */
public class Status {
    public enum Code {
        OK,
        CANCELLED,
        UNKNOWN,
        INVALID_ARGUMENT,
        DEADLINE_EXCEEDED,
        NOT_FOUND,
        ALREADY_EXISTS,
        PERMISSION_DENIED,
        RESOURCE_EXHAUSTED,
        FAILED_PRECONDITION,
        ABORTED,
        OUT_OF_RANGE,
        UNIMPLEMENTED,
        INTERNAL,
        UNAVAILABLE,
        DATA_LOSS,
        UNAUTHENTICATED
    }

    private final Code code;
    private final String message;
    private final Throwable cause;

    private Status(Code code, String message, Throwable cause) {
        this.code = Objects.requireNonNull(code);
        this.message = message;
        this.cause = cause;
    }

    /**
     * Creates a new status with the given code and message.
     */
    public static Status of(Code code, String message) {
        return new Status(code, message, null);
    }

    /**
     * Creates a new status with the given code, message, and cause.
     */
    public static Status of(Code code, String message, Throwable cause) {
        return new Status(code, message, cause);
    }

    /**
     * Creates a new OK status.
     */
    public static Status ok() {
        return new Status(Code.OK, null, null);
    }

    /**
     * Creates a new NOT_FOUND status with the given message.
     */
    public static Status notFound(String message) {
        return new Status(Code.NOT_FOUND, message, null);
    }

    /**
     * Creates a new INTERNAL status with the given message and cause.
     */
    public static Status internal(String message, Throwable cause) {
        return new Status(Code.INTERNAL, message, cause);
    }

    /**
     * Creates a new INVALID_ARGUMENT status with the given message.
     */
    public static Status invalidArgument(String message) {
        return new Status(Code.INVALID_ARGUMENT, message, null);
    }

    /**
     * Creates a new ALREADY_EXISTS status with the given message.
     */
    public static Status alreadyExists(String message) {
        return new Status(Code.ALREADY_EXISTS, message, null);
    }
    
    /**
     * Creates a new UNIMPLEMENTED status with the given message.
     */
    public static Status unimplemented(String message) {
        return new Status(Code.UNIMPLEMENTED, message, null);
    }

    /**
     * Returns the code for this status.
     */
    @Nonnull
    public Code getCode() {
        return code;
    }

    /**
     * Returns the message for this status, or null if there is no message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the cause of this status, or null if there is no cause.
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * Returns true if this status represents an error (i.e., the code is not OK).
     */
    public boolean isError() {
        return code != Code.OK;
    }

    /**
     * Returns true if this status is OK.
     */
    public boolean isOk() {
        return code == Code.OK;
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