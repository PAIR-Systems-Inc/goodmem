package com.goodmem.rest.dto;

import goodmem.v1.SpaceOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;

/**
 * Data Transfer Object for deleting a Space.
 * 
 * <p>This record represents the parameters for deleting a space via the
 * DELETE /v1/spaces/{id} REST endpoint.
 */
@OpenApiDescription("Request parameters for deleting a space.")
@OpenApiName("DeleteSpaceRequest")
@ProtobufEquivalent(SpaceOuterClass.DeleteSpaceRequest.class)
public record DeleteSpaceRequest(
    @OpenApiDescription("The unique identifier of the space to delete.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String spaceId
) {
    /**
     * Empty constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public DeleteSpaceRequest() {
        this(null);
    }
}