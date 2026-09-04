# 1단계: Gradle 로 실행 가능한 jar 를 만듭니다.
FROM gradle:8.14.3-jdk21 AS builder
WORKDIR /build

# 의존성 목록만 먼저 복사해 두면 소스만 바뀐 재빌드에서 캐시를 재사용합니다.
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: JRE 만 있는 가벼운 런타임 이미지로 jar 만 옮깁니다.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /build/build/libs/*.jar /app/app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
