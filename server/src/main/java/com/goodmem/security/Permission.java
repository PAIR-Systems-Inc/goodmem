package com.goodmem.security;

/**
 * Enumeration of permissions used in the system's role-based access control (RBAC).
 *
 * <p>This enum defines the granular permissions that can be assigned to roles and checked against
 * user actions. Each permission represents a specific capability within the system.
 *
 * <p>Permissions are organized by resource type and action, following the pattern:
 * [ACTION]_[RESOURCE]_[SCOPE], where:
 * <ul>
 *   <li>ACTION: The operation being performed (e.g., DISPLAY, CREATE, UPDATE, DELETE)
 *   <li>RESOURCE: The entity being acted upon (e.g., USER, SPACE, MEMORY)
 *   <li>SCOPE: The range of entities affected (e.g., OWN, ANY)
 * </ul>
 *
 * <p>These permissions are used in conjunction with {@link Role} and {@link User} implementations 
 * to enforce access control throughout the application.
 */
public enum Permission {

  // User permissions
  /** Permission for a user to view himself. */
  DISPLAY_USER_OWN,

  /** Permission for a user to view any user in the system. */
  DISPLAY_USER_ANY,
  
  // Space permissions
  /** Permission for a user to create spaces for themselves. */
  CREATE_SPACE_OWN,
  
  /** Permission for a user to create spaces for any user. */
  CREATE_SPACE_ANY,
  
  /** Permission for a user to view their own spaces. */
  DISPLAY_SPACE_OWN,
  
  /** Permission for a user to view any space in the system. */
  DISPLAY_SPACE_ANY,
  
  /** Permission for a user to update their own spaces. */
  UPDATE_SPACE_OWN,
  
  /** Permission for a user to update any space in the system. */
  UPDATE_SPACE_ANY,
  
  /** Permission for a user to delete their own spaces. */
  DELETE_SPACE_OWN,
  
  /** Permission for a user to delete any space in the system. */
  DELETE_SPACE_ANY,
  
  /** Permission for a user to list their own spaces. */
  LIST_SPACE_OWN,
  
  /** Permission for a user to list any spaces in the system. */
  LIST_SPACE_ANY;
}
