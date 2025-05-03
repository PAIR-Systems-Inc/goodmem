package com.goodmem.security;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.ApiKeys.UserWithApiKey;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Optional;
import org.tinylog.Logger;

/**
 * Intercept requests and lookup user profile information.
 */
public class AuthInterceptor implements ServerInterceptor {
  protected static final Metadata.Key<String> API_KEY_METADATA_KEY =
      Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

  // Context key for the authenticated user object
  public static final Context.Key<User> USER_CONTEXT_KEY = Context.key("user");
  
  private final com.zaxxer.hikari.HikariDataSource dataSource;
  
  public AuthInterceptor(com.zaxxer.hikari.HikariDataSource dataSource) {
    this.dataSource = dataSource;
  }

  private <ReqT, RespT> ServerCall.Listener<ReqT> abortUnauthenticated(
      ServerCall<ReqT, RespT> call, String errorMsg) {
    Logger.warn("Authentication failed: {}", errorMsg);
    call.close(
        Status.UNAUTHENTICATED.withDescription(errorMsg),
        new Metadata()); // Close the call
    return new ServerCall.Listener<ReqT>() {};
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String apiKeyString = headers.get(API_KEY_METADATA_KEY);
    Logger.info("API Key: {}", (apiKeyString != null ? "present" : "absent"));
    
    // No API key provided - cannot authenticate
    if (apiKeyString == null || apiKeyString.isEmpty()) {
      return abortUnauthenticated(call, "No API key provided.");
    }
    
    // Attempt to authenticate with the provided API key
    try (java.sql.Connection conn = dataSource.getConnection()) {
      // Look up user by API key
      StatusOr<Optional<UserWithApiKey>> userOr =
          com.goodmem.db.ApiKeys.getUserByApiKey(conn, apiKeyString);
          
      if (!userOr.isOk()) {
        return abortUnauthenticated(call, "Error looking up API key.");
      }
      
      Optional<com.goodmem.db.ApiKeys.UserWithApiKey> userWithKeyOpt = userOr.getValue();
      if (userWithKeyOpt.isEmpty()) {
        return abortUnauthenticated(call, "Invalid or expired API key.");
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
      Logger.error(e, "Unexpected failure during authentication.");
      return abortUnauthenticated(call, "Error during authentication.");
    }
  }
}
