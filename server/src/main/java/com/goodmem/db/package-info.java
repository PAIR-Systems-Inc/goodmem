/**
 * The database layer for the Goodmem application.
 *
 * <p>This package contains the record classes that represent database entities and the
 * corresponding helper classes that provide CRUD operations for each entity type.
 *
 * <p>The database layer follows a consistent pattern:
 *
 * <ul>
 *   <li>Each database table has a Java record class (e.g., {@code User}, {@code Space})
 *   <li>Each record class has a corresponding helper class with a plural name (e.g., {@code Users},
 *       {@code Spaces})
 *   <li>Helper classes provide static methods for common database operations
 *   <li>Database operations return {@code StatusOr<T>} to handle either success with a value or
 *       failure with a status
 * </ul>
 *
 * <p>All record classes include a method to convert the record to its corresponding Protocol Buffer
 * message.
 */
package com.goodmem.db;
