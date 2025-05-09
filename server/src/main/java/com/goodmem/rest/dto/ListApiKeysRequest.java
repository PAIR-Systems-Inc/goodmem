package com.goodmem.rest.dto;

import goodmem.v1.Apikey;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiName;

/**
 * Data Transfer Object for listing API Keys request parameters.
 * 
 * <p>This record represents the query parameters for the GET /v1/apikeys REST endpoint.
 * Currently, this is a placeholder as the endpoint does not take any query parameters.
 * In the future, it could be expanded to support pagination, filtering, etc.
 */
@OpenApiDescription("Request parameters for listing API keys.")
@OpenApiName("ListApiKeysRequest")
@ProtobufEquivalent(Apikey.ListApiKeysRequest.class)
public record ListApiKeysRequest() {
    /**
     * Empty constructor that creates an empty request.
     * Required for proper JSON deserialization.
     */
    public ListApiKeysRequest {
        // No fields to initialize yet
    }
}