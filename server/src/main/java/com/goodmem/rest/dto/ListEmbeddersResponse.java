package com.goodmem.rest.dto;

import goodmem.v1.EmbedderOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiByFields;
import io.javalin.openapi.Visibility;

import java.util.Collections;
import java.util.List;

/**
 * DTO representing a response containing a list of embedders.
 *
 * <p>This record serves as a data transfer object for returning a list of embedders in API responses,
 * providing a clear separation between the REST API and the protocol buffer implementation.
 */
@OpenApiDescription("Response containing a list of embedders")
@OpenApiName("ListEmbeddersResponse")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(EmbedderOuterClass.ListEmbeddersResponse.class)
public record ListEmbeddersResponse(
    @OpenApiDescription("List of embedder configurations")
    @OpenApiRequired
    List<EmbedderResponse> embedders
) {
    /**
     * Default constructor for JSON deserialization.
     */
    public ListEmbeddersResponse() {
        this(Collections.emptyList());
    }
}