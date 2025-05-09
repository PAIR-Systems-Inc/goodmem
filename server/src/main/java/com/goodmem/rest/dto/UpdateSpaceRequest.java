package com.goodmem.rest.dto;

import goodmem.v1.SpaceOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;

import java.util.Map;

/**
 * Data Transfer Object for updating a Space.
 * 
 * <p>This record represents the JSON request body for updating a space via the
 * PUT /v1/spaces/{id} REST endpoint. It includes all mutable fields that can be
 * updated for an existing space.
 */
@OpenApiDescription("Request parameters for updating a space.")
@OpenApiName("UpdateSpaceRequest")
@ProtobufEquivalent(SpaceOuterClass.UpdateSpaceRequest.class)
public record UpdateSpaceRequest(
    @OpenApiDescription("The unique identifier of the space to update.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String spaceId,
    
    @OpenApiDescription("The new name for the space.")
    @OpenApiExample("Updated Research Space")
    @OpenApiNullable
    @OpenApiStringValidation(minLength = "1", maxLength = "255")
    String name,
    
    @OpenApiDescription("Whether the space is publicly readable by all users.")
    @OpenApiExample("true")
    @OpenApiNullable
    Boolean publicRead,
    
    @OpenApiDescription("Labels to replace all existing labels. Mutually exclusive with mergeLabels.")
    @OpenApiExample("{\"project\": \"Updated AI Research\", \"team\": \"NLP Group\"}")
    @OpenApiNullable
    Map<String, String> replaceLabels,
    
    @OpenApiDescription("Labels to merge with existing labels. Mutually exclusive with replaceLabels.")
    @OpenApiExample("{\"status\": \"active\", \"priority\": \"high\"}")
    @OpenApiNullable
    Map<String, String> mergeLabels
) {
    /**
     * Empty constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public UpdateSpaceRequest() {
        this(null, null, null, null, null);
    }
    
    /**
     * Validates that only one of replaceLabels or mergeLabels is set.
     * 
     * @throws IllegalArgumentException if both replaceLabels and mergeLabels are non-null
     */
    public void validateLabelStrategy() {
        if (replaceLabels != null && mergeLabels != null) {
            throw new IllegalArgumentException(
                "Only one of replaceLabels or mergeLabels can be specified, not both.");
        }
    }
}