# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
# Convert line endings of mvnw to unix style just in case of Windows host
RUN tr -d '\r' < mvnw > mvnw_unix && mv mvnw_unix mvnw && chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
