package com.goodmem.rest.dto;

import goodmem.v1.UserOuterClass;
import io.javalin.openapi.OpenApiByFields;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;
import io.javalin.openapi.Visibility;

/**
 * Data Transfer Object for user information.
 * 
 * <p>This record represents the response from the user-related endpoints.
 * It includes user identification and profile information but excludes
 * any sensitive data like passwords or API keys.
 */
@OpenApiDescription("User information response")
@OpenApiName("UserResponse")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(UserOuterClass.User.class)
public record UserResponse(
    @OpenApiDescription("The unique identifier of the user")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String userId,
    
    @OpenApiDescription("The user's email address")
    @OpenApiExample("user@example.com")
    @OpenApiRequired
    String email,
    
    @OpenApiDescription("The user's display name")
    @OpenApiExample("John Doe")
    @OpenApiRequired
    String displayName,
    
    @OpenApiDescription("The user's username (optional)")
    @OpenApiExample("johndoe")
    String username,
    
    @OpenApiDescription("Timestamp when the user was created (milliseconds since epoch)")
    @OpenApiExample("1620000000000")
    @OpenApiRequired
    Long createdAt,
    
    @OpenApiDescription("Timestamp when the user was last updated (milliseconds since epoch)")
    @OpenApiExample("1620100000000")
    @OpenApiRequired
    Long updatedAt
) {
    /**
     * Empty constructor for JSON deserialization.
     */
    public UserResponse() {
        this(null, null, null, null, null, null);
    }
}