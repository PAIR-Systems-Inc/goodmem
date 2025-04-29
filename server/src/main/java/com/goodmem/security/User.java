package com.goodmem.security;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.UUID;

/**
 * Interface representing a user in the authentication and authorization system.
 *
 * <p>This interface extends PermissionChecker to allow permission checks for users, while also
 * providing access to user properties from the underlying database user model.
 *
 * <p>The concrete implementation will internally hold a db.User record and delegate the appropriate
 * methods to it.
 */
public interface User extends PermissionChecker {
  /**
   * Gets the unique identifier for this user.
   *
   * @return The user's UUID
   */
  UUID getId();

  /**
   * Gets the user's email address.
   *
   * @return The email address or null if not provided
   */
  String getEmail();

  /**
   * Gets the user's display name.
   *
   * @return The display name or null if not provided
   */
  String getDisplayName();

  /**
   * Gets the timestamp when the user was activated.
   *
   * @return The activation timestamp or null if not activated
   */
  Instant getActiveDate();

  /**
   * Gets the timestamp when the user was or will be deactivated.
   *
   * @return The deactivation timestamp or null if not deactivated
   */
  Instant getInactiveDate();

  /**
   * Gets the timestamp of the user's last login.
   *
   * @return The last login timestamp or null if never logged in
   */
  Instant getLastLogin();

  /**
   * Gets additional provider-specific attributes.
   *
   * @return A JsonObject with additional attributes or null if none
   */
  JsonObject getAdditionalAttributes();

  /**
   * Checks if the user is active at the specified time.
   *
   * @param when The time to check against
   * @return true if the user is active at the specified time, false otherwise
   */
  boolean isActive(Instant when);

  /**
   * Checks if the user is currently active.
   *
   * @return true if the user is currently active, false otherwise
   */
  boolean isActive();
}
