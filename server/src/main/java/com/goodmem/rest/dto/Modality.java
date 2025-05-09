package com.goodmem.rest.dto;

import goodmem.v1.EmbedderOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiName;

/**
 * Enum representing the types of content modalities supported by embedders.
 * 
 * <p>This enum corresponds to the goodmem.v1.EmbedderOuterClass.Modality protobuf enum,
 * but is independent from the protocol buffer implementation to maintain separation
 * between REST and gRPC layers.
 */
@OpenApiDescription("Content modality types supported by embedders")
@OpenApiName("Modality")
@ProtobufEquivalent(EmbedderOuterClass.Modality.class)
public enum Modality {
  TEXT,   // Text content
  IMAGE,  // Image content
  AUDIO,  // Audio content
  VIDEO;  // Video content
  
  /**
   * Converts this DTO Modality to its corresponding protobuf Modality.
   *
   * @return The protocol buffer Modality enum value
   */
  public goodmem.v1.EmbedderOuterClass.Modality toProtoModality() {
    return switch (this) {
      case TEXT -> goodmem.v1.EmbedderOuterClass.Modality.MODALITY_TEXT;
      case IMAGE -> goodmem.v1.EmbedderOuterClass.Modality.MODALITY_IMAGE; 
      case AUDIO -> goodmem.v1.EmbedderOuterClass.Modality.MODALITY_AUDIO;
      case VIDEO -> goodmem.v1.EmbedderOuterClass.Modality.MODALITY_VIDEO;
    };
  }
  
  /**
   * Converts a string representation to a Modality enum value.
   *
   * @param modalityStr The string representation of the modality
   * @return The corresponding Modality enum value or null if not found
   */
  public static Modality fromString(String modalityStr) {
    if (modalityStr == null) {
      return null;
    }
    
    return switch (modalityStr.toUpperCase()) {
      case "TEXT" -> TEXT;
      case "IMAGE" -> IMAGE;
      case "AUDIO" -> AUDIO;
      case "VIDEO" -> VIDEO;
      default -> null;
    };
  }
  
  /**
   * Converts a protobuf Modality to its corresponding DTO Modality.
   *
   * @param protoModality The protocol buffer Modality enum value
   * @return The corresponding DTO Modality enum value or null if not recognized
   */
  public static Modality fromProtoModality(goodmem.v1.EmbedderOuterClass.Modality protoModality) {
    if (protoModality == null) {
      return null;
    }
    
    return switch (protoModality) {
      case MODALITY_TEXT -> TEXT;
      case MODALITY_IMAGE -> IMAGE;
      case MODALITY_AUDIO -> AUDIO;
      case MODALITY_VIDEO -> VIDEO;
      default -> null;
    };
  }
}