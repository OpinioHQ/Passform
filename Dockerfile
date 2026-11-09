# 배포용 백엔드 이미지. 로컬 개발에는 쓰지 않는다(deploy/README.md).
# 테스트는 CI가 돌리므로 여기서는 실행 가능한 jar만 만든다.
FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version > /dev/null
# processResources가 src/ 아래 스키마·프롬프트와 config/rag.yaml을 클래스패스에 넣는다.
COPY src src
COPY config config
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-noble
RUN groupadd --system passform && useradd --system --gid passform --no-create-home passform
WORKDIR /app
COPY --from=build /src/build/libs/passform-*.jar app.jar
USER passform
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
