FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts ./common-contracts
COPY customer-order-service ./customer-order-service
RUN mvn -B --non-recursive install:install --no-transfer-progress && \
    mvn -B -f common-contracts/pom.xml clean resources:resources compiler:compile jar:jar install:install --no-transfer-progress && \
    mvn -B -f customer-order-service/pom.xml clean resources:resources compiler:compile jar:jar spring-boot:repackage --no-transfer-progress

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/customer-order-service/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
