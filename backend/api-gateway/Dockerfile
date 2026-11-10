# FROM eclipse-temurin:25-jre-alpine
# RUN addgroup -g 1001 -S swiftdrop && \
#     adduser -u 1001 -S swiftdrop -G swiftdrop
# WORKDIR /app
# COPY api-gateway/target/*.jar app.jar
# RUN chown -R swiftdrop:swiftdrop /app
# USER swiftdrop
# EXPOSE 8080
# HEALTHCHECK --interval=15s --timeout=10s --start-period=180s --retries=10 \
#     CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1
# ENTRYPOINT ["java", \
#     "-XX:+UseZGC", \
#     "-XX:MaxRAMPercentage=75.0", \
#     "-Djava.security.egd=file:/dev/./urandom", \
#     "-jar", "app.jar"]


# Stage 1: Build JAR inside Docker
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common-contracts ./common-contracts
COPY api-gateway ./api-gateway

RUN mvn -B --non-recursive install:install --no-transfer-progress && \
    mvn -B -f common-contracts/pom.xml clean resources:resources compiler:compile jar:jar install:install -DskipTests --no-transfer-progress && \
    mvn -B -f api-gateway/pom.xml clean package -DskipTests --no-transfer-progress

# Stage 2: Runtime image
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -g 1001 -S swiftdrop && \
    adduser -u 1001 -S swiftdrop -G swiftdrop

WORKDIR /app
COPY --from=build /workspace/api-gateway/target/*.jar app.jar
RUN chown -R swiftdrop:swiftdrop /app

USER swiftdrop
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=10s --start-period=180s --retries=10 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]