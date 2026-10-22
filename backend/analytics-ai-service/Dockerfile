FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY analytics-ai-service ./analytics-ai-service
RUN mvn -q -pl analytics-ai-service -am -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/analytics-ai-service/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
