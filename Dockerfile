# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make mvnw executable and download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

# Expose port (Render will set PORT env var)
EXPOSE 8081

# Run the application with memory limits for Render free tier
ENTRYPOINT ["java", "-Xms128m", "-Xmx384m", "-XX:+UseG1GC", "-jar", "app.jar"]
