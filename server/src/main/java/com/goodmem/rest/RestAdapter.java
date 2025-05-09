package com.goodmem.rest;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.google.protobuf.ByteString;
import io.javalin.http.Context;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import java.util.Map;
import org.tinylog.Logger;

/**
 * Base interface for REST adapters that handle REST API endpoints.
 * 
 * <p>This interface defines the common functionality that all service-specific
 * REST adapters should implement. It provides methods for error handling,
 * UUID conversion, and other utility functions needed by REST handlers.
 */
public interface RestAdapter {

  /**
   * Sets an error response with the specified status code and message.
   *
   * @param ctx The Javalin context to set the error on
   * @param statusCode The HTTP status code to set
   * @param message The error message to include in the response
   */
  default void setError(Context ctx, int statusCode, String message) {
    ctx.status(statusCode).json(Map.of("error", message));
    Logger.error("Error response: {} - {}", statusCode, message);
  }

  /**
   * Converts a hexadecimal UUID string to a ByteString.
   *
   * @param hexString The hexadecimal UUID string to convert
   * @return A StatusOr containing either the ByteString or an error status
   */
  default StatusOr<ByteString> convertHexToUuidBytes(String hexString) {
    if (Strings.isNullOrEmpty(hexString)) {
      return StatusOr.ofStatus(Status.invalidArgument("UUID cannot be null or empty"));
    }
    try {
      // Remove any hyphens or prefixes
      String cleanHex = hexString.replace("-", "").replace("0x", "");
      byte[] bytes = BaseEncoding.base16().decode(cleanHex.toUpperCase());
      if (bytes.length != 16) {
        return StatusOr.ofStatus(Status.invalidArgument("Invalid UUID format: incorrect length"));
      }
      return StatusOr.ofValue(ByteString.copyFrom(bytes));
    } catch (IllegalArgumentException e) {
      return StatusOr.ofStatus(Status.invalidArgument("Invalid UUID format: " + e.getMessage()));
    }
  }

  /**
   * Registers the REST endpoints handled by this adapter.
   * 
   * <p>Implementing classes should use this method to define the routes they handle
   * and map them to the appropriate handler methods.
   */
  void registerRoutes();
}