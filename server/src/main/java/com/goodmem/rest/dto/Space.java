package com.goodmem.rest.dto;

import java.util.Map;

import goodmem.v1.SpaceOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiByFields;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;
import io.javalin.openapi.Visibility;

/**
 * Data Transfer Object for the response when creating a new Space.
 * 
 * <p>This record represents the JSON response body from the /v1/spaces REST endpoint.
 * It includes all the fields from the Space proto message that are exposed to REST clients.
 * The response contains the complete space information, including server-generated fields
 * like IDs, timestamps, and ownership information.
 */
@OpenApiDescription("A Space is a container for organizing related memories with vector embeddings.")
@OpenApiName("Space")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(SpaceOuterClass.Space.class)
public record Space(
    @OpenApiDescription("The unique identifier for this space.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String spaceId,
    
    @OpenApiDescription("The name of the space.")
    @OpenApiExample("Research Project Space")
    @OpenApiRequired
    @OpenApiStringValidation(minLength = "1", maxLength = "255")
    String name,
    
    @OpenApiDescription("Key-value pairs of metadata associated with the space.")
    @OpenApiExample("{\"project\": \"AI Research\", \"team\": \"NLP Group\"}")
    @OpenApiNullable
    Map<String, String> labels,
    
    @OpenApiDescription("The ID of the embedder used for this space's memory embeddings.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String embedderId,
    
    @OpenApiDescription("Timestamp when this space was created (milliseconds since epoch).")
    @OpenApiExample("1651483320000")
    @OpenApiRequired
    Long createdAt,
    
    @OpenApiDescription("Timestamp when this space was last updated (milliseconds since epoch).")
    @OpenApiExample("1651483320000")
    @OpenApiRequired
    Long updatedAt,
    
    @OpenApiDescription("The ID of the user who owns this space.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String ownerId,
    
    @OpenApiDescription("The ID of the user who created this space.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String createdById,
    
    @OpenApiDescription("The ID of the user who last updated this space.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String updatedById,
    
    @OpenApiDescription("Whether this space is publicly readable by all users.")
    @OpenApiExample("false")
    @OpenApiRequired
    Boolean publicRead
) {
    /**
     * Empty constructor that creates an empty response with null values.
     * Required for proper JSON serialization.
     */
    public Space() {
        this(null, null, null, null, null, null, null, null, null, null);
    }
}