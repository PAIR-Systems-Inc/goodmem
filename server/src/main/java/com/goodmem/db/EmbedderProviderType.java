package com.goodmem.db;

/**
 * Enumeration of supported embedder provider types.
 * 
 * IMPORTANT: Keep this enum in sync with the database's provider_type_enum type in 01-schema.sql
 */
public enum EmbedderProviderType {
  /**
   * OpenAI embedding models (e.g., text-embedding-3-small, text-embedding-3-large).
   */
  OPENAI,
  
  /**
   * vLLM-compatible embedding models.
   */
  VLLM,
  
  /**
   * Tensor Engine Interface models - covers both native and OpenAI-compatible TEI endpoints.
   */
  TEI;
  
  /**
   * Converts the enum to its string representation for database storage.
   * 
   * @return The string value for database storage
   */
  public String toDatabaseValue() {
    return name();
  }
  
  /**
   * Creates an enum value from its database string representation.
   * 
   * @param value The database string value
   * @return The corresponding enum value
   * @throws IllegalArgumentException If the value doesn't match any enum constant
   */
  public static EmbedderProviderType fromDatabaseValue(String value) {
    return valueOf(value);
  }
}