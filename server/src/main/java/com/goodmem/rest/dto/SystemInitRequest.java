package com.goodmem.rest.dto;

import goodmem.v1.UserOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiName;

/**
 * Data Transfer Object for system initialization request.
 * 
 * <p>This is an empty request as the system initialization endpoint doesn't
 * require any parameters. The endpoint is only used during first-time setup
 * to create the root user and API key.
 */
@OpenApiDescription("Request for initializing the system. No parameters required.")
@OpenApiName("SystemInitRequest")
@ProtobufEquivalent(UserOuterClass.InitializeSystemRequest.class)
public record SystemInitRequest() {
    // Empty record as no parameters are needed for initialization
}