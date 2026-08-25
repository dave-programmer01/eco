# Stage 1: Build the Spring Boot application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime container with native Tesseract OCR support
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install native Tesseract OCR and English language training data
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng

# Copy compiled JAR
COPY --from=builder /app/target/eco-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
