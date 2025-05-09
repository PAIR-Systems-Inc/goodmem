package com.goodmem.rest.dto;

import goodmem.v1.Apikey;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiName;

import java.util.List;

/**
 * Data Transfer Object for response from the list API keys endpoint.
 * 
 * <p>This record represents the JSON response from the GET /v1/apikeys REST endpoint.
 * It contains a list of API key metadata records.
 */
@OpenApiDescription("Response containing a list of API keys.")
@OpenApiName("ListApiKeysResponse")
@ProtobufEquivalent(Apikey.ListApiKeysResponse.class)
public record ListApiKeysResponse(
    @OpenApiDescription("List of API keys belonging to the authenticated user.")
    List<ApiKeyResponse> keys
) {
    /**
     * Empty constructor that creates an empty response with no keys.
     * Required for proper JSON serialization/deserialization.
     */
    public ListApiKeysResponse() {
        this(List.of());
    }
}