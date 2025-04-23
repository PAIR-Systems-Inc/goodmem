# Build stage for Java client
FROM eclipse-temurin:8-jdk

WORKDIR /app

# Copy Gradle configuration and source code
COPY build.gradle.kts /app/
COPY src/ /app/src/
COPY init.gradle /app/init.gradle

# Set up Gradle wrapper
COPY gradle/ /app/gradle/
COPY gradlew gradlew.bat /app/
RUN chmod +x /app/gradlew

# Build the client
RUN ./gradlew --init-script init.gradle build

# The outputs will be in /app/build/
