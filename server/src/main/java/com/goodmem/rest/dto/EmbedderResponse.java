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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DTO representing an Embedder response in the REST API.
 *
 * <p>This record serves as a data transfer object for returning embedder information in API responses,
 * providing a clear separation between the REST API and the protocol buffer implementation.
 */
@OpenApiDescription("Embedder configuration information")
@OpenApiName("EmbedderResponse")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(EmbedderOuterClass.Embedder.class)
public record EmbedderResponse(
    @OpenApiDescription("Unique identifier of the embedder")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String embedderId,
    
    @OpenApiDescription("User-facing name of the embedder")
    @OpenApiExample("OpenAI Ada-2")
    @OpenApiRequired
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
    
    @OpenApiDescription("API path for embeddings request")
    @OpenApiExample("/v1/embeddings")
    @OpenApiRequired
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
    
    @OpenApiDescription("Supported content modalities")
    @OpenApiNullable
    List<Modality> supportedModalities,
    
    @OpenApiDescription("User-defined labels for categorization")
    @OpenApiExample("{\"environment\": \"production\", \"team\": \"nlp\"}")
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
    
    @OpenApiDescription("Owner ID of the embedder")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String ownerId,
    
    @OpenApiDescription("Creation timestamp (milliseconds since epoch)")
    @OpenApiExample("1617293472000")
    @OpenApiRequired
    Long createdAt,
    
    @OpenApiDescription("Last update timestamp (milliseconds since epoch)")
    @OpenApiExample("1617293472000")
    @OpenApiRequired
    Long updatedAt,
    
    @OpenApiDescription("ID of the user who created the embedder")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String createdById,
    
    @OpenApiDescription("ID of the user who last updated the embedder")
    @OpenApiExample("550e8400-e29b-41d4-a716-446655440000")
    @OpenApiRequired
    @OpenApiStringValidation(format = "uuid")
    String updatedById
) {
    /**
     * Empty constructor for JSON deserialization.
     */
    public EmbedderResponse() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}