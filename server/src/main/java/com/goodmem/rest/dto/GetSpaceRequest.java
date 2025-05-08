package com.goodmem.rest.dto;

import goodmem.v1.SpaceOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;

/**
 * Data Transfer Object for requesting a Space by ID.
 * 
 * <p>This record represents the request parameters needed to retrieve a specific space.
 * In the REST API, the spaceId is provided as a path parameter in the URL.
 */
@OpenApiDescription("Request parameters for retrieving a specific Space by ID.")
@OpenApiName("GetSpaceRequest")
@ProtobufEquivalent(SpaceOuterClass.GetSpaceRequest.class)
public record GetSpaceRequest(
    @OpenApiDescription("The unique identifier of the space to retrieve.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String spaceId
) {
    /**
     * Empty constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public GetSpaceRequest() {
        this(null);
    }
}