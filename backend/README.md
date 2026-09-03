# Love Map-tually Backend

Love Map-tually의 Spring Boot REST API입니다.

## 기술 기준

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper
- PostgreSQL, Spring Data JPA, Flyway
- Spring Security
- BCrypt 비밀번호 해시, HMAC-SHA256 JWT access token
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

## 인증 API

- `POST /api/auth/signup`: 실제 PostgreSQL에 사용자를 저장하고 JWT를 발급합니다.
- `POST /api/auth/login`: BCrypt 비밀번호를 검증하고 JWT를 발급합니다.
- 이외의 API는 기본적으로 `Authorization: Bearer <token>`이 필요합니다.
- API 명세에 없는 refresh token과 logout은 현재 범위에서 추가하지 않았습니다. 운영 단계에서는 토큰 폐기·재발급 정책과 함께 설계해야 합니다.

## Mock API

그룹·초대 API는 API v3.2와 같은 Controller·DTO를 사용하는 인메모리 Mock입니다. 그룹 생성 → 초대 코드 발급 → 공개 코드 확인 → 다른 사용자의 참여 흐름을 재현할 수 있습니다. JWT 인증은 Mock이 아니며 실제 users 테이블을 사용합니다.

## 데이터베이스

Flyway의 `V1__create_initial_schema.sql`이 ERD v3.1의 15개 테이블과 제약을 생성합니다.
API v3.2의 `tagStatus`를 영속화하기 위해 승인된 `reviews.tag_status` 컬럼도 포함합니다.
