# Builds the demo from this checkout (build context: repository root).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl atlas-client-demo -am package -Dmaven.test.skip=true \
    && cp atlas-client-demo/target/atlas-client-demo.jar /demo.jar

FROM eclipse-temurin:21-jre
COPY --from=build /demo.jar /app/demo.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/demo.jar"]
