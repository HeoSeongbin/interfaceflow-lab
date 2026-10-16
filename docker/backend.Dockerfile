FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY backend backend
ARG MODULE
RUN mvn -B -s .mvn/settings.xml -pl backend/${MODULE} -am package -DskipTests
RUN cp backend/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar /app.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001
ENTRYPOINT ["java","-jar","app.jar"]
