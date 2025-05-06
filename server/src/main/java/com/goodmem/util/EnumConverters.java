package com.goodmem.util;

import com.goodmem.db.EmbedderModality;
import com.goodmem.db.EmbedderProviderType;
import goodmem.v1.EmbedderOuterClass.Modality;
import goodmem.v1.EmbedderOuterClass.ProviderType;

/**
 * Utility class for converting between proto enums and database enums.
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
}