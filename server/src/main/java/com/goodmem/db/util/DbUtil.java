package com.goodmem.db.util;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.util.Timestamps;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Utility methods for database operations. */
public final class DbUtil {

  /**
   * Default vector size (dimensions) for embeddings. The OpenAI Ada-002 model uses 1536 dimensions.
   */
  public static final int DEFAULT_VECTOR_DIMENSIONS = 1536;

  private DbUtil() {
    // Utility class, no instances
  }

  /** Converts a java.sql.Timestamp to java.time.Instant. */
  @Nonnull
  public static Instant toInstant(java.sql.Timestamp timestamp) {
    if (timestamp == null) {
      throw new IllegalArgumentException("Timestamp cannot be null");
    }
    return timestamp.toInstant();
  }

  /** Converts a java.time.Instant to java.sql.Timestamp. */
  @Nonnull
  public static java.sql.Timestamp toSqlTimestamp(Instant instant) {
    if (instant == null) {
      throw new IllegalArgumentException("Instant cannot be null");
    }
    return java.sql.Timestamp.from(instant);
  }

  /** Converts a java.time.Instant to a Google Protocol Buffer Timestamp. */
  @Nonnull
  public static com.google.protobuf.Timestamp toProtoTimestamp(Instant instant) {
    if (instant == null) {
      throw new IllegalArgumentException("Instant cannot be null");
    }
    return Timestamps.fromMillis(instant.toEpochMilli());
  }

  /** Converts a Google Protocol Buffer Timestamp to a java.time.Instant. */
  @Nonnull
  public static Instant fromProtoTimestamp(com.google.protobuf.Timestamp timestamp) {
    if (timestamp == null) {
      throw new IllegalArgumentException("Timestamp cannot be null");
    }
    return Instant.ofEpochMilli(Timestamps.toMillis(timestamp));
  }

  /** Gets a UUID from a ResultSet column using column name. */
  @Nonnull
  public static StatusOr<UUID> getUuid(ResultSet rs, String columnName) {
    try {
      UUID uuid = rs.getObject(columnName, UUID.class);
      if (rs.wasNull() || uuid == null) {
        return StatusOr.ofStatus(Status.invalidArgument("Column " + columnName + " is null"));
      }
      return StatusOr.ofValue(uuid);
    } catch (SQLException e) {
      return StatusOr.ofStatus(Status.internal("Failed to get UUID: " + e.getMessage(), e));
    }
  }
  
  /** Gets a UUID from a ResultSet column using column index. */
  @Nonnull
  public static StatusOr<UUID> getUuid(ResultSet rs, String columnName, int columnIndex) {
    try {
      UUID uuid = rs.getObject(columnIndex, UUID.class);
      if (rs.wasNull() || uuid == null) {
        return StatusOr.ofStatus(Status.invalidArgument("Column " + columnName + " at index " + columnIndex + " is null"));
      }
      return StatusOr.ofValue(uuid);
    } catch (SQLException e) {
      return StatusOr.ofStatus(Status.internal("Failed to get UUID: " + e.getMessage(), e));
    }
  }

  /**
   * Gets an optional UUID from a ResultSet column, returning Optional.empty() if the column is
   * null.
   */
  public static StatusOr<Optional<UUID>> getOptionalUuid(ResultSet rs, String columnName) {
    try {
      UUID uuid = rs.getObject(columnName, UUID.class);
      if (rs.wasNull() || uuid == null) {
        return StatusOr.ofValue(Optional.empty()); // Empty Optional for null UUIDs
      }
      return StatusOr.ofValue(Optional.of(uuid));
    } catch (SQLException e) {
      return StatusOr.ofStatus(Status.internal("Failed to get UUID: " + e.getMessage(), e));
    }
  }

  /** Gets an Instant from a ResultSet column using column name. */
  @Nonnull
  public static StatusOr<Instant> getInstant(ResultSet rs, String columnName) {
    try {
      java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
      if (rs.wasNull() || timestamp == null) {
        return StatusOr.ofStatus(Status.invalidArgument("Column " + columnName + " is null"));
      }
      return StatusOr.ofValue(timestamp.toInstant());
    } catch (SQLException e) {
      return StatusOr.ofStatus(Status.internal("Failed to get Instant: " + e.getMessage(), e));
    }
  }
  
  /** Gets an Instant from a ResultSet column using column index. */
  @Nonnull
  public static StatusOr<Instant> getInstant(ResultSet rs, String columnName, int columnIndex) {
    try {
      java.sql.Timestamp timestamp = rs.getTimestamp(columnIndex);
      if (rs.wasNull() || timestamp == null) {
        return StatusOr.ofStatus(Status.invalidArgument("Column " + columnName + " at index " + columnIndex + " is null"));
      }
      return StatusOr.ofValue(timestamp.toInstant());
    } catch (SQLException e) {
      return StatusOr.ofStatus(Status.internal("Failed to get Instant: " + e.getMessage(), e));
    }
  }

