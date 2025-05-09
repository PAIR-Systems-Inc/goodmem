package com.goodmem.rest.dto;

import goodmem.v1.Apikey;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;

import java.util.Map;

/**
 * Data Transfer Object for updating an API Key.
 * 
 * <p>This record represents the JSON request body for the PUT /v1/apikeys/{id} REST endpoint.
 * It supports updating the API key status and labels.
 */
@OpenApiDescription("Request parameters for updating an API key.")
@OpenApiName("UpdateApiKeyRequest")
@ProtobufEquivalent(Apikey.UpdateApiKeyRequest.class)
public record UpdateApiKeyRequest(
    @OpenApiDescription("New status for the API key. Allowed values: ACTIVE, INACTIVE.")
    @OpenApiExample("ACTIVE")
    @OpenApiNullable
    String status,
    
    @OpenApiDescription("Replace all existing labels with this set. Mutually exclusive with mergeLabels.")
    @OpenApiExample("{\"environment\": \"production\", \"service\": \"recommendation-engine\"}")
    @OpenApiNullable
    Map<String, String> replaceLabels,
    
    @OpenApiDescription("Merge these labels with existing ones. Mutually exclusive with replaceLabels.")
    @OpenApiExample("{\"team\": \"ml-research\"}")
    @OpenApiNullable
    Map<String, String> mergeLabels
) {
    /**
     * Empty constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public UpdateApiKeyRequest() {
        this(null, null, null);
    }
    
    /**
     * Validates that only one of replaceLabels or mergeLabels is provided.
     * 
     * @throws IllegalArgumentException if both replaceLabels and mergeLabels are provided
     */
    public void validateLabelStrategy() {
        if (replaceLabels != null && mergeLabels != null) {
            throw new IllegalArgumentException(
                "Only one of 'replaceLabels' or 'mergeLabels' can be provided, not both");
        }
    }
}