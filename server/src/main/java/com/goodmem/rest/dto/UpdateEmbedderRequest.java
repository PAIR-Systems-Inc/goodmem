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

import java.util.List;
import java.util.Map;

/**
 * DTO representing a request to update an Embedder in the REST API.
 *
 * <p>This record serves as a data transfer object for accepting embedder update parameters
 * in API requests, providing a clear separation between the REST API and the protocol buffer
 * implementation.
 * 
 * <p>Only the fields that need to be updated should be included in the request.
 * Fields that are null or not provided will remain unchanged.
 */
@OpenApiDescription("Request body for updating an existing Embedder. Only fields that should be updated need to be included.")
@OpenApiName("UpdateEmbedderRequest")
@OpenApiByFields(Visibility.PUBLIC)
@ProtobufEquivalent(EmbedderOuterClass.UpdateEmbedderRequest.class)
public record UpdateEmbedderRequest(
    @OpenApiDescription("User-facing name of the embedder")
    @OpenApiExample("Updated OpenAI Embedder")
    @OpenApiNullable
    @OpenApiStringValidation(minLength = "1", maxLength = "255")
    String displayName,
    
    @OpenApiDescription("Description of the embedder")
    @OpenApiExample("Updated description for OpenAI's text embedding model")
    @OpenApiNullable
    String description,
    
    @OpenApiDescription("API endpoint URL")
    @OpenApiExample("https://api.openai.com")
    @OpenApiNullable
    String endpointUrl,
    
    @OpenApiDescription("API path for embeddings request")
    @OpenApiExample("/v1/embeddings")
    @OpenApiNullable
    String apiPath,
    
    @OpenApiDescription("Model identifier")
    @OpenApiExample("text-embedding-3-small")
    @OpenApiNullable
    String modelIdentifier,
    
    @OpenApiDescription("Output vector dimensions")
    @OpenApiExample("1536")
    @OpenApiNullable
    Integer dimensionality,
    
    @OpenApiDescription("Maximum input sequence length")
    @OpenApiExample("8192")
    @OpenApiNullable
    Integer maxSequenceLength,
    
    @OpenApiDescription("Supported content modalities")
    @OpenApiExample("[\"TEXT\", \"IMAGE\"]")
    @OpenApiNullable
    List<Modality> supportedModalities,
    
    @OpenApiDescription("API credentials")
    @OpenApiExample("sk-1234567890abcdef")
    @OpenApiNullable
    String credentials,
    
    @OpenApiDescription("Replace all existing labels with these (mutually exclusive with mergeLabels)")
    @OpenApiExample("{\"environment\": \"production\", \"team\": \"nlp\"}")
    @OpenApiObjectValidation(maxProperties = "20")
    @OpenApiNullable
    Map<String, String> replaceLabels,
    
    @OpenApiDescription("Merge these labels with existing ones (mutually exclusive with replaceLabels)")
    @OpenApiExample("{\"environment\": \"production\", \"team\": \"nlp\"}")
    @OpenApiObjectValidation(maxProperties = "20")
    @OpenApiNullable
    Map<String, String> mergeLabels,
    
    @OpenApiDescription("Version information")
    @OpenApiExample("1.0.0")
    @OpenApiNullable
    String version,
    
    @OpenApiDescription("Monitoring endpoint URL")
    @OpenApiExample("https://monitoring.example.com/embedders/status")
    @OpenApiNullable
    String monitoringEndpoint
) {
    /**
     * Validates the label update strategy.
     * Only one of replaceLabels or mergeLabels can be set.
     *
     * @throws IllegalArgumentException if both replaceLabels and mergeLabels are provided
     */
    public void validateLabelStrategy() throws IllegalArgumentException {
        if (replaceLabels != null && mergeLabels != null) {
            throw new IllegalArgumentException("Only one of replaceLabels or mergeLabels can be provided");
        }
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public UpdateEmbedderRequest() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}