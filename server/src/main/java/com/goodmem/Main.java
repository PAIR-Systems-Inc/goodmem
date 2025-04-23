package com.goodmem;

import goodmem.v1.SpaceOuterClass.CreateSpaceRequest;
import goodmem.v1.SpaceOuterClass.DeleteSpaceRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesResponse;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.SpaceServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());
  private static final int GRPC_PORT = 9090;
  private static final int REST_PORT = 8080;

  private Server grpcServer;
  private final SpaceServiceImpl serviceImpl;
  private final SpaceServiceGrpc.SpaceServiceBlockingStub spaceService;

  public Main() {
    this.serviceImpl = new SpaceServiceImpl();
    // Create a blocking stub for the REST-to-gRPC bridge
    this.spaceService =
        SpaceServiceGrpc.newBlockingStub(
            io.grpc.inprocess.InProcessChannelBuilder.forName("in-process").build());
  }

  public void startGrpcServer() throws IOException {
    grpcServer =
        ServerBuilder.forPort(GRPC_PORT)
            .addService(ServerInterceptors.intercept(serviceImpl, new AuthInterceptor()))
            .build()
            .start();
    logger.info("gRPC Server started, listening on port " + GRPC_PORT);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Shutting down gRPC server since JVM is shutting down");
                  try {
                    Main.this.stopGrpcServer();
                  } catch (InterruptedException e) {
                    logger.severe("Server shutdown interrupted: " + e.getMessage());
                  }
                }));
  }

  private void stopGrpcServer() throws InterruptedException {
    if (grpcServer != null) {
      grpcServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  public void startJavalinServer() {
    Javalin app =
        Javalin.create(
                config -> {
                  config.bundledPlugins.enableCors(
                      cors -> {
                        cors.addRule(it -> it.anyHost());
                      });
                })
            .start(REST_PORT);

    // Configure REST routes that map to gRPC methods
    app.post("/v1/spaces", this::handleCreateSpace);
    app.delete("/v1/spaces/{id}", this::handleDeleteSpace);
    app.get("/v1/spaces", this::handleListSpaces);

    logger.info("REST server started, listening on port " + REST_PORT);
  }

  private void handleCreateSpace(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    logger.info("REST CreateSpace request with API key: " + apiKey);

    CreateSpaceRequest.Builder requestBuilder = CreateSpaceRequest.newBuilder();
    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    if (json.containsKey("name")) {
      requestBuilder.setName((String) json.get("name"));
    }

    if (json.containsKey("embedding_model")) {
      requestBuilder.setEmbeddingModel((String) json.get("embedding_model"));
    }

    if (json.containsKey("public_read")) {
      requestBuilder.setPublicRead((Boolean) json.get("public_read"));
    }

    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }

    Space response = spaceService.createSpace(requestBuilder.build());
    ctx.json(protoToMap(response));
  }

  private void handleDeleteSpace(Context ctx) {
    String spaceId = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST DeleteSpace request for ID: " + spaceId + " with API key: " + apiKey);

    DeleteSpaceRequest request = DeleteSpaceRequest.newBuilder().setSpaceId(spaceId).build();

    spaceService.deleteSpace(request);
    ctx.status(204);
  }

  private void handleListSpaces(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    logger.info("REST ListSpaces request with API key: " + apiKey);

    ListSpacesRequest.Builder requestBuilder = ListSpacesRequest.newBuilder();

    // Handle label selectors from query parameters
    ctx.queryParamMap()
        .forEach(
            (key, values) -> {
              if (key.startsWith("label.") && !values.isEmpty()) {
                String labelKey = key.substring("label.".length());
                requestBuilder.putLabelSelectors(labelKey, values.get(0));
              }
            });

    ListSpacesResponse response = spaceService.listSpaces(requestBuilder.build());
    ctx.json(Map.of("spaces", response.getSpacesList().stream().map(this::protoToMap).toList()));
  }

  private Map<String, Object> protoToMap(Space space) {
    // Convert Space proto to a Map for JSON serialization
    return Map.of(
        "space_id", space.getSpaceId(),
        "name", space.getName(),
        "labels", space.getLabelsMap(),
        "embedding_model", space.getEmbeddingModel(),
        "created_at", space.getCreatedAt().getSeconds(),
        "owner_id", space.getOwnerId(),
        "public_read", space.getPublicRead());
  }

  public static void main(String[] args) throws IOException {
    Main server = new Main();
    server.startGrpcServer();
    server.startJavalinServer();
  }
}
