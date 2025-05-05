package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;
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
 * @param deploymentContext Optional deployment context information
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
    Map<String, Object> deploymentContext,
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
  public goodmem.v1.Embedder toProto() {
    goodmem.v1.Embedder.Builder builder =
        goodmem.v1.Embedder.newBuilder()
            .setEmbedderId(UuidUtil.toProtoBytes(embedderId))
            .setDisplayName(displayName)
            .setProviderType(convertProviderTypeToProto(providerType))
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
      supportedModalities.forEach(modality -> 
          builder.addSupportedModalities(convertModalityToProto(modality)));
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
  
  /**
   * Converts Java enum to Proto enum for Provider Type.
   *
   * @param providerType The Java ProviderType enum
   * @return The Proto ProviderType enum
   */
  private goodmem.v1.ProviderType convertProviderTypeToProto(EmbedderProviderType providerType) {
    return switch (providerType) {
      case OPENAI -> goodmem.v1.ProviderType.PROVIDER_TYPE_OPENAI;
      case VLLM -> goodmem.v1.ProviderType.PROVIDER_TYPE_VLLM;
      case TEI -> goodmem.v1.ProviderType.PROVIDER_TYPE_TEI;
      default -> goodmem.v1.ProviderType.PROVIDER_TYPE_UNSPECIFIED;
    };
  }
  
  /**
   * Converts Java enum to Proto enum for Modality.
   *
   * @param modality The Java Modality enum
   * @return The Proto Modality enum
   */
  private goodmem.v1.Modality convertModalityToProto(EmbedderModality modality) {
    return switch (modality) {
      case TEXT -> goodmem.v1.Modality.MODALITY_TEXT;
      case IMAGE -> goodmem.v1.Modality.MODALITY_IMAGE;
      case AUDIO -> goodmem.v1.Modality.MODALITY_AUDIO;
      case VIDEO -> goodmem.v1.Modality.MODALITY_VIDEO;
      default -> goodmem.v1.Modality.MODALITY_UNSPECIFIED;
    };
  }
}