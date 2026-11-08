# Stage 1: Build JAR using maven:3.9.6-eclipse-temurin-21-alpine
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Pre-cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build executable fat JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime image using eclipse-temurin:21-jre-alpine
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy executable jar from builder stage
COPY --from=builder /build/target/gateway-*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
