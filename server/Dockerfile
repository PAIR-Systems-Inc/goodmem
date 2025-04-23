# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Install necessary dependencies
RUN apt-get update && apt-get install -y \
    protobuf-compiler \
    unzip \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Download and install protoc-gen-grpc-java plugin
RUN mkdir -p /usr/local/bin/protoc-plugins/
RUN wget -q -O /usr/local/bin/protoc-plugins/protoc-gen-grpc-java https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/1.72.0/protoc-gen-grpc-java-1.72.0-linux-x86_64.exe \
    && chmod +x /usr/local/bin/protoc-plugins/protoc-gen-grpc-java \
    && ln -s /usr/local/bin/protoc-plugins/protoc-gen-grpc-java /usr/local/bin/protoc-gen-grpc-java

# Copy gradle configuration files
COPY settings.gradle.kts build.gradle.kts ./
COPY server/build.gradle.kts server/

# Copy proto files
COPY proto/ proto/

# Copy source code
COPY server/src/ server/src/

# Use Gradle wrapper if it exists, otherwise use local Gradle installation
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew :server:shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/server/build/libs/goodmem-server.jar /app/goodmem-server.jar

# Expose ports for gRPC and REST
EXPOSE 9090 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app/goodmem-server.jar"]