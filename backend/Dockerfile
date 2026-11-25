# 운영 백엔드 이미지. CI(.github/workflows/images.yml)에서 빌드한다. 서버(t3.small 2GB)에서는 빌드하지 않는다.
# 빌드: docker build -t rental-backend backend
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# 의존성 캐시: 빌드 스크립트만 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY config config
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
# 테스트는 CI(ci.yml)가 PR에서 돌린다. 이미지 빌드에서는 bootJar만
RUN ./gradlew --no-daemon bootJar \
	&& cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar$')" app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
# JAVA_MAX_HEAP: 2GB 서버에 앱 하나 + Redis/RabbitMQ·Nginx를 같이 두는 기준. compose의 mem_limit보다 작게 둔다
ENV TZ=Asia/Seoul \
	SPRING_PROFILES_ACTIVE=prod \
	JAVA_MAX_HEAP=768m \
	JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Xmx${JAVA_MAX_HEAP} -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError ${JAVA_OPTS} -jar /app/app.jar"]
