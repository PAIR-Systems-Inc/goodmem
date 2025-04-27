package com.goodmem.config;

/**
 * TODO: document this
 * @param minioEndpoint
 * @param minioAccessKey
 * @param minioSecretKey
 * @param minioBucket
 */
public record MinioConfig(
    String minioEndpoint,
    String minioAccessKey,
    String minioSecretKey,
    String minioBucket
) {}