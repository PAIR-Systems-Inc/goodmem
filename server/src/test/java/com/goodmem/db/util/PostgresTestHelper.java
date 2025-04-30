package com.goodmem.db.util;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Helper class for setting up PostgreSQL test containers with pgvector support. Provides consistent
 * initialization for all database tests.
 */
public class PostgresTestHelper {

  // Common classpath resources for schema files
  private static final String EXTENSIONS_SQL_PATH = "/db/00-extensions.sql";
  private static final String SCHEMA_SQL_PATH = "/db/01-schema.sql";
  private static final String TC_EXTENSIONS_SQL_PATH = "db/00-extensions.sql";

  /**
   * Creates a PostgreSQL container configured with the pgvector extension and initialized with
   * schema files. Uses classpath resources to load schema files.
   *
   * @param databaseName The name to use for the test database
   * @return A configured PostgreSQLContainer ready to start
   */
  public static PostgreSQLContainer<?> createPostgresContainer(String databaseName) {
    return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
        .withDatabaseName(databaseName)
        .withUsername("goodmem")
        .withPassword("goodmem")
        // Initialize with extensions first to make functions available
        .withInitScript(TC_EXTENSIONS_SQL_PATH);
  }

  /**
   * Creates a JDBC connection to the PostgreSQL container.
   *
   * @param container The PostgreSQL container to connect to
   * @return A JDBC Connection to the database
   * @throws SQLException If connection fails
   */
  public static Connection createConnection(PostgreSQLContainer<?> container) throws SQLException {
    return DriverManager.getConnection(
        container.getJdbcUrl(), container.getUsername(), container.getPassword());
  }

  /**
   * Initializes the database schema in a running PostgreSQL container. This method should be called
   * after the container is started.
   *
   * @param connection The JDBC connection to use
   * @param testClass The test class that is using this helper (used to locate resources)
   * @throws RuntimeException If the schema files cannot be found or executed
   */
  public static void initializeSchema(Connection connection, Class<?> testClass) {
    try (var stmt = connection.createStatement()) {
      // Read schema file from classpath
      var schemaUrl = testClass.getResource(SCHEMA_SQL_PATH);
      if (schemaUrl == null) {
        throw new RuntimeException("Schema file not found: " + SCHEMA_SQL_PATH);
      }

      String schema = new String(java.nio.file.Files.readAllBytes(Path.of(schemaUrl.toURI())));

      // Execute the schema
      stmt.execute(schema);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize database schema", e);
    }
  }

  /**
   * Starts a PostgreSQL container, creates a connection, and initializes the schema. This is a
   * convenience method that combines multiple steps into one.
   *
   * @param databaseName The name to use for the test database
   * @param testClass The test class that is using this helper
   * @return A PostgresContext containing the container and connection
   * @throws SQLException If database connection fails
   */
  public static PostgresContext setupPostgres(String databaseName, Class<?> testClass)
      throws SQLException {
    PostgreSQLContainer<?> container = createPostgresContainer(databaseName);
    container.start();

    Connection connection = createConnection(container);
    initializeSchema(connection, testClass);

    return new PostgresContext(container, connection);
  }

  /**
   * Context object that holds the PostgreSQL container and connection. Provides convenient access
   * to both objects.
   */
  public static class PostgresContext {
    private final PostgreSQLContainer<?> container;
    private final Connection connection;

    public PostgresContext(PostgreSQLContainer<?> container, Connection connection) {
      this.container = container;
      this.connection = connection;
    }

    public PostgreSQLContainer<?> getContainer() {
      return container;
    }

    public Connection getConnection() {
      return connection;
    }

    /**
     * Closes the connection and stops the container. This should be called in test tearDown
     * methods.
     */
    public void close() {
      try {
        if (connection != null && !connection.isClosed()) {
          connection.close();
        }
      } catch (SQLException e) {
        // Log but continue with cleanup
        System.err.println("Error closing connection: " + e.getMessage());
      }

      if (container != null && container.isRunning()) {
        container.stop();
      }
    }
  }

  /**
   * Returns the path to the extensions SQL file in the classpath.
   *
   * @return Path to extensions SQL file
   */
  public static String getExtensionsSqlPath() {
    return EXTENSIONS_SQL_PATH;
  }

  /**
   * Returns the path to the schema SQL file in the classpath.
   *
   * @return Path to schema SQL file
   */
  public static String getSchemaSqlPath() {
    return SCHEMA_SQL_PATH;
  }

  /**
   * Returns the path to the extensions SQL file for TestContainers.
   *
   * @return Path to extensions SQL file for TestContainers
   */
  public static String getTcExtensionsSqlPath() {
    return TC_EXTENSIONS_SQL_PATH;
  }
}
