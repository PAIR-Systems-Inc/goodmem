package com.goodmem.rest.dto;

import goodmem.v1.EmbedderOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;
import io.javalin.openapi.OpenApiByFields;
import io.javalin.openapi.Visibility;

/**
 * DTO representing a request to retrieve an Embedder by ID.
 *
 * <p>This record serves as a data transfer object for accepting embedder ID parameters
 * in API requests, providing a clear separation between the REST API and the protocol buffer
 * implementation.
 */
@OpenApiDescription("Request parameters for retrieving an embedder by ID")
@OpenApiName("GetEmbedderRequest")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(EmbedderOuterClass.GetEmbedderRequest.class)
public record GetEmbedderRequest(
    @OpenApiDescription("Unique identifier of the embedder to retrieve")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String embedderId
) {
    /**
     * Default constructor for JSON deserialization.
     */
    public GetEmbedderRequest() {
        this(null);
    }
}