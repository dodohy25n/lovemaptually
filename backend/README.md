# Love Map-tually Backend

Love Map-tually의 Spring Boot REST API입니다. 현재 브랜치는 백엔드 실행 기반만 제공합니다.

## 기술 기준

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper
- PostgreSQL, Spring Data JPA, Flyway
- Spring Security
- Springdoc OpenAPI
- JUnit 5, Testcontainers

## 개발 명령

```bash
cd backend
docker compose up -d postgres
./gradlew test
./gradlew bootRun
```

- API 문서: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

환경변수 이름은 `.env.example`을 참고하세요. 실제 비밀값이 들어간 `.env` 파일은 커밋하지 않습니다.
