package com.goodmem.rest.dto;

import goodmem.v1.Apikey;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;

import java.util.Map;

/**
 * Data Transfer Object for API Key information, used in responses.
 * 
 * <p>This record represents the JSON response for API key related endpoints.
 * It contains metadata about the API key but does not include the sensitive 
 * key hash information.
 */
@OpenApiDescription("API key metadata without sensitive information.")
@OpenApiName("ApiKeyResponse")
@ProtobufEquivalent(Apikey.ApiKey.class)
public record ApiKeyResponse(
    @OpenApiDescription("Unique identifier for the API key.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    String apiKeyId,
    
    @OpenApiDescription("ID of the user that owns this API key.")
    @OpenApiExample("b3303d0a-1a4a-493f-b9bf-38e37153b5a2")
    String userId,
    
    @OpenApiDescription("First few characters of the key for display/identification purposes.")
    @OpenApiExample("gm_12345...")
    @OpenApiNullable
    String keyPrefix,
    
    @OpenApiDescription("Current status of the API key: ACTIVE, INACTIVE, or STATUS_UNSPECIFIED.")
    @OpenApiExample("ACTIVE")
    String status,
    
    @OpenApiDescription("User-defined labels for organization and filtering.")
    @OpenApiExample("{\"purpose\": \"production\", \"service\": \"recommendation-engine\"}")
    @OpenApiNullable
    Map<String, String> labels,
    
    @OpenApiDescription("Expiration timestamp in milliseconds since epoch. If not provided, the key does not expire.")
    @OpenApiExample("1672531200000") // Example: 2023-01-01T00:00:00Z
    @OpenApiNullable
    Long expiresAt,
    
    @OpenApiDescription("Last time this API key was used, in milliseconds since epoch.")
    @OpenApiExample("1640995200000") // Example: 2022-01-01T00:00:00Z
    @OpenApiNullable
    Long lastUsedAt,
    
    @OpenApiDescription("When the API key was created, in milliseconds since epoch.")
    @OpenApiExample("1640908800000") // Example: 2021-12-31T00:00:00Z
    Long createdAt,
    
    @OpenApiDescription("When the API key was last updated, in milliseconds since epoch.")
    @OpenApiExample("1640908800000") // Example: 2021-12-31T00:00:00Z
    Long updatedAt,
    
    @OpenApiDescription("ID of the user that created this API key.")
    @OpenApiExample("b3303d0a-1a4a-493f-b9bf-38e37153b5a2")
    String createdById,
    
    @OpenApiDescription("ID of the user that last updated this API key.")
    @OpenApiExample("b3303d0a-1a4a-493f-b9bf-38e37153b5a2")
    String updatedById
) {
    /**
     * Empty constructor that creates an empty response with null values.
     * Required for proper JSON serialization/deserialization.
     */
    public ApiKeyResponse() {
        this(null, null, null, null, null, null, null, null, null, null, null);
    }
}