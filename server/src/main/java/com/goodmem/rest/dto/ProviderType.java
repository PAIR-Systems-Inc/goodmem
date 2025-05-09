package com.goodmem.rest.dto;

import goodmem.v1.EmbedderOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiName;

/**
 * Enum representing the types of embedding providers supported by the system.
 * 
 * <p>This enum corresponds to the goodmem.v1.EmbedderOuterClass.ProviderType protobuf enum,
 * but is independent from the protocol buffer implementation to maintain separation
 * between REST and gRPC layers.
 */
@OpenApiDescription("Embedding provider types")
@OpenApiName("ProviderType")
@ProtobufEquivalent(EmbedderOuterClass.ProviderType.class)
public enum ProviderType {
  OPENAI,  // OpenAI embedding models
  VLLM,    // vLLM-compatible embedding models
  TEI;     // Tensor Engine Interface models
  
  /**
   * Converts this DTO ProviderType to its corresponding protobuf ProviderType.
   *
   * @return The protocol buffer ProviderType enum value
   */
  public goodmem.v1.EmbedderOuterClass.ProviderType toProtoProviderType() {
    return switch (this) {
      case OPENAI -> goodmem.v1.EmbedderOuterClass.ProviderType.PROVIDER_TYPE_OPENAI;
      case VLLM -> goodmem.v1.EmbedderOuterClass.ProviderType.PROVIDER_TYPE_VLLM;
      case TEI -> goodmem.v1.EmbedderOuterClass.ProviderType.PROVIDER_TYPE_TEI;
    };
  }
  
  /**
   * Converts a string representation to a ProviderType enum value.
   *
   * @param providerTypeStr The string representation of the provider type
   * @return The corresponding ProviderType enum value or null if not found
   */
  public static ProviderType fromString(String providerTypeStr) {
    if (providerTypeStr == null) {
      return null;
    }
    
    return switch (providerTypeStr.toUpperCase()) {
      case "OPENAI" -> OPENAI;
      case "VLLM" -> VLLM;
      case "TEI" -> TEI;
      default -> null;
    };
  }
  
  /**
   * Converts a protobuf ProviderType to its corresponding DTO ProviderType.
   *
   * @param protoProviderType The protocol buffer ProviderType enum value
   * @return The corresponding DTO ProviderType enum value or null if not recognized
   */
  public static ProviderType fromProtoProviderType(goodmem.v1.EmbedderOuterClass.ProviderType protoProviderType) {
    if (protoProviderType == null) {
      return null;
    }
    
    return switch (protoProviderType) {
      case PROVIDER_TYPE_OPENAI -> OPENAI;
      case PROVIDER_TYPE_VLLM -> VLLM;
      case PROVIDER_TYPE_TEI -> TEI;
      default -> null;
    };
  }
}