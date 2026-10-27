FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY auth-iam-service ./auth-iam-service
RUN mvn -B --non-recursive install && \
    mvn -B -f common-contracts/pom.xml clean install -DskipTests --no-transfer-progress && \
    mvn -B -f auth-iam-service/pom.xml clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/auth-iam-service/target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
