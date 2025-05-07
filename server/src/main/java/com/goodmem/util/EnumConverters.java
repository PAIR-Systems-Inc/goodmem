package com.goodmem.util;

import com.goodmem.db.EmbedderModality;
import com.goodmem.db.EmbedderProviderType;
import goodmem.v1.EmbedderOuterClass.Modality;
import goodmem.v1.EmbedderOuterClass.ProviderType;

/**
 * Utility class for converting between proto enums and database enums.
 * 
 * <p>This class centralizes all enum conversion logic for consistent handling across the application.
 * It provides methods for converting between:
 * <ul>
 *   <li>Proto enums and database enums</li>
 *   <li>String representations and proto enums</li>
 *   <li>Database enums and proto enums</li>
 * </ul>
 */
public final class EnumConverters {

  private EnumConverters() {
    // Utility class, no instances
  }
  
  /**
   * Converts proto ProviderType enum to database EmbedderProviderType enum.
   *
   * @param providerType The proto ProviderType enum
   * @return The database EmbedderProviderType enum
   */
  public static EmbedderProviderType fromProtoProviderType(ProviderType providerType) {
    return switch (providerType) {
      case PROVIDER_TYPE_OPENAI -> EmbedderProviderType.OPENAI;
      case PROVIDER_TYPE_VLLM -> EmbedderProviderType.VLLM;
      case PROVIDER_TYPE_TEI -> EmbedderProviderType.TEI;
      default -> EmbedderProviderType.OPENAI; // Default to OPENAI for UNSPECIFIED
    };
  }

  /**
   * Converts proto Modality enum to database EmbedderModality enum.
   *
   * @param modality The proto Modality enum
   * @return The database EmbedderModality enum
   */
  public static EmbedderModality fromProtoModality(Modality modality) {
    return switch (modality) {
      case MODALITY_TEXT -> EmbedderModality.TEXT;
      case MODALITY_IMAGE -> EmbedderModality.IMAGE;
      case MODALITY_AUDIO -> EmbedderModality.AUDIO;
      case MODALITY_VIDEO -> EmbedderModality.VIDEO;
      default -> EmbedderModality.TEXT; // Default to TEXT for UNSPECIFIED
    };
  }
  
  /**
   * Converts database EmbedderProviderType enum to proto ProviderType enum.
   *
   * @param providerType The database EmbedderProviderType enum
   * @return The proto ProviderType enum
   */
  public static ProviderType toProtoProviderType(EmbedderProviderType providerType) {
    return switch (providerType) {
      case OPENAI -> ProviderType.PROVIDER_TYPE_OPENAI;
      case VLLM -> ProviderType.PROVIDER_TYPE_VLLM;
      case TEI -> ProviderType.PROVIDER_TYPE_TEI;
      default -> ProviderType.PROVIDER_TYPE_UNSPECIFIED;
    };
  }

  /**
   * Converts database EmbedderModality enum to proto Modality enum.
   *
   * @param modality The database EmbedderModality enum
   * @return The proto Modality enum
   */
  public static Modality toProtoModality(EmbedderModality modality) {
    return switch (modality) {
      case TEXT -> Modality.MODALITY_TEXT;
      case IMAGE -> Modality.MODALITY_IMAGE;
      case AUDIO -> Modality.MODALITY_AUDIO;
      case VIDEO -> Modality.MODALITY_VIDEO;
      default -> Modality.MODALITY_UNSPECIFIED;
    };
  }
  
  /**
   * Converts a string representation of a provider type to the proto ProviderType enum.
   * Case-insensitive matching is performed.
   *
   * @param providerTypeStr The string representation of the provider type
   * @return The proto ProviderType enum
   */
  public static ProviderType providerTypeFromString(String providerTypeStr) {
    if (providerTypeStr == null || providerTypeStr.isEmpty()) {
      return ProviderType.PROVIDER_TYPE_UNSPECIFIED;
    }
    
    return switch (providerTypeStr.toUpperCase()) {
      case "OPENAI" -> ProviderType.PROVIDER_TYPE_OPENAI;
      case "VLLM" -> ProviderType.PROVIDER_TYPE_VLLM;
      case "TEI" -> ProviderType.PROVIDER_TYPE_TEI;
      default -> ProviderType.PROVIDER_TYPE_UNSPECIFIED;
    };
  }

  /**
   * Converts a string representation of a modality to the proto Modality enum.
   * Case-insensitive matching is performed.
   *
   * @param modalityStr The string representation of the modality
   * @return The proto Modality enum
   */
  public static Modality modalityFromString(String modalityStr) {
    if (modalityStr == null || modalityStr.isEmpty()) {
      return Modality.MODALITY_UNSPECIFIED;
    }
    
    return switch (modalityStr.toUpperCase()) {
      case "TEXT" -> Modality.MODALITY_TEXT;
      case "IMAGE" -> Modality.MODALITY_IMAGE;
      case "AUDIO" -> Modality.MODALITY_AUDIO;
      case "VIDEO" -> Modality.MODALITY_VIDEO;
      default -> Modality.MODALITY_UNSPECIFIED;
    };
  }
}