package com.goodmem.rest.dto;

import goodmem.v1.EmbedderOuterClass;
import io.javalin.openapi.OpenApiDescription;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiStringValidation;
import io.javalin.openapi.OpenApiByFields;
import io.javalin.openapi.Visibility;
import io.javalin.openapi.OpenApiObjectValidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DTO representing a request to create a new Embedder in the REST API.
 *
 * <p>This record serves as a data transfer object for accepting embedder creation parameters
 * in API requests, providing a clear separation between the REST API and the protocol buffer
 * implementation.
 */
@OpenApiDescription("Request body for creating a new Embedder. An Embedder represents a configuration for vectorizing content.")
@OpenApiName("EmbedderCreationRequest")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(EmbedderOuterClass.CreateEmbedderRequest.class)
public record CreateEmbedderRequest(
    @OpenApiDescription("User-facing name of the embedder")
    @OpenApiExample("OpenAI Ada-2")
    @OpenApiRequired
    @OpenApiStringValidation(minLength = "1", maxLength = "255")
    String displayName,
    
    @OpenApiDescription("Description of the embedder")
    @OpenApiExample("OpenAI's text embedding model with 1536 dimensions")
    @OpenApiNullable
    String description,
    
    @OpenApiDescription("Type of embedding provider")
    @OpenApiExample("OPENAI")
    @OpenApiRequired
    ProviderType providerType,
    
    @OpenApiDescription("API endpoint URL")
    @OpenApiExample("https://api.openai.com")
    @OpenApiRequired
    String endpointUrl,
    
    @OpenApiDescription("API path for embeddings request (defaults to /v1/embeddings if not provided)")
    @OpenApiExample("/v1/embeddings")
    @OpenApiNullable
    String apiPath,
    
    @OpenApiDescription("Model identifier")
    @OpenApiExample("text-embedding-3-small")
    @OpenApiRequired
    String modelIdentifier,
    
    @OpenApiDescription("Output vector dimensions")
    @OpenApiExample("1536")
    @OpenApiRequired
    Integer dimensionality,
    
    @OpenApiDescription("Maximum input sequence length")
    @OpenApiExample("8192")
    @OpenApiNullable
    Integer maxSequenceLength,
    
    @OpenApiDescription("Supported content modalities (defaults to TEXT if not provided)")
    @OpenApiExample("[\"TEXT\", \"IMAGE\"]")
    @OpenApiNullable
    List<Modality> supportedModalities,
    
    @OpenApiDescription("API credentials (required)")
    @OpenApiExample("sk-1234567890abcdef")
    @OpenApiRequired
    String credentials,
    
    @OpenApiDescription("User-defined labels for categorization")
    @OpenApiExample("{\"environment\": \"production\", \"team\": \"nlp\"}")
    @OpenApiObjectValidation(maxProperties = "20")
    @OpenApiNullable
    Map<String, String> labels,
    
    @OpenApiDescription("Version information")
    @OpenApiExample("1.0.0")
    @OpenApiNullable
    String version,
    
    @OpenApiDescription("Monitoring endpoint URL")
    @OpenApiExample("https://monitoring.example.com/embedders/status")
    @OpenApiNullable
    String monitoringEndpoint,
    
    @OpenApiDescription("Optional owner ID. If not provided, derived from the authentication context. Requires CREATE_EMBEDDER_ANY permission if specified.")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiNullable
    @OpenApiStringValidation(format = "uuid")
    String ownerId
) {
    /**
     * Validates the required fields for creating an embedder.
     *
     * @throws IllegalArgumentException if any required field is missing
     */
    public void validate() throws IllegalArgumentException {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Display name is required");
        }
        
        if (providerType == null) {
            throw new IllegalArgumentException("Provider type is required");
        }
        
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint URL is required");
        }
        
        if (modelIdentifier == null || modelIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Model identifier is required");
        }
        
        if (dimensionality == null || dimensionality <= 0) {
            throw new IllegalArgumentException("Dimensionality must be a positive integer");
        }
        
        if (credentials == null || credentials.trim().isEmpty()) {
            throw new IllegalArgumentException("Credentials are required");
        }
    }
    
    /**
     * Default constructor that creates an empty request with null values.
     * Required for proper JSON deserialization.
     */
    public CreateEmbedderRequest() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}