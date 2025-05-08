package com.goodmem.rest.dto;

import goodmem.v1.SpaceOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiStringValidation;

import java.util.Map;

/**
 * Data Transfer Object for listing spaces with optional filtering and pagination.
 * 
 * <p>This record represents the query parameters used for filtering and paginating
 * space listings in the REST API. In the gRPC implementation, these fields are part 
 * of the ListSpacesRequest message.
 */
@OpenApiDescription("Request parameters for listing and filtering spaces.")
@OpenApiName("ListSpacesRequest")
@ProtobufEquivalent(SpaceOuterClass.ListSpacesRequest.class)
public record ListSpacesRequest(
    @OpenApiDescription("Filter spaces by owner ID. If not provided, shows spaces based on permissions.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiNullable
    @OpenApiStringValidation(format = "uuid")
    String ownerId,
    
    @OpenApiDescription("Filter spaces by label key-value pairs. All specified labels must match.")
    @OpenApiExample("{\"project\": \"AI Research\", \"team\": \"NLP Group\"}")
    @OpenApiNullable
    Map<String, String> labelSelectors,
    
    @OpenApiDescription("Filter spaces by name using glob pattern matching.")
    @OpenApiExample("Research*")
    @OpenApiNullable
    String nameFilter,
    
    @OpenApiDescription("Maximum number of results to return in a single page.")
    @OpenApiExample("20")
    @OpenApiNullable
    Integer maxResults,
    
    @OpenApiDescription("Pagination token for retrieving the next set of results.")
    @OpenApiExample("eyJzdGFydCI6MjAsIm93bmVySWQiOiJiMzMwM2QwYS0...")
    @OpenApiNullable
    String nextToken,
    
    @OpenApiDescription("Field to sort by. Supported values: \"created_time\", \"name\", \"updated_time\".")
    @OpenApiExample("name")
    @OpenApiNullable
    String sortBy,
    
    @OpenApiDescription("Sort order (ASCENDING or DESCENDING).")
    @OpenApiExample("ASCENDING")
    @OpenApiNullable
    SortOrder sortOrder
) {
    /**
     * Empty constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public ListSpacesRequest() {
        this(null, null, null, null, null, null, null);
    }
}