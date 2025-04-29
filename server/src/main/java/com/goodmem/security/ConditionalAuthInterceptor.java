package com.goodmem.security;

import com.goodmem.MethodAuthorizer;
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
  
  private final MethodAuthorizer methodAuthorizer;
  private final AuthInterceptor authInterceptor;
  
  /**
   * Creates a new ConditionalAuthInterceptor with the specified method authorizer and auth interceptor.
   * 
   * @param methodAuthorizer the authorizer that determines which methods can bypass authentication
   * @param authInterceptor the interceptor used for regular authentication
   */
  public ConditionalAuthInterceptor(MethodAuthorizer methodAuthorizer, AuthInterceptor authInterceptor) {
    this.methodAuthorizer = methodAuthorizer;
    this.authInterceptor = authInterceptor;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
      
    String fullMethodName = call.getMethodDescriptor().getFullMethodName();
    logger.info("Processing request for method: " + fullMethodName);
    
    // Check if this method is allowed without authentication
    if (methodAuthorizer.isMethodAllowed(fullMethodName)) {
      logger.info("Method " + fullMethodName + " allowed without authentication");
      
      // For allowed methods, we proceed without authentication
      // We can set a null user or an "anonymous" user in the context if needed
      return next.startCall(call, headers);
    }
    
    // For all other methods, delegate to the regular auth interceptor
    return authInterceptor.interceptCall(call, headers, next);
  }
}