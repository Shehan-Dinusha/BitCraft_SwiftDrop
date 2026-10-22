FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY pricing-zone-service ./pricing-zone-service
RUN mvn -q -pl pricing-zone-service -am -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/pricing-zone-service/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
