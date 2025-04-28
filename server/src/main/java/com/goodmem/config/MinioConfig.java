package com.goodmem.config;

import com.google.common.base.MoreObjects;

/**
 * Configuration record for MinIO object storage client.
 *
 * <p>This record encapsulates all necessary parameters to establish a connection
 * to a MinIO server instance and to interact with a specific bucket. MinIO provides
 * S3-compatible object storage used by the application for storing binary data.
 *
 * @param minioEndpoint The URL endpoint for the MinIO server (e.g., "http://localhost:9000")
 * @param minioAccessKey The access key (username) for authenticating with the MinIO server
 * @param minioSecretKey The secret key (password) for authenticating with the MinIO server
 * @param minioBucket The name of the bucket in which objects will be stored
 */
public record MinioConfig(
    String minioEndpoint,
    String minioAccessKey,
    String minioSecretKey,
    String minioBucket
) {

  /**
   * Returns a string representation of this object without the secret key.
   *
   * <p>This method creates a secure string representation that includes all configuration
   * details except for the secret key, making it safe to use in logs and other
   * potentially publicly visible contexts.
   *
   * @return A string containing the MinIO configuration with the secret key omitted
   */
  public String toSecureString() {
    return MoreObjects.toStringHelper(this)
        .add("minioEndpoint", minioEndpoint())
        .add("minioAccessKey", minioAccessKey())
        .add("minioBucket", minioBucket())
        .toString();
  }
}