package com.goodmem;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.logging.Logger;

public class AuthInterceptor implements ServerInterceptor {
    private static final Logger logger = Logger.getLogger(AuthInterceptor.class.getName());
    private static final Metadata.Key<String> API_KEY_METADATA_KEY = 
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> PRINCIPAL_ID_CONTEXT_KEY = 
            Context.key("principal-id");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String apiKey = headers.get(API_KEY_METADATA_KEY);
        logger.info("API Key: " + (apiKey != null ? "present" : "absent"));

        // For now, we accept all API keys and set a dummy principal ID
        // TODO: Validate API key against authentication service
        // TODO: Lookup or derive proper principal ID
        String principalId = "user-123";

        Context context = Context.current().withValue(PRINCIPAL_ID_CONTEXT_KEY, principalId);
        return Contexts.interceptCall(context, call, headers, next);
    }
}