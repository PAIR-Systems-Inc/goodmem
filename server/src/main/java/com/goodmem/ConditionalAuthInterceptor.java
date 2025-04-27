package com.goodmem;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.logging.Logger;

/**
 * A gRPC ServerInterceptor that conditionally applies authentication based on the method being called.
 * This allows specific methods (like system initialization) to bypass authentication requirements.
 */
public class ConditionalAuthInterceptor implements ServerInterceptor {
  private static final Logger logger = Logger.getLogger(ConditionalAuthInterceptor.class.getName());
  private static final Metadata.Key<String> API_KEY_METADATA_KEY =
      Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

  public static final Context.Key<String> PRINCIPAL_ID_CONTEXT_KEY = AuthInterceptor.PRINCIPAL_ID_CONTEXT_KEY;
  
  private final MethodAuthorizer methodAuthorizer;
  
  /**
   * Creates a new ConditionalAuthInterceptor with the specified method authorizer.
   * 
   * @param methodAuthorizer the authorizer that determines which methods can bypass authentication
   */
  public ConditionalAuthInterceptor(MethodAuthorizer methodAuthorizer) {
    this.methodAuthorizer = methodAuthorizer;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
      
    String fullMethodName = call.getMethodDescriptor().getFullMethodName();
    logger.info("Processing request for method: " + fullMethodName);
    
    // Check if this method is allowed without authentication
    if (methodAuthorizer.isMethodAllowed(fullMethodName)) {
      logger.info("Method " + fullMethodName + " allowed without authentication");
      
      // For allowed methods, we set a special principal ID indicating no authentication
      String principalId = "unauthenticated";
      Context context = Context.current().withValue(PRINCIPAL_ID_CONTEXT_KEY, principalId);
      return Contexts.interceptCall(context, call, headers, next);
    }
    
    // For all other methods, require authentication
    String apiKey = headers.get(API_KEY_METADATA_KEY);
    logger.info("API Key: " + (apiKey != null ? "present" : "absent"));

    // Standard authentication logic, same as in AuthInterceptor
    // TODO: Validate API key against authentication service
    // TODO: Lookup or derive proper principal ID
    String principalId = "user-123";

    Context context = Context.current().withValue(PRINCIPAL_ID_CONTEXT_KEY, principalId);
    return Contexts.interceptCall(context, call, headers, next);
  }
}