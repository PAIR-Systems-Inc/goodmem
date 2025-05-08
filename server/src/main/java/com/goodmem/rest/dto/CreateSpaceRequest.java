package com.goodmem.rest.dto;

import goodmem.v1.SpaceOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiObjectValidation;
import io.javalin.openapi.OpenApiPropertyType;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;
import io.javalin.openapi.OpenApiByFields;
import io.javalin.openapi.Visibility;

import java.util.Collections;
import java.util.Map;

/**
 * Data Transfer Object for creating a new Space in the GoodMem API.
 * 
 * <p>This record represents the JSON request body for the /v1/spaces REST endpoint.
 * It encapsulates all the necessary information to create a new Space, which serves as
 * a container for organizing related memories. The Space definition includes basic
 * metadata like name and visibility settings, along with label key-value pairs for
 * categorization.
 * 
 * <p>The design aligns with the CreateSpaceRequest proto message definition, but follows
 * REST API conventions for field naming (camelCase).
 */
@OpenApiDescription("Request body for creating a new Space. A Space is a container for organizing related memories with vector embeddings.")
@OpenApiName("SpaceCreationRequest")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(SpaceOuterClass.CreateSpaceRequest.class)
public record CreateSpaceRequest(
    @OpenApiDescription("The desired name for the space. Must be unique within the user's scope.")
    @OpenApiExample("My Research Space")
    @OpenApiRequired
    @OpenApiStringValidation(minLength = "1", maxLength = "255")
    String name,

    @OpenApiDescription("The UUID of an existing Embedder configuration to be used for this space. This determines how memories within this space will be vectorized.")
    @OpenApiExample("00000000-0000-0000-0000-000000000001")
    @OpenApiPropertyType(definedBy = String.class)
    @OpenApiStringValidation(format = "uuid")
    @OpenApiRequired
    String embedderId,

    @OpenApiDescription("Indicates if the space and its memories can be read by unauthenticated users or users other than the owner. Defaults to false.")
    @OpenApiExample("false")
    @OpenApiNullable
    Boolean publicRead,

    @OpenApiDescription("A set of key-value pairs to categorize or tag the space. Used for filtering and organizational purposes.")
    @OpenApiExample("{\"category\":\"research\", \"project\":\"ai-embeddings\"}")
    @OpenApiObjectValidation(maxProperties = "20")
    @OpenApiNullable
    Map<String, String> labels,

    @OpenApiDescription("Optional owner ID. If not provided, derived from the authentication context. Requires CREATE_SPACE_ANY permission if specified.")
    @OpenApiPropertyType(definedBy = String.class)
    @OpenApiStringValidation(format = "uuid")
    @OpenApiNullable
    String ownerId
) {
    /**
     * Convenience constructor with only the essential fields, using empty map for labels.
     * 
     * @param name The name of the space
     * @param embedderId The UUID of the embedder to use with this space
     * @param publicRead Whether the space should be publicly readable
     */
    public CreateSpaceRequest(String name, String embedderId, Boolean publicRead) {
        this(name, embedderId, publicRead, Collections.emptyMap(), null);
    }
    
    /**
     * Default constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public CreateSpaceRequest() {
        this(null, null, null, null, null);
    }
}