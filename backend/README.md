# 러브맵츄얼리 백엔드

Java 21, Spring Boot 3.5, JPA, Flyway, PostgreSQL 17입니다.
ERD v3.2(18개 테이블)와 REST API 명세 v3.3을 따릅니다.

## 실행

```bash
createuser -s lovemaptually
psql -d postgres -c "ALTER USER lovemaptually PASSWORD 'lovemaptually'"
createdb -O lovemaptually lovemaptually
./gradlew bootRun
```

Flyway가 기동 때 세 개의 마이그레이션을 적용합니다.
V1이 15개 테이블, V2가 태그 사전 33행, V3이 월간 리포트용 3종입니다.
`ddl-auto: validate`라 엔티티가 스키마와 어긋나면 기동이 실패합니다. 의도한 안전장치입니다.

Swagger는 `http://localhost:8080/swagger-ui.html`입니다.

## 구현 범위

전부 DB까지 실제로 연결됩니다.

- BCrypt 회원가입과 로그인, HMAC-SHA256 JWT
- 그룹 생성과 다회용 초대 코드, 참여
- 장소 검색과 상세, 우리 지도 담기와 조회, 핀 상세
- 리뷰 저장과 조회, 장소의 그룹 리뷰
- 우리 취향 조회
- 추천 요청 접수와 결과 조회
- 프리미엄 전환과 월간 리포트 생성, 목록, 조회

## 인터페이스 세 개

구현체를 갈아 끼우면 나머지 코드가 그대로인 자리가 셋입니다.

| 인터페이스 | 구현체 | 설정 |
| --- | --- | --- |
| `AiClient` | `MatchingTableAiClient` 정규식 매칭표, `FailingAiClient` 실패 시연 | `app.ai.client=matching\|failing` |
| `RecommendationClient` | `HttpRecommendationClient` FastAPI 엔진, `RuleFallbackRecommendationClient` 태그 점수만 | `app.recommender.base-url` |
| `ReportWriter` | `OpenAiReportWriter` gpt-4o-mini, `TemplateReportWriter` 개발용 | `app.report.writer=openai\|template` |
| `PaymentClient` | `MockPaymentClient` 즉시 승인 | 없음 |

`AiClient`가 실패해도 리뷰는 저장되고 응답은 `201` + `tagStatus: FAILED`입니다.
추천 엔진이 죽으면 규칙 폴백이 받고 응답에 `degraded: true`가 실립니다.
리포트는 유료 기능이라 실패를 템플릿으로 덮지 않고 `FAILED`로 남깁니다.

## 집계 캐시 세 개

`place_tags`, `user_tags`, `group_places`는 `review_tags`에서 언제든 다시 셀 수 있는 파생입니다.
리뷰를 저장하는 **같은 트랜잭션**에서 갱신하며, 더하지 않고 다시 셉니다.

장소 쪽은 사람 1명당 1표로 그 사람의 최신 리뷰만 세고, 사람 쪽은 리뷰마다 1표입니다.
한 사람이 다섯 번 가서 다섯 번 맵다고 써도 그 집이 다섯 배 매워지지 않기 때문입니다.

## 설정

`.env.example`을 복사해 `.env`를 만듭니다. `OPENAI_API_KEY`는 월간 리포트에만 씁니다.
Spring은 `.env`를 자동으로 읽지 않으므로 실행 전에 올립니다.

```bash
set -a; source .env; set +a
```

## 테스트

```bash
./gradlew test
```

Testcontainers를 쓰므로 Docker가 떠 있어야 합니다.
Docker가 없으면 통합 테스트가 조용히 건너뛰어지니 실행된 테스트 수를 확인하십시오.
