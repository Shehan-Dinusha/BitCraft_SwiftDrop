FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY notification-service ./notification-service
RUN mvn -B --non-recursive install && \
    mvn -B -f common-contracts/pom.xml clean install -DskipTests --no-transfer-progress && \
    mvn -B -f notification-service/pom.xml clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/notification-service/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
