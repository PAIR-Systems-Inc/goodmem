package com.goodmem.common.status;

/**
 * Status codes that correspond to both gRPC status codes and HTTP status codes.
 * This provides a standardized set of error codes that can be used across different
 * transport protocols.
 */
public enum StatusCode {
    // Standard gRPC/HTTP status codes
    OK(200),                 // 200 OK
    CANCELLED(499),          // 499 Client Closed Request
    UNKNOWN(500),            // 500 Internal Server Error
    INVALID_ARGUMENT(400),   // 400 Bad Request
    DEADLINE_EXCEEDED(504),  // 504 Gateway Timeout
    NOT_FOUND(404),          // 404 Not Found
    ALREADY_EXISTS(409),     // 409 Conflict
    PERMISSION_DENIED(403),  // 403 Forbidden
    RESOURCE_EXHAUSTED(429), // 429 Too Many Requests
    FAILED_PRECONDITION(400),// 400 Bad Request (more specific: precondition failed)
    ABORTED(409),            // 409 Conflict
    OUT_OF_RANGE(400),       // 400 Bad Request (more specific: parameter out of range)
    UNIMPLEMENTED(501),      // 501 Not Implemented
    INTERNAL(500),           // 500 Internal Server Error
    UNAVAILABLE(503),        // 503 Service Unavailable
    DATA_LOSS(500),          // 500 Internal Server Error (more specific: data loss)
    UNAUTHENTICATED(401);    // 401 Unauthorized

    private final int httpCode;

    StatusCode(int httpCode) {
        this.httpCode = httpCode;
    }

    /**
     * Returns the corresponding HTTP status code.
     */
    public int getHttpCode() {
        return httpCode;
    }

    /**
     * Returns whether this status code represents a successful operation.
     */
    public boolean isSuccess() {
        return this == OK;
    }

    /**
     * Returns whether this status code represents an error.
     */
    public boolean isError() {
        return !isSuccess();
    }

    /**
     * Maps an HTTP status code to the closest matching StatusCode.
     * 
     * @param httpStatusCode the HTTP status code to convert
     * @return the corresponding StatusCode
     */
    public static StatusCode fromHttpStatus(int httpStatusCode) {
        switch (httpStatusCode) {
            case 200: return OK;
            case 400: return INVALID_ARGUMENT; // Could also be OUT_OF_RANGE or FAILED_PRECONDITION
            case 401: return UNAUTHENTICATED;
            case 403: return PERMISSION_DENIED;
            case 404: return NOT_FOUND;
            case 409: return ALREADY_EXISTS; // Could also be ABORTED
            case 429: return RESOURCE_EXHAUSTED;
            case 499: return CANCELLED;
            case 500: return INTERNAL; // Could also be UNKNOWN or DATA_LOSS
            case 501: return UNIMPLEMENTED;
            case 503: return UNAVAILABLE;
            case 504: return DEADLINE_EXCEEDED;
            default:
                // Map based on range
                if (httpStatusCode >= 400 && httpStatusCode < 500) {
                    return INVALID_ARGUMENT;
                } else if (httpStatusCode >= 500) {
                    return INTERNAL;
                } else {
                    return OK;
                }
        }
    }
}