package com.goodmem.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;

/**
 * Default implementation of the User interface for the role-based access control system.
 *
 * <p>This class implements the User interface by:
 *
 * <ol>
 *   <li>Wrapping a database User entity to provide access to user properties
 *   <li>Maintaining a set of roles assigned to the user
 *   <li>Aggregating permissions from all roles to provide permission checking
 * </ol>
 *
 * <p>The permission checking is based on the transitive permissions granted by all roles assigned
 * to the user. If any of the user's roles grants a permission, the user is considered to have that
 * permission.
 */
public class DefaultUserImpl implements User {

  private final com.goodmem.db.User dbUser;
  private final Set<Role> roles;

  /**
   * Creates a new DefaultUserImpl instance with the specified db.User and roles.
   *
   * <p>The user will have all permissions granted by any of the provided roles. Permissions are
   * aggregated across all roles, following the principle that if any role grants a permission, the
   * user has that permission.
   *
   * @param dbUser The database user entity to wrap
   * @param roles The set of roles assigned to this user
   */
  public DefaultUserImpl(com.goodmem.db.User dbUser, Set<Role> roles) {
    this.dbUser = dbUser;
    this.roles = ImmutableSet.copyOf(roles);
  }

  public DefaultUserImpl(com.goodmem.db.User dbUser, Role role) {
    this.dbUser = dbUser;
    this.roles = ImmutableSet.of(role);
  }

  @Override
  public UUID getId() {
    return dbUser.userId();
  }

  @Override
  public String getEmail() {
    return dbUser.email();
  }

  @Override
  public String getDisplayName() {
    return dbUser.displayName();
  }

  @Override
  public Instant getActiveDate() {
    return Instant.MIN;
  }

  @Override
  public Instant getInactiveDate() {
    return Instant.MAX;
  }

  @Override
  public Instant getLastLogin() {
    return Instant.now();
  }

  @Override
  public JsonObject getAdditionalAttributes() {
    return new JsonObject();
  }

  @Override
  public boolean isActive(Instant when) {
    return getActiveDate().isBefore(when) && getInactiveDate().isAfter(when);
  }

  @Override
  public boolean isActive() {
    return isActive(Instant.now());
  }

  /**
   * Checks if this user has the specified permission.
   *
   * @param permission The permission to check
   * @return true if the user has the permission, false otherwise
   */
  @Override
  public boolean hasPermission(Permission permission) {
    for (Role r : roles) {
      if (r.hasPermission(permission)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", getId())
        .add("email", getEmail())
        .add("displayName", getDisplayName())
        .add("active", isActive())
        .add("roleCount", roles.size())
        .toString();
  }
}
