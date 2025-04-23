# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

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
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/server/build/libs/goodmem-server.jar /app/goodmem-server.jar

# Expose ports for gRPC and REST
EXPOSE 9090 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app/goodmem-server.jar"]