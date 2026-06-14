FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY jgen ./jgen
WORKDIR /workspace/jgen
RUN mvn -pl jgen-portal -am package -DskipTests

FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app
COPY --from=build /workspace/jgen/jgen-portal/target/jgen-portal-*.jar /app/jgen-portal.jar

EXPOSE 8080
VOLUME ["/app/.jgen-portal", "/root/.m2"]

ENTRYPOINT ["java", "-jar", "/app/jgen-portal.jar"]
