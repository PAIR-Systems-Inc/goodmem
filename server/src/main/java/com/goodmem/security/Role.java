package com.goodmem.security;

/**
 * Interface defining a role in the role-based access control (RBAC) system.
 *
 * <p>A role represents a named set of permissions that can be assigned to users. Each role
 * has a name, description, and a defined set of permissions. When a user is assigned a role,
 * they inherit all the permissions associated with that role.
 *
 * <p>This interface extends {@link PermissionChecker} to enable consistent permission checking
 * across both users and roles. The implementation determines whether a given permission is 
 * included in the role's permission set.
 *
 * <p>Typical usage involves:
 * <ul>
 *   <li>Creating predefined role implementations (see {@link Roles} enum)
 *   <li>Assigning roles to users
 *   <li>Checking user permissions through the role hierarchy
 * </ul>
 *
 * <p>The system supports a hierarchical approach where users can have multiple roles,
 * and permissions are aggregated across all assigned roles.
 */
public interface Role extends PermissionChecker {
  /**
   * Gets the unique name of this role.
   *
   * @return The role's name, typically in uppercase (e.g., "ADMIN", "USER")
   */
  String getName();

  /**
   * Gets the human-readable description of this role.
   *
   * @return A description explaining the purpose and scope of the role
   */
  String getDescription();
}
