package com.goodmem.rest.dto;

import goodmem.v1.Apikey;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;

/**
 * Data Transfer Object for the response when creating a new API Key.
 * 
 * <p>This record represents the JSON response for the POST /v1/apikeys endpoint.
 * It contains both the API key metadata and the raw API key value. The raw API key
 * is only returned once during creation and cannot be retrieved again.
 */
@OpenApiDescription("Response returned when creating a new API key.")
@OpenApiName("CreateApiKeyResponse")
@ProtobufEquivalent(Apikey.CreateApiKeyResponse.class)
public record CreateApiKeyResponse(
    @OpenApiDescription("Metadata for the created API key.")
    ApiKeyResponse apiKeyMetadata,
    
    @OpenApiDescription("The actual API key value. This is only returned once and cannot be retrieved again.")
    @OpenApiExample("gm_12345678901234567890123456789012345678901234567890")
    String rawApiKey
) {
    /**
     * Empty constructor that creates an empty response with null values.
     * Required for proper JSON serialization/deserialization.
     */
    public CreateApiKeyResponse() {
        this(null, null);
    }
}