  /**
   * Gets an optional Instant from a ResultSet column, returning Optional.empty() if the column is
   * null.
   */
  public static StatusOr<Optional<Instant>> getOptionalInstant(ResultSet rs, String columnName) {
    try {
      java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
      if (rs.wasNull() || timestamp == null) {
        return StatusOr.ofValue(Optional.empty()); // Empty Optional for null timestamps
      }
      return StatusOr.ofValue(Optional.of(timestamp.toInstant()));
    } catch (SQLException e) {
      return StatusOr.ofStatus(Status.internal("Failed to get Instant: " + e.getMessage(), e));
    }
  }

  /** Truncates a string to the given max length if it is longer. */
  @Nonnull
  public static String truncate(String str, int maxLength) {
    if (str == null) {
      return "";
    }
    return str.length() > maxLength ? str.substring(0, maxLength) : str;
  }

  /**
   * Converts a PostgreSQL JSONB map to a Java Map<String, String>.
   * Uses Gson to parse the JSON string from the database.
   *
   * @param rs The ResultSet containing the JSONB column
   * @param columnName The name of the column containing the JSONB data
   * @return StatusOr containing the parsed Map<String, String> or an error
   */
  @Nonnull
  public static StatusOr<Map<String, String>> parseJsonbToMap(ResultSet rs, String columnName) {
    try {
      String jsonbStr = rs.getString(columnName);
      if (rs.wasNull() || Strings.isNullOrEmpty(jsonbStr)) {
        return StatusOr.ofValue(Map.of()); // Empty map for null or empty JSONB
      }

      try {
        // Use Gson to parse the JSON string into a Map<String, String>
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        
        return StatusOr.ofValue(gson.fromJson(jsonbStr, mapType));
      } catch (JsonSyntaxException e) {
        return StatusOr.ofStatus(Status.internal("Failed to parse JSON: " + e.getMessage(), e));
      }
    } catch (SQLException e) {
      return StatusOr.ofStatus(Status.internal("Failed to retrieve JSONB: " + e.getMessage(), e));
    }
  }
  
  /**
   * Converts a Java Map<String, String> to a JSON string for storage in PostgreSQL JSONB column.
   *
   * @param map The Map<String, String> to convert to JSON
   * @return StatusOr containing the JSON string or an error
   */
  @Nonnull
  public static StatusOr<String> mapToJsonb(Map<String, String> map) {
    if (map == null || map.isEmpty()) {
      return StatusOr.ofValue("{}"); // Empty JSON object for null or empty map
    }
    
    try {
      Gson gson = new Gson();
      return StatusOr.ofValue(gson.toJson(map));
    } catch (Exception e) {
      return StatusOr.ofStatus(Status.internal("Failed to serialize map to JSON: " + e.getMessage(), e));
    }
  }
  
  /**
   * Sets a Map<String, String> as a JSONB value in a PreparedStatement.
   *
   * @param stmt The PreparedStatement to set the parameter in
   * @param parameterIndex The index of the parameter to set (1-based)
   * @param map The Map<String, String> to set as JSONB
   * @return Status indicating success or failure
   */
  @Nonnull
  public static Status setJsonbParameter(PreparedStatement stmt, int parameterIndex, Map<String, String> map) {
    try {
      if (map == null || map.isEmpty()) {
        // For empty maps, use a special JSON type to ensure it's stored as a valid JSON object
        stmt.setObject(parameterIndex, "{}", Types.OTHER);
        return Status.ok();
      }
      
      StatusOr<String> jsonOr = mapToJsonb(map);
      if (jsonOr.isNotOk()) {
        return jsonOr.getStatus();
      }
      
      stmt.setObject(parameterIndex, jsonOr.getValue(), Types.OTHER);
      return Status.ok();
    } catch (SQLException e) {
      return Status.internal("Failed to set JSONB parameter: " + e.getMessage(), e);
    }
  }

  /**
   * Gets a vector embedding from a ResultSet column.
   *
   * <p>PostgreSQL pgvector stores vector data in a binary format. This method extracts it and
   * converts it to a float array.
   */
  @Nonnull
  public static StatusOr<float[]> getVector(ResultSet rs, String columnName) {
    try {
      // The specific way to extract vectors depends on the pgvector version and JDBC driver
      // This implementation uses getString, which returns a string representation like "[1,2,3]"
      String vectorStr = rs.getString(columnName);
      if (rs.wasNull() || vectorStr == null) {
        return StatusOr.ofStatus(Status.invalidArgument("Column " + columnName + " is null"));
      }

      // Convert the string representation to a float array
      // Strip brackets and split by commas
      String cleanStr = vectorStr.replace("[", "").replace("]", "").trim();
      String[] parts = cleanStr.split(",");
      float[] vector = new float[parts.length];

      for (int i = 0; i < parts.length; i++) {
        vector[i] = Float.parseFloat(parts[i].trim());
      }

      return StatusOr.ofValue(vector);
    } catch (SQLException | NumberFormatException e) {
      return StatusOr.ofStatus(Status.internal("Failed to get vector: " + e.getMessage(), e));
    }
  }

  /**
   * Formats a float array as a PostgreSQL vector literal for use with pgvector.
   *
   * @param vector The float array to format
   * @return A string representation of the vector that can be cast to the vector type
   */
  @Nonnull
  public static String formatVector(float[] vector) {
    if (vector == null) {
      throw new IllegalArgumentException("Vector cannot be null");
    }

    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(vector[i]);
    }
    sb.append("]");
    return sb.toString();
  }
}
