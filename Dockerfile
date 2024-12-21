# Use OpenJDK 17 as the base image
FROM openjdk:17-jdk-alpine

# Set working directory in container
WORKDIR /app

# Add basic packages and set executable permission
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN apk add --no-cache curl bash && \
    chmod +x mvnw && \
    dos2unix mvnw

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application
RUN ./mvnw clean install -DskipTests

# Use a smaller runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy the built jar from the previous stage
COPY --from=0 /app/target/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Set the startup command
ENTRYPOINT ["java", "-jar", "app.jar"] 