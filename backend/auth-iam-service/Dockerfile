FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /workspace
# Resolve Maven Central through Google's mirror (see docker/maven-settings.xml).
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY auth-iam-service ./auth-iam-service
RUN mvn -B --non-recursive install:install --no-transfer-progress && \
    mvn -B -f common-contracts/pom.xml clean resources:resources compiler:compile jar:jar install:install --no-transfer-progress && \
    mvn -B -f auth-iam-service/pom.xml clean resources:resources compiler:compile jar:jar spring-boot:repackage --no-transfer-progress

# Stage 2: Runtime image. Runs as an unprivileged user (least privilege).
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -g 1001 -S swiftdrop && \
    adduser -u 1001 -S swiftdrop -G swiftdrop

WORKDIR /app
COPY --from=build /workspace/auth-iam-service/target/*.jar app.jar
RUN chown -R swiftdrop:swiftdrop /app

USER swiftdrop
EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=10s --start-period=180s --retries=10 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
