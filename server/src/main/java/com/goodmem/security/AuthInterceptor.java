package com.goodmem.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.logging.Logger;

public class AuthInterceptor implements ServerInterceptor {
  private static final Logger logger = Logger.getLogger(AuthInterceptor.class.getName());
  protected static final Metadata.Key<String> API_KEY_METADATA_KEY =
      Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

  // Context key for the authenticated user object
  public static final Context.Key<User> USER_CONTEXT_KEY = Context.key("user");
  
  private final com.zaxxer.hikari.HikariDataSource dataSource;
  
  public AuthInterceptor(com.zaxxer.hikari.HikariDataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String apiKeyString = headers.get(API_KEY_METADATA_KEY);
    logger.info("API Key: " + (apiKeyString != null ? "present" : "absent"));
    
    // No API key provided - cannot authenticate
    if (apiKeyString == null || apiKeyString.isEmpty()) {
      logger.warning("No API key provided");
      return next.startCall(call, headers);
    }
    
    // Attempt to authenticate with the provided API key
    try (java.sql.Connection conn = dataSource.getConnection()) {
      // Look up user by API key
      com.goodmem.common.status.StatusOr<java.util.Optional<com.goodmem.db.ApiKeys.UserWithApiKey>> userOr = 
          com.goodmem.db.ApiKeys.getUserByApiKey(conn, apiKeyString);
          
      if (!userOr.isOk()) {
        logger.warning("Error looking up API key: " + userOr.getStatus().getMessage());
        return next.startCall(call, headers);
      }
      
      java.util.Optional<com.goodmem.db.ApiKeys.UserWithApiKey> userWithKeyOpt = userOr.getValue();
      if (userWithKeyOpt.isEmpty()) {
        logger.warning("Invalid or expired API key");
        return next.startCall(call, headers);
      }
      
      // We have a valid user - create security user with appropriate role
      com.goodmem.db.User dbUser = userWithKeyOpt.get().user();
      
      // TODO: In a real implementation, look up the user's roles from the database
      // For now, we'll assign ADMIN for a specific user ID and USER for everyone else
      Role role;
      if (dbUser.userId().toString().equals("00000000-0000-0000-0000-000000000001")) {
        role = Roles.ADMIN.role();
      } else {
        role = Roles.USER.role();
      }
      
      // Create security User from db.User with the appropriate role
      User securityUser = new DefaultUserImpl(dbUser, role);
      
      // Add the user to the context
      Context context = Context.current().withValue(USER_CONTEXT_KEY, securityUser);
      return Contexts.interceptCall(context, call, headers, next);
      
    } catch (Exception e) {
      logger.warning("Error during authentication: " + e.getMessage());
      // Continue without authentication
      return next.startCall(call, headers);
    }
  }
}
