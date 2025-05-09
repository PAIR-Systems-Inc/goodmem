package com.goodmem.rest.dto;

import goodmem.v1.EmbedderOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiObjectValidation;
import io.javalin.openapi.OpenApiStringValidation;
import io.javalin.openapi.OpenApiByFields;
import io.javalin.openapi.Visibility;

import java.util.Map;

/**
 * DTO representing a request to list embedders with optional filtering.
 *
 * <p>This record serves as a data transfer object for accepting embedder listing parameters
 * in API requests, providing a clear separation between the REST API and the protocol buffer
 * implementation.
 */
@OpenApiDescription("Request parameters for listing embedders with optional filtering")
@OpenApiName("ListEmbeddersRequest")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(EmbedderOuterClass.ListEmbeddersRequest.class)
public record ListEmbeddersRequest(
    @OpenApiDescription("Filter embedders by owner ID. If not provided, shows embedders based on permissions.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiNullable
    @OpenApiStringValidation(format = "uuid")
    String ownerId,
    
    @OpenApiDescription("Filter embedders by provider type (OPENAI, VLLM, TEI)")
    @OpenApiExample("OPENAI")
    @OpenApiNullable
    ProviderType providerType,
    
    @OpenApiDescription("Filter by label key-value pairs. Multiple label filters can be specified.")
    @OpenApiExample("{\"environment\": \"production\", \"team\": \"nlp\"}")
    @OpenApiObjectValidation(maxProperties = "20")
    @OpenApiNullable
    Map<String, String> labelSelectors
) {
    /**
     * Default constructor for JSON deserialization.
     */
    public ListEmbeddersRequest() {
        this(null, null, null);
    }
}