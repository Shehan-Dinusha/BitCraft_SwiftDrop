# ──────────────────────────────────────────────────────────────────────────────
# SwiftDrop API Gateway – Multi-stage Dockerfile
# Stage 1: Build (Maven + JDK 25)
# Stage 2: Runtime (JRE 25 Alpine – minimal attack surface)
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

# Copy parent POM and common-contracts first for layer caching
COPY pom.xml ./pom.xml
COPY common-contracts/pom.xml ./common-contracts/pom.xml
COPY common-contracts/src ./common-contracts/src

# Copy gateway module
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY api-gateway/src ./api-gateway/src

# Build only the gateway and its dependency
RUN cd /build && \
    mvn -B -pl api-gateway -am package -DskipTests --no-transfer-progress \
    -Dmaven.repo.local=/build/.m2

# ──────────────────────────────────────────────────────────────────────────────
# Runtime Stage
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -g 1001 -S swiftdrop && \
    adduser -u 1001 -S swiftdrop -G swiftdrop

WORKDIR /app

COPY --from=builder /build/api-gateway/target/*.jar app.jar

RUN chown -R swiftdrop:swiftdrop /app

USER swiftdrop

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
