package com.goodmem.rest;

import com.goodmem.common.status.StatusOr;
import com.goodmem.operations.SystemInitOperation;
import com.goodmem.rest.dto.GetUserRequest;
import com.goodmem.rest.dto.SystemInitRequest;
import com.goodmem.rest.dto.SystemInitResponse;
import com.goodmem.rest.dto.UserResponse;
import com.goodmem.util.RestMapper;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import goodmem.v1.UserOuterClass;
import goodmem.v1.UserServiceGrpc;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.tinylog.Logger;

/**
 * REST adapter for user-related endpoints.
 * 
 * <p>This adapter handles all REST API endpoints related to users and system initialization,
 * including retrieving user details and initializing the system with a root user and API key.
 */
public class UserServiceRestAdapter implements RestAdapter {

  private final UserServiceGrpc.UserServiceBlockingStub userService;
  private final DataSource dataSource;

  /**
   * Creates a new UserServiceRestAdapter with the specified gRPC service stub.
   * 
   * @param userService The gRPC service stub to delegate to
   * @param dataSource Data source for database connection
   */
  public UserServiceRestAdapter(
      UserServiceGrpc.UserServiceBlockingStub userService,
      DataSource dataSource) {
    this.userService = userService;
    this.dataSource = dataSource;
  }

  @Override
  public void registerRoutes() {
    // No implementation required - route registration is handled by the caller
  }

  /**
   * Handles a REST request to retrieve a User by ID. Converts the hex UUID to binary format and
   * calls the gRPC service to fetch the user details.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/users/{id}",
      methods = { HttpMethod.GET },
      summary = "Get a user by ID",
      description = "Retrieves a specific user by their unique identifier. Returns the user's information including email, display name, and creation time.",
      operationId = "getUser",
      tags = "Users",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the user to retrieve",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      queryParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "email",
              description = "Alternative lookup by email address (if ID is not specified)",
              required = false,
              type = String.class,
              example = "user@example.com")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved user",
              content = @OpenApiContent(from = UserResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - user ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to view this user's information"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - user with the specified ID does not exist")
      })
  public void handleGetUser(Context ctx) {
    String userIdHex = ctx.pathParam("id");
    String email = ctx.queryParam("email");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetUser request for ID: {} with API key: {}", userIdHex, apiKey);

    // Create the DTO from the path parameter and query parameter
    GetUserRequest requestDto = new GetUserRequest(userIdHex, email);
    
    // Build the gRPC request builder
    UserOuterClass.GetUserRequest.Builder requestBuilder = UserOuterClass.GetUserRequest.newBuilder();
    
    // Set the user ID if provided
    if (!Strings.isNullOrEmpty(requestDto.userId())) {
      StatusOr<ByteString> userIdOr = convertHexToUuidBytes(requestDto.userId());
      if (userIdOr.isNotOk()) {
        setError(ctx, 400, "Invalid user ID format");
        return;
      }
      requestBuilder.setUserId(userIdOr.getValue());
    }
    
    // Set the email if provided
    if (!Strings.isNullOrEmpty(requestDto.email())) {
      requestBuilder.setEmail(requestDto.email());
    }
    
    // Call the gRPC service
    UserOuterClass.User response = userService.getUser(requestBuilder.build());
    
    // Map the gRPC response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    UserResponse responseDto = new UserResponse(
        (String) responseMap.get("user_id"),
        (String) responseMap.get("email"),
        (String) responseMap.get("display_name"),
        (String) responseMap.get("username"),
        (Long) responseMap.get("created_at"),
        (Long) responseMap.get("updated_at")
    );
    
    ctx.json(responseDto);
  }

  /**
   * Handles the system initialization endpoint. This special endpoint doesn't require
   * authentication and is used to set up the initial admin user.
   * 
   * <p>If the root user already exists, it returns a message indicating that the system
   * is already initialized. If the root user doesn't exist, it creates the root user
   * and generates an API key for administrative access.
   * 
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/system/init",
      methods = { HttpMethod.POST },
      summary = "Initialize the system",
      description = 
          "Initializes the system by creating a root user and API key. This endpoint should only be called once during " +
          "first-time setup. If the system is already initialized, the endpoint will return a success response without " +
          "creating new credentials.",
      operationId = "initializeSystem",
      tags = "System",
      requestBody =
          @OpenApiRequestBody(
              description = "Empty request body - no parameters required",
              required = false,
              content = @OpenApiContent(from = SystemInitRequest.class)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "System initialization successful",
              content = @OpenApiContent(from = SystemInitResponse.class)),
          @OpenApiResponse(
              status = "500", 
              description = "Internal server error during initialization")
      })
  public void handleSystemInit(Context ctx) {
    Logger.info("REST SystemInit request");

    // Parse the empty request body
    // This isn't strictly necessary since we don't need any parameters, but follows the pattern
    ctx.bodyAsClass(SystemInitRequest.class);
    
    // Set up database connection from connection pool
    try (Connection connection = dataSource.getConnection()) {

      // Create and execute the operation
      var operation = new SystemInitOperation(connection);
      var result = operation.execute();

      if (!result.isSuccess()) {
        Logger.error("System initialization failed: {}", result.errorMessage());
        setError(ctx, 500, result.errorMessage());
        return;
      }

      // Create the appropriate response DTO based on the result
      SystemInitResponse responseDto;
      
      if (result.alreadyInitialized()) {
        Logger.info("System is already initialized");
        responseDto = SystemInitResponse.alreadyInitializedResponse();
      } else {
        // System was just initialized
        Logger.info("System initialized successfully");
        responseDto = SystemInitResponse.newlyInitialized(
            result.apiKey(),
            result.userId().toString()
        );
      }
      
      // Return the response
      ctx.status(200).json(responseDto);

    } catch (Exception e) {
      Logger.error(e, "Error during system initialization.");
      setError(ctx, 500, "Internal server error: " + e.getMessage());
    }
  }
}