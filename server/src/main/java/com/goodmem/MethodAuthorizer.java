package com.goodmem;

import java.util.HashSet;
import java.util.Set;

/**
 * A simple class to determine if specific methods should be allowed without authentication.
 * This is used to create public endpoints for functionality like system initialization.
 */
public class MethodAuthorizer {
  private final Set<String> allowedMethods = new HashSet<>();

  /**
   * Adds a method to the list of allowed methods that can be called without authentication.
   * 
   * @param fullMethodName the full method name in the format "service/method" (e.g. "goodmem.v1.UserService/InitializeSystem")
   * @return this instance for method chaining
   */
  public MethodAuthorizer allowMethod(String fullMethodName) {
    allowedMethods.add(fullMethodName);
    return this;
  }

  /**
   * Checks if a method is allowed to be called without authentication.
   * 
   * @param fullMethodName the full method name in the format "service/method"
   * @return true if the method is allowed without authentication, false otherwise
   */
  public boolean isMethodAllowed(String fullMethodName) {
    return allowedMethods.contains(fullMethodName);
  }
}