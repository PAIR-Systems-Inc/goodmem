package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;
import com.goodmem.util.EnumConverters;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a row in the 'embedder' table.
 *
 * @param embedderId The unique identifier of the embedder
 * @param displayName The display name for the embedder
 * @param description Optional description of the embedder
 * @param providerType The provider type enum (OPENAI, VLLM, TEI)
 * @param endpointUrl The API endpoint URL
 * @param apiPath The API path for embeddings request
 * @param modelIdentifier The model identifier
 * @param dimensionality Output vector dimensions
 * @param maxSequenceLength Maximum input sequence length (optional)
 * @param supportedModalities List of supported modalities
 * @param credentials API credentials (to be encrypted)
 * @param labels User-defined labels for the embedder
 * @param version Optional version information
 * @param monitoringEndpoint Optional monitoring endpoint
 * @param ownerId The user ID of the owner
 * @param createdAt Timestamp when the record was created
 * @param updatedAt Timestamp when the record was last updated
 * @param createdById User ID who created this embedder
 * @param updatedById User ID who last updated this embedder
 */
public record Embedder(
    UUID embedderId,
    String displayName,
    String description,
    EmbedderProviderType providerType,
    String endpointUrl,
    String apiPath,
    String modelIdentifier,
    int dimensionality,
    Integer maxSequenceLength,
    List<EmbedderModality> supportedModalities,
    String credentials,
    Map<String, String> labels,
    String version,
    String monitoringEndpoint,
    UUID ownerId,
    Instant createdAt,
    Instant updatedAt,
    UUID createdById,
    UUID updatedById) {

  /**
   * Converts this database record to its corresponding Protocol Buffer message.
   *
   * @return The Protocol Buffer Embedder message
   */
  public goodmem.v1.EmbedderOuterClass.Embedder toProto() {
    goodmem.v1.EmbedderOuterClass.Embedder.Builder builder =
        goodmem.v1.EmbedderOuterClass.Embedder.newBuilder()
            .setEmbedderId(UuidUtil.toProtoBytes(embedderId))
            .setDisplayName(displayName)
            .setProviderType(EnumConverters.toProtoProviderType(providerType))
            .setEndpointUrl(endpointUrl)
            .setApiPath(apiPath)
            .setModelIdentifier(modelIdentifier)
            .setDimensionality(dimensionality)
            .setOwnerId(UuidUtil.toProtoBytes(ownerId))
            .setCreatedAt(DbUtil.toProtoTimestamp(createdAt))
            .setUpdatedAt(DbUtil.toProtoTimestamp(updatedAt))
            .setCreatedById(UuidUtil.toProtoBytes(createdById))
            .setUpdatedById(UuidUtil.toProtoBytes(updatedById));

    // Add optional fields if present
    if (description != null) {
      builder.setDescription(description);
    }

    if (maxSequenceLength != null) {
      builder.setMaxSequenceLength(maxSequenceLength);
    }

    if (supportedModalities != null && !supportedModalities.isEmpty()) {
      supportedModalities.forEach(
          modality -> builder.addSupportedModalities(EnumConverters.toProtoModality(modality)));
    }

    if (credentials != null) {
      builder.setCredentials(credentials);
    }

    // Add labels if present
    if (labels != null) {
      builder.putAllLabels(labels);
    }

    if (version != null) {
      builder.setVersion(version);
    }

    if (monitoringEndpoint != null) {
      builder.setMonitoringEndpoint(monitoringEndpoint);
    }

    return builder.build();
  }
}
