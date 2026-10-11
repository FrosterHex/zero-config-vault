# ==============================================================================
# Multi-stage Dockerfile for Zero-Config Vault Backend (Spring Boot 3.x / Java 17)
# ==============================================================================

# Stage 1: Build JAR package
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal Distroless / Alpine-based JRE Runtime
FROM eclipse-temurin:17-jre-jammy AS runner
WORKDIR /app

# Install curl for health check probing
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as non-privileged service user
RUN groupadd -r vault && useradd -r -g vault -d /app -s /sbin/nologin vault

# Copy build artifact from builder stage
COPY --from=builder --chown=vault:vault /workspace/target/secure-config-vault-*.jar /app/app.jar

USER vault

# Default environment configuration
ENV SERVER_PORT=8080 \
    SPRING_DATA_MONGODB_URI="mongodb://mongo:27017/secure_config_vault?replicaSet=rs0" \
    VAULT_MASTER_KEY="0123456789abcdef0123456789abcdef" \
    JAVA_OPTS="-Xms128m -Xmx350m -Xss256k -XX:+UseG1GC -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/v3/api-docs || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
