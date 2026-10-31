# 현재 체크아웃한 소스로 데모를 빌드합니다(빌드 컨텍스트: 저장소 루트).
# 1단계: Maven으로 데모 모듈과 그 모듈이 의존하는 모듈만(-pl ... -am) 테스트 없이 빌드합니다.
#        ~/.m2를 빌드 캐시로 마운트해 다시 빌드할 때 의존성을 내려받지 않게 합니다.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl atlas-client-demo -am package -Dmaven.test.skip=true \
    && cp atlas-client-demo/target/atlas-client-demo.jar /demo.jar

# 2단계: 빌드 도구 없이 JRE만 있는 이미지에 실행 가능한 jar만 복사해 이미지를 작게 만듭니다.
FROM eclipse-temurin:21-jre
COPY --from=build /demo.jar /app/demo.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/demo.jar"]
