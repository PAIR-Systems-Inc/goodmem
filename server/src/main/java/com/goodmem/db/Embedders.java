package com.goodmem.db;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.DbUtil;
import com.google.common.collect.ImmutableList;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

/** DAO helper class for the 'embedder' table. */
public final class Embedders {

  private Embedders() {
    // Utility class, no instances
  }

  /**
   * Loads all embedders.
   *
   * @param conn an open JDBC connection
   * @return StatusOr containing a list of Embedder objects or an error
   */
  @Nonnull
  public static StatusOr<List<Embedder>> loadAll(Connection conn) {
    String sql =
        """
        SELECT embedder_id, display_name, description, provider_type, endpoint_url, api_path,
               model_identifier, dimensionality, max_sequence_length, supported_modalities,
               credentials, labels, version, monitoring_endpoint,
               owner_id, created_at, updated_at, created_by_id, updated_by_id
          FROM embedder
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      List<Embedder> result = new ArrayList<>();
      while (rs.next()) {
        StatusOr<Embedder> embedderOr = extractEmbedder(rs);
        if (embedderOr.isNotOk()) {
          return StatusOr.ofStatus(embedderOr.getStatus());
        }
        result.add(embedderOr.getValue());
      }
      return StatusOr.ofValue(ImmutableList.copyOf(result));
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads a single embedder by ID.
   *
   * @param conn an open JDBC connection
   * @param embedderId the UUID of the embedder to load
   * @return StatusOr containing an Optional Embedder or an error
   */
  @Nonnull
  public static StatusOr<Optional<Embedder>> loadById(Connection conn, UUID embedderId) {
    String sql =
        """
        SELECT embedder_id, display_name, description, provider_type, endpoint_url, api_path,
               model_identifier, dimensionality, max_sequence_length, supported_modalities,
               credentials, labels, version, monitoring_endpoint,
               owner_id, created_at, updated_at, created_by_id, updated_by_id
          FROM embedder
         WHERE embedder_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, embedderId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          StatusOr<Embedder> embedderOr = extractEmbedder(rs);
          if (embedderOr.isNotOk()) {
            return StatusOr.ofStatus(embedderOr.getStatus());
          }
          return StatusOr.ofValue(Optional.of(embedderOr.getValue()));
        }
        return StatusOr.ofValue(Optional.empty());
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads embedders by owner ID.
   *
   * @param conn an open JDBC connection
   * @param ownerId the owner user ID to load embedders for
   * @return StatusOr containing a list of Embedder objects or an error
   */
  @Nonnull
  public static StatusOr<List<Embedder>> loadByOwnerId(Connection conn, UUID ownerId) {
    String sql =
        """
        SELECT embedder_id, display_name, description, provider_type, endpoint_url, api_path,
               model_identifier, dimensionality, max_sequence_length, supported_modalities,
               credentials, labels, version, monitoring_endpoint,
               owner_id, created_at, updated_at, created_by_id, updated_by_id
          FROM embedder
         WHERE owner_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, ownerId);
      try (ResultSet rs = stmt.executeQuery()) {
        List<Embedder> result = new ArrayList<>();
        while (rs.next()) {
          StatusOr<Embedder> embedderOr = extractEmbedder(rs);
          if (embedderOr.isNotOk()) {
            return StatusOr.ofStatus(embedderOr.getStatus());
          }
          result.add(embedderOr.getValue());
        }
        return StatusOr.ofValue(ImmutableList.copyOf(result));
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads embedders by provider type.
   *
   * @param conn an open JDBC connection
   * @param providerType the provider type to load embedders for
   * @return StatusOr containing a list of Embedder objects or an error
   */
  @Nonnull
  public static StatusOr<List<Embedder>> loadByProviderType(
      Connection conn, EmbedderProviderType providerType) {
    String sql =
        """
        SELECT embedder_id, display_name, description, provider_type, endpoint_url, api_path,
               model_identifier, dimensionality, max_sequence_length, supported_modalities,
               credentials, labels, version, monitoring_endpoint,
               owner_id, created_at, updated_at, created_by_id, updated_by_id
          FROM embedder
         WHERE provider_type = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      // Cast the string to the provider_type_enum PostgreSQL type
      stmt.setObject(1, providerType.toDatabaseValue(), java.sql.Types.OTHER);
      try (ResultSet rs = stmt.executeQuery()) {
        List<Embedder> result = new ArrayList<>();
        while (rs.next()) {
          StatusOr<Embedder> embedderOr = extractEmbedder(rs);
          if (embedderOr.isNotOk()) {
            return StatusOr.ofStatus(embedderOr.getStatus());
          }
          result.add(embedderOr.getValue());
        }
        return StatusOr.ofValue(ImmutableList.copyOf(result));
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads an embedder by unique connection details.
   *
   * @param conn an open JDBC connection
   * @param endpointUrl the endpoint URL
   * @param apiPath the API path
   * @param modelIdentifier the model identifier
   * @return StatusOr containing an Optional Embedder or an error
   */
  @Nonnull
  public static StatusOr<Optional<Embedder>> loadByConnectionDetails(
      Connection conn, String endpointUrl, String apiPath, String modelIdentifier) {
    String sql =
        """
        SELECT embedder_id, display_name, description, provider_type, endpoint_url, api_path,
               model_identifier, dimensionality, max_sequence_length, supported_modalities,
               credentials, labels, version, monitoring_endpoint,
               owner_id, created_at, updated_at, created_by_id, updated_by_id
          FROM embedder
         WHERE endpoint_url = ? AND api_path = ? AND model_identifier = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, endpointUrl);
      stmt.setString(2, apiPath);
      stmt.setString(3, modelIdentifier);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          StatusOr<Embedder> embedderOr = extractEmbedder(rs);
          if (embedderOr.isNotOk()) {
            return StatusOr.ofStatus(embedderOr.getStatus());
          }
          return StatusOr.ofValue(Optional.of(embedderOr.getValue()));
        }
        return StatusOr.ofValue(Optional.empty());
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Inserts or updates an embedder row (upsert).
   *
   * @param conn an open JDBC connection
   * @param embedder the Embedder object to save
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> save(Connection conn, Embedder embedder) {
    String sql =
        """
        INSERT INTO embedder
               (embedder_id, display_name, description, provider_type, endpoint_url, api_path,
                model_identifier, dimensionality, max_sequence_length, supported_modalities,
                credentials, labels, version, monitoring_endpoint,
                owner_id, created_at, updated_at, created_by_id, updated_by_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(embedder_id)
        DO UPDATE SET display_name          = excluded.display_name,
                      description           = excluded.description,
                      provider_type         = excluded.provider_type,
                      endpoint_url          = excluded.endpoint_url,
                      api_path              = excluded.api_path,
                      model_identifier      = excluded.model_identifier,
                      dimensionality        = excluded.dimensionality,
                      max_sequence_length   = excluded.max_sequence_length,
                      supported_modalities  = excluded.supported_modalities,
                      credentials           = excluded.credentials,
                      labels                = excluded.labels,
                      version               = excluded.version,
                      monitoring_endpoint   = excluded.monitoring_endpoint,
                      owner_id              = excluded.owner_id,
                      updated_at            = excluded.updated_at,
                      updated_by_id         = excluded.updated_by_id
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, embedder.embedderId());
      stmt.setString(2, embedder.displayName());
      stmt.setString(3, embedder.description());
      // Cast the string to the provider_type_enum PostgreSQL type
      stmt.setObject(4, embedder.providerType().toDatabaseValue(), java.sql.Types.OTHER);
      stmt.setString(5, embedder.endpointUrl());
      stmt.setString(6, embedder.apiPath());
      stmt.setString(7, embedder.modelIdentifier());
      stmt.setInt(8, embedder.dimensionality());
      
      // Handle nullable max_sequence_length
      if (embedder.maxSequenceLength() != null) {
        stmt.setInt(9, embedder.maxSequenceLength());
      } else {
        stmt.setNull(9, java.sql.Types.INTEGER);
      }
      
      // Convert supported modalities to a PostgreSQL array of modality_enum type
      if (embedder.supportedModalities() != null && !embedder.supportedModalities().isEmpty()) {
        String[] modalitiesArray = embedder.supportedModalities().stream()
            .map(EmbedderModality::toDatabaseValue)
            .toArray(String[]::new);
        Array sqlArray = conn.createArrayOf("modality_enum", modalitiesArray);
        stmt.setArray(10, sqlArray);
      } else {
        // Set default TEXT modality if none provided
        Array sqlArray = conn.createArrayOf("modality_enum", new String[] {"TEXT"});
        stmt.setArray(10, sqlArray);
      }
      
      stmt.setString(11, embedder.credentials());
      
      // Process and set the labels as JSONB
      Status labelsStatus = DbUtil.setJsonbParameter(stmt, 12, embedder.labels());
      if (!labelsStatus.isOk()) {
        return StatusOr.ofStatus(labelsStatus);
      }
      
      stmt.setString(13, embedder.version());
      stmt.setString(14, embedder.monitoringEndpoint());
      stmt.setObject(15, embedder.ownerId());
      stmt.setTimestamp(16, DbUtil.toSqlTimestamp(embedder.createdAt()));
      stmt.setTimestamp(17, DbUtil.toSqlTimestamp(embedder.updatedAt()));
      stmt.setObject(18, embedder.createdById());
      stmt.setObject(19, embedder.updatedById());

      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Deletes an embedder by ID.
   *
   * @param conn an open JDBC connection
   * @param embedderId the UUID of the embedder to delete
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> delete(Connection conn, UUID embedderId) {
    String sql =
        """
        DELETE FROM embedder
         WHERE embedder_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, embedderId);
      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /** Extracts an Embedder from the current row of a ResultSet. */
  @Nonnull
  private static StatusOr<Embedder> extractEmbedder(ResultSet rs) throws SQLException {
    StatusOr<UUID> embedderIdOr = DbUtil.getUuid(rs, "embedder_id");
    if (embedderIdOr.isNotOk()) {
      return StatusOr.ofStatus(embedderIdOr.getStatus());
    }

    String displayName = rs.getString("display_name");
    String description = rs.getString("description");
    
    // Parse provider type enum
    String providerTypeStr = rs.getString("provider_type");
    EmbedderProviderType providerType;
    try {
      providerType = EmbedderProviderType.fromDatabaseValue(providerTypeStr);
    } catch (IllegalArgumentException e) {
      return StatusOr.ofStatus(
          Status.invalidArgument("Invalid provider type: " + providerTypeStr));
    }
    
    String endpointUrl = rs.getString("endpoint_url");
    String apiPath = rs.getString("api_path");
    String modelIdentifier = rs.getString("model_identifier");
    int dimensionality = rs.getInt("dimensionality");
    
    // Handle nullable max_sequence_length
    Integer maxSequenceLength = rs.getInt("max_sequence_length");
    if (rs.wasNull()) {
      maxSequenceLength = null;
    }
    
    // Parse the modalities array from database
    Array modalitiesArray = rs.getArray("supported_modalities");
    List<EmbedderModality> supportedModalities = new ArrayList<>();
    if (modalitiesArray != null) {
      String[] modalityStrings = (String[]) modalitiesArray.getArray();
      for (String modalityStr : modalityStrings) {
        try {
          supportedModalities.add(EmbedderModality.fromDatabaseValue(modalityStr));
        } catch (IllegalArgumentException e) {
          return StatusOr.ofStatus(
              Status.invalidArgument("Invalid modality: " + modalityStr));
        }
      }
    }
    
    String credentials = rs.getString("credentials");
    
    // Parse the JSONB labels
    StatusOr<Map<String, String>> labelsOr = DbUtil.parseJsonbToMap(rs, "labels");
    if (labelsOr.isNotOk()) {
      return StatusOr.ofStatus(labelsOr.getStatus());
    }
    Map<String, String> labels = labelsOr.getValue();
    
    String version = rs.getString("version");
    
    String monitoringEndpoint = rs.getString("monitoring_endpoint");

    StatusOr<UUID> ownerIdOr = DbUtil.getUuid(rs, "owner_id");
    if (ownerIdOr.isNotOk()) {
      return StatusOr.ofStatus(ownerIdOr.getStatus());
    }

    StatusOr<Instant> createdAtOr = DbUtil.getInstant(rs, "created_at");
    if (createdAtOr.isNotOk()) {
      return StatusOr.ofStatus(createdAtOr.getStatus());
    }

    StatusOr<Instant> updatedAtOr = DbUtil.getInstant(rs, "updated_at");
    if (updatedAtOr.isNotOk()) {
      return StatusOr.ofStatus(updatedAtOr.getStatus());
    }

    StatusOr<UUID> createdByIdOr = DbUtil.getUuid(rs, "created_by_id");
    if (createdByIdOr.isNotOk()) {
      return StatusOr.ofStatus(createdByIdOr.getStatus());
    }

    StatusOr<UUID> updatedByIdOr = DbUtil.getUuid(rs, "updated_by_id");
    if (updatedByIdOr.isNotOk()) {
      return StatusOr.ofStatus(updatedByIdOr.getStatus());
    }

    return StatusOr.ofValue(
        new Embedder(
            embedderIdOr.getValue(),
            displayName,
            description,
            providerType,
            endpointUrl,
            apiPath,
            modelIdentifier,
            dimensionality,
            maxSequenceLength,
            ImmutableList.copyOf(supportedModalities),
            credentials,
            labels,
            version,
            monitoringEndpoint,
            ownerIdOr.getValue(),
            createdAtOr.getValue(),
            updatedAtOr.getValue(),
            createdByIdOr.getValue(),
            updatedByIdOr.getValue()));
  }
}