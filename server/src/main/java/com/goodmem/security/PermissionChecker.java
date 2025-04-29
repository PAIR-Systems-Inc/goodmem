package com.goodmem.security;

/**
 * Interface defining the contract for permission checking.
 *
 * <p>This interface is the foundation of the role-based access control (RBAC) system. Implementers
 * (such as User and Role) can determine if they have specific permissions, providing a consistent
 * way to check authorization throughout the application.
 *
 * <p>Classes that implement this interface should maintain an internal set of permissions or have
 * access to a mechanism that can determine permission status, such as roles assigned to a user, or
 * permissions assigned to a role.
 *
 * <p>The interface provides both a fundamental check method {@link #hasPermission} and convenience
 * methods for checking multiple permissions with various logic (any/all).
 */
public interface PermissionChecker {
  /**
   * Checks if this entity has the specified permission.
   *
   * @param permission The permission to check
   * @return true if the entity has the permission, false otherwise
   */
  boolean hasPermission(Permission permission);

  /**
   * Checks if this entity has any of the specified permissions. Returns true if at least one of the
   * permissions is granted.
   *
   * @param permissions The permissions to check
   * @return true if the entity has at least one of the permissions, false if none
   */
  default boolean hasAnyPermission(Permission... permissions) {
    for (Permission p : permissions) {
      if (hasPermission(p)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if this entity has all of the specified permissions. Returns true only if all of the
   * permissions are granted.
   *
   * @param permissions The permissions to check
   * @return true if the entity has all of the permissions, false otherwise
   */
  default boolean hasAllPermissions(Permission... permissions) {
    for (Permission p : permissions) {
      if (!hasPermission(p)) {
        return false;
      }
    }
    return true;
  }
}
