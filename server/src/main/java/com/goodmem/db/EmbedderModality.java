package com.goodmem.db;

/**
 * Enumeration of supported modalities for embedders.
 * 
 * IMPORTANT: Keep this enum in sync with the database's modality_enum type in 01-schema.sql
 */
public enum EmbedderModality {
  TEXT,
  IMAGE,
  AUDIO,
  VIDEO;
  
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
  public static EmbedderModality fromDatabaseValue(String value) {
    return valueOf(value);
  }
}