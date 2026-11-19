FROM maven:3-eclipse-temurin-27 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY delivery-execution-service ./delivery-execution-service
RUN mvn -B --non-recursive install:install --no-transfer-progress && \
    mvn -B -f common-contracts/pom.xml clean resources:resources compiler:compile jar:jar install:install --no-transfer-progress && \
    mvn -B -f delivery-execution-service/pom.xml clean resources:resources compiler:compile jar:jar spring-boot:repackage --no-transfer-progress

# Stage 2: Runtime image. Runs as an unprivileged user (least privilege).
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -g 1001 -S swiftdrop && \
    adduser -u 1001 -S swiftdrop -G swiftdrop

WORKDIR /app
COPY --from=build /workspace/delivery-execution-service/target/*.jar app.jar
# Local proof-of-delivery photo directory (POD_STORAGE_DIR). Created here so a
# Docker named volume mounted on it inherits the non-root owner.
RUN mkdir -p /var/lib/swiftdrop/pod && \
    chown -R swiftdrop:swiftdrop /app /var/lib/swiftdrop

USER swiftdrop
EXPOSE 8085

HEALTHCHECK --interval=15s --timeout=10s --start-period=180s --retries=10 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health/readiness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
