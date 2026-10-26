FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY auth-iam-service ./auth-iam-service
RUN mvn -q -pl auth-iam-service -am -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/auth-iam-service/target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
