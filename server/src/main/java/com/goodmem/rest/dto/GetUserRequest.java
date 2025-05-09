package com.goodmem.rest.dto;

import goodmem.v1.UserOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;

/**
 * Data Transfer Object for getting a user.
 * 
 * <p>This record represents the request parameters for the /v1/users/{id} endpoint.
 * It supports looking up users by their UUID.
 */
@OpenApiDescription("Request for retrieving user information.")
@OpenApiName("GetUserRequest")
@ProtobufEquivalent(UserOuterClass.GetUserRequest.class)
public record GetUserRequest(
    @OpenApiDescription("The unique identifier of the user to retrieve")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String userId,
    
    @OpenApiDescription("Alternative lookup by email address. If both userId and email are provided, userId takes precedence.")
    @OpenApiExample("user@example.com")
    @OpenApiNullable
    String email
) {
    /**
     * Constructor for creating a request with only a user ID.
     * 
     * @param userId The UUID of the user to retrieve
     */
    public GetUserRequest(String userId) {
        this(userId, null);
    }
    
    /**
     * Empty constructor for JSON deserialization.
     */
    public GetUserRequest() {
        this(null, null);
    }
}