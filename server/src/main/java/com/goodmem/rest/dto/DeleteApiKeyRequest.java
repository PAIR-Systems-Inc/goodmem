package com.goodmem.rest.dto;

import goodmem.v1.Apikey;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;

/**
 * Data Transfer Object for deleting an API Key.
 * 
 * <p>This record represents the path parameter for the DELETE /v1/apikeys/{id} REST endpoint.
 */
@OpenApiDescription("Request parameters for deleting an API key.")
@OpenApiName("DeleteApiKeyRequest")
@ProtobufEquivalent(Apikey.DeleteApiKeyRequest.class)
public record DeleteApiKeyRequest(
    @OpenApiDescription("Unique identifier of the API key to delete.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    String apiKeyId
) {
    /**
     * Empty constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public DeleteApiKeyRequest() {
        this(null);
    }
}