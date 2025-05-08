package com.goodmem.rest.dto;

import goodmem.v1.SpaceOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;

import java.util.List;

/**
 * Data Transfer Object for the response when listing spaces.
 * 
 * <p>This record represents the JSON response body from the /v1/spaces REST endpoint when listing spaces.
 * It includes a list of Space objects matching the query criteria and an optional pagination token
 * for retrieving the next set of results if more are available.
 */
@OpenApiDescription("Response containing a list of spaces and optional pagination token.")
@OpenApiName("ListSpacesResponse")
@ProtobufEquivalent(SpaceOuterClass.ListSpacesResponse.class)
public record ListSpacesResponse(
    @OpenApiDescription("The list of spaces matching the query criteria.")
    List<Space> spaces,
    
    @OpenApiDescription("Pagination token for retrieving the next set of results. Only present if there are more results available.")
    @OpenApiExample("eyJzdGFydCI6MjAsIm93bmVySWQiOiJiMzMwM2QwYS0...")
    @OpenApiNullable
    String nextToken
) {
    /**
     * Empty constructor that creates an empty response with null values.
     * Required for proper JSON serialization.
     */
    public ListSpacesResponse() {
        this(List.of(), null);
    }
}