/**
 * Contains classes for error handling and status reporting.
 * 
 * <p>This package provides a consistent way to represent operation statuses and errors
 * without relying on exceptions for control flow. The central classes are:
 * 
 * <ul>
 *   <li>{@link com.goodmem.common.status.StatusCode} - Enum of possible status codes, aligned with gRPC and HTTP status codes</li>
 *   <li>{@link com.goodmem.common.status.Status} - Represents a status with an optional message and cause</li>
 *   <li>{@link com.goodmem.common.status.StatusOr} - Container that holds either a successful value or an error status</li>
 * </ul>
 * 
 * <p>The StatusOr pattern allows methods to return either success values or detailed
 * error information without throwing exceptions. This makes error handling more explicit
 * and enables better composition of operations that might fail.
 * 
 * <p>Example usage:
 * <pre>
 * // Method that returns either a User or an error status
 * public StatusOr&lt;User&gt; getUserById(UUID userId) {
 *     if (userId == null) {
 *         return StatusOr.ofStatus(Status.invalidArgument("User ID cannot be null"));
 *     }
 *     
 *     try {
 *         Optional&lt;User&gt; user = repository.findUserById(userId);
 *         if (user.isPresent()) {
 *             return StatusOr.ofValue(user.get());
 *         } else {
 *             return StatusOr.ofStatus(Status.notFound("User not found with ID: " + userId));
 *         }
 *     } catch (Exception e) {
 *         return StatusOr.ofException(e);
 *     }
 * }
 * 
 * // Calling code that handles the result
 * StatusOr&lt;User&gt; result = getUserById(userId);
 * if (result.isOk()) {
 *     // Handle success case
 *     User user = result.getValue();
 *     // Process the user...
 * } else {
 *     // Handle error case
 *     Status error = result.getStatus();
 *     logger.error("Failed to get user: {}", error);
 *     // Handle the error based on the status code...
 * }
 * </pre>
 */
package com.goodmem.common.status;