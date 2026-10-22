FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN ./mvnw -q -DskipTests package || mvn -q -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
