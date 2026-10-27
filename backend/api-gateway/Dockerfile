FROM eclipse-temurin:25-jre-alpine
RUN addgroup -g 1001 -S swiftdrop && \
    adduser -u 1001 -S swiftdrop -G swiftdrop
WORKDIR /app
COPY api-gateway/target/*.jar app.jar
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
