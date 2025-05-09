/**
 * Data Transfer Objects (DTOs) for the GoodMem REST API.
 *
 * <p>This package contains Java record classes that serve as DTOs for the GoodMem REST API.
 * These DTOs provide a clean separation between the REST API layer and the underlying gRPC
 * service implementation. They define the JSON structure for both request bodies and response
 * payloads, enabling proper documentation and validation in the OpenAPI specification.
 *
 * <h2>Key Design Patterns</h2>
 *
 * <h3>1. Protocol Buffer Mapping</h3>
 * <p>Each DTO class is annotated with {@link com.goodmem.rest.dto.ProtobufEquivalent} to indicate
 * its corresponding Protocol Buffer message class from the gRPC service definition. This establishes
 * a clear and documented relationship between REST DTOs and gRPC messages.
 * <pre>
 * {@code @ProtobufEquivalent(SpaceOuterClass.CreateSpaceRequest.class)}
 * </pre>
 *
 * <h3>2. OpenAPI Annotations</h3>
 * <p>All DTOs use comprehensive OpenAPI annotations to generate accurate API documentation:
 * <ul>
 *   <li>{@code @OpenApiName} - Provides a human-friendly name for the schema</li>
 *   <li>{@code @OpenApiDescription} - Documents the purpose of the DTO</li>
 *   <li>{@code @OpenApiByFields} - Configures OpenAPI visibility of fields</li>
 *   <li>{@code @OpenApiRequired}/{@code @OpenApiNullable} - Documents required vs optional fields</li>
 *   <li>{@code @OpenApiStringValidation} - Specifies string format and length constraints</li>
 *   <li>{@code @OpenApiExample} - Provides example values for fields</li>
 * </ul>
 *
 * <h3>3. Java Record Structure</h3>
 * <p>All DTOs are implemented as Java records to ensure immutability and reduce boilerplate.
 * Each record follows a consistent structure:
 * <ul>
 *   <li>Fields annotated with descriptive OpenAPI annotations</li>
 *   <li>An empty no-args constructor for JSON deserialization</li>
 *   <li>Optional convenience constructors for common use cases</li>
 * </ul>
 *
 * <h3>4. Minimal Pre-validation</h3>
 * <p>DTOs generally avoid duplicating validation logic that exists in the gRPC implementation.
 * Only minimal pre-validation is performed in the REST layer to:
 * <ul>
 *   <li>Detect obvious request errors before hitting the gRPC service</li>
 *   <li>Validate constraints that aren't easily expressed in Protocol Buffers (e.g., mutually exclusive fields)</li>
 *   <li>Provide user-friendly error messages specific to the REST API</li>
 * </ul>
 * <p>The bulk of business logic validation is delegated to the gRPC service implementation.
 *
 * <h3>5. Protocol Buffer Independence</h3>
 * <p>DTO classes avoid direct dependencies on Protocol Buffer implementation details.
 * When a gRPC enum or nested type is needed in a DTO, a corresponding Java class
 * is created in this package (e.g., {@link com.goodmem.rest.dto.SortOrder}) with methods
 * to convert between the DTO type and Protocol Buffer type.
 *
 * <h3>6. REST API Conventions</h3>
 * <p>DTOs follow REST API conventions, particularly:
 * <ul>
 *   <li>Using camelCase for field names (vs snake_case in Protocol Buffers)</li>
 *   <li>Using string UUIDs (vs binary bytes in Protocol Buffers)</li>
 *   <li>Using milliseconds since epoch for timestamps (vs Timestamp message in Protocol Buffers)</li>
 *   <li>Mapping oneof fields to mutually exclusive fields in DTOs with minimal validation</li>
 * </ul>
 *
 * <h3>7. Request/Response Patterns</h3>
 * <p>The package follows consistent patterns for different operations:
 * <ul>
 *   <li>Create: {@code Create[Resource]Request} -&gt; {@code [Resource]}</li>
 *   <li>Get: {@code Get[Resource]Request} -&gt; {@code [Resource]}</li>
 *   <li>List: {@code List[Resource]Request} -&gt; {@code List[Resource]Response}</li>
 *   <li>Update: {@code Update[Resource]Request} -&gt; {@code [Resource]}</li>
 *   <li>Delete: {@code Delete[Resource]Request} -&gt; No content response (204)</li>
 * </ul>
 *
 * <h3>8. Documentation Guidelines</h3>
 * <p>All DTOs include comprehensive Javadoc comments that explain:
 * <ul>
 *   <li>The purpose and usage context of the DTO</li>
 *   <li>Relationship to the corresponding REST endpoint</li>
 *   <li>Special handling or validation requirements</li>
 *   <li>Any nuances or behaviors specific to the DTO</li>
 * </ul>
 *
 * @see com.goodmem.rest.dto.ProtobufEquivalent
 * @see io.javalin.openapi.OpenApiName
 * @see io.javalin.openapi.OpenApiDescription
 */
package com.goodmem.rest.dto;