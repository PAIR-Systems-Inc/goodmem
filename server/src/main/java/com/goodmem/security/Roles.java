package com.goodmem.security;

/**
 * Enumeration of the predefined roles available in the system.
 *
 * <p>This enum provides the standard roles used throughout the application as part of the
 * role-based access control (RBAC) system. Each enum constant represents a distinct role with
 * its own set of permissions and responsibilities.
 *
 * <p>The defined roles follow a hierarchical structure:
 * <ul>
 *   <li>{@link #ROOT}: Highest level role with unrestricted access to all system functions
 *   <li>{@link #ADMIN}: Administrative role with broad system management capabilities
 *   <li>{@link #USER}: Standard user role with basic system access
 * </ul>
 *
 * <p>Each role is implemented as an anonymous class extending either {@link Role} directly
 * (for roles with special permission logic like ROOT and ADMIN) or {@link BaseRoleImpl}
 * (for roles with standard permission handling like USER).
 *
 * <p>Usage:
 * <pre>
 * // Get the ADMIN role implementation
 * Role adminRole = Roles.ADMIN.role();
 *
 * // Check if the admin role has a specific permission
 * boolean hasPermission = adminRole.hasPermission(Permission.DISPLAY_USER_ANY);
 * </pre>
 */
public enum Roles {
  /**
   * The ROOT role represents the system owner with unlimited permissions.
   * This role automatically has access to all permissions in the system.
   */
  ROOT(
      new Role() {
        @Override
        public String getName() {
          return "ROOT";
        }

        @Override
        public String getDescription() {
          return "The root user and the account owner.";
        }

        /**
         * The ROOT role has access to all permissions in the system without restriction.
         * 
         * @param permission Any permission in the system
         * @return Always returns true
         */
        @Override
        public boolean hasPermission(Permission permission) {
          return true;
        }
      }),
      
  /**
   * The ADMIN role provides broad administrative access to manage the system.
   * Administrators have access to nearly all system functions but may be restricted
   * from certain sensitive operations reserved for the ROOT role.
   */
  ADMIN(
      new Role() {
        @Override
        public String getName() {
          return "ADMIN";
        }

        @Override
        public String getDescription() {
          return "A system administrator.";
        }

        /**
         * The ADMIN role has access to all standard permissions in the system.
         * 
         * @param permission Any permission in the system
         * @return Always returns true (though future implementation may restrict certain permissions)
         */
        @Override
        public boolean hasPermission(Permission permission) {
          return true;
        }
      }),
      
  /**
   * The USER role represents a standard system user with basic permissions.
   * Users have limited access focused on managing their own resources and
   * using the core functionality of the system.
   */
  USER(
      new BaseRoleImpl(
          Permission.DISPLAY_USER_OWN,
          Permission.CREATE_SPACE_OWN,
          Permission.DISPLAY_SPACE_OWN,
          Permission.UPDATE_SPACE_OWN,
          Permission.DELETE_SPACE_OWN) {
        @Override
        public String getName() {
          return "USER";
        }

        @Override
        public String getDescription() {
          return "A system user.";
        }
      });

  private final Role roleImpl;

  /**
   * Constructs a role enum constant with the specified role implementation.
   *
   * @param roleImpl The concrete implementation of the Role interface
   */
  Roles(Role roleImpl) {
    this.roleImpl = roleImpl;
  }

  /**
   * Gets the role implementation associated with this enum constant.
   *
   * @return The concrete Role implementation
   */
  public Role role() {
    return roleImpl;
  }
}
