package com.goodmem.rest.dto;

import goodmem.v1.UserOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;

/**
 * Data Transfer Object for system initialization response.
 * 
 * <p>This record represents the JSON response body from the /v1/system/init REST endpoint.
 * If the system is already initialized, only the initialized flag and message fields will be present.
 * If the system is being initialized for the first time, the response will include the root API key
 * and user ID for the created admin user.
 */
@OpenApiDescription("Response from the system initialization endpoint.")
@OpenApiName("SystemInitResponse")
@ProtobufEquivalent(UserOuterClass.InitializeSystemResponse.class)
public record SystemInitResponse(
    @OpenApiDescription("Indicates whether the system was already initialized before this request.")
    @OpenApiExample("true")
    @OpenApiRequired
    Boolean alreadyInitialized,
    
    @OpenApiDescription("A human-readable message about the initialization status.")
    @OpenApiExample("System initialized successfully")
    @OpenApiRequired
    String message,
    
    @OpenApiDescription("The API key for the root user. Only present if system was just initialized (not previously initialized).")
    @OpenApiExample("goodmem_f923b5e6a07e4fb0bdcaa6b7d4c8fea1")
    @OpenApiNullable
    String rootApiKey,
    
    @OpenApiDescription("The user ID of the root user. Only present if system was just initialized (not previously initialized).")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiNullable
    @OpenApiStringValidation(format = "uuid")
    String userId
) {
    /**
     * Creates a response for the case when the system is already initialized.
     * 
     * @return A SystemInitResponse with alreadyInitialized=true and appropriate message
     */
    public static SystemInitResponse alreadyInitializedResponse() {
        return new SystemInitResponse(
            true,
            "System is already initialized",
            null,
            null
        );
    }
    
    /**
     * Creates a response for the case when the system was just initialized successfully.
     * 
     * @param apiKey The generated root API key
     * @param userId The UUID of the root user
     * @return A SystemInitResponse with the API key and user ID
     */
    public static SystemInitResponse newlyInitialized(String apiKey, String userId) {
        return new SystemInitResponse(
            false,
            "System initialized successfully",
            apiKey,
            userId
        );
    }
    
    /**
     * Empty constructor for JSON deserialization.
     */
    public SystemInitResponse() {
        this(null, null, null, null);
    }
}