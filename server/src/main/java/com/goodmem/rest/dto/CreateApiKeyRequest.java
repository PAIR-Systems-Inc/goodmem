package com.goodmem.rest.dto;

import goodmem.v1.Apikey;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiObjectValidation;

import java.util.Map;

/**
 * Data Transfer Object for creating a new API Key.
 * 
 * <p>This record represents the JSON request body for the POST /v1/apikeys REST endpoint.
 * An API key is a credential used to authenticate requests to the GoodMem API.
 */
@OpenApiDescription("Request parameters for creating a new API key.")
@OpenApiName("CreateApiKeyRequest")
@ProtobufEquivalent(Apikey.CreateApiKeyRequest.class)
public record CreateApiKeyRequest(
    @OpenApiDescription("Key-value pairs of metadata associated with the API key. Used for organization and filtering.")
    @OpenApiExample("{\"purpose\": \"production\", \"service\": \"recommendation-engine\"}")
    @OpenApiObjectValidation(maxProperties = "10")
    @OpenApiNullable
    Map<String, String> labels,
    
    @OpenApiDescription("Expiration timestamp in milliseconds since epoch. If not provided, the key does not expire.")
    @OpenApiExample("1672531200000") // Example: 2023-01-01T00:00:00Z
    @OpenApiNullable
    Long expiresAt
) {
    /**
     * Empty constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public CreateApiKeyRequest() {
        this(null, null);
    }
}