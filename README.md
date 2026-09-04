# 러브맵츄얼리

함께 다녀온 곳에 각자 리뷰를 남기면, 그 기록이 다음에 갈 곳을 고릅니다.

SKALA 8주차 웹 서비스 설계 Mini-project 결과물입니다.

## 왜 만들었는가

데이트 기록은 흩어져 남습니다. 사진은 갤러리에, 후기는 대화방에, 가고 싶은 곳은 저장함에 있습니다.
흩어져 남으니 "우리 둘한테 이 집은 몇 점인가"에 답할 데이터가 안 생기고,
그 기준이 없으니 다음에 갈 곳을 매번 검색부터 시작하며, 그 빈자리를 익명 다수의 평균 별점이 채웁니다.

그래서 이 서비스는 **모든 데이터를 커플이 아니라 개인 단위로** 받습니다.
두 사람의 평가를 수식으로 합치지 않고, 각자 남긴 것을 각자의 것으로 둔 채 겹치는 곳과 갈리는 곳을 보여 줍니다.

## 핵심 설계 네 가지

**하나. 만족도를 묻지 않고 답을 두 개 받습니다.**
리뷰 한 문장에서 `fact`(이 가게가 어느 쪽인가)와 `want`(이 사람은 어느 쪽을 원하는가)를 따로 뽑습니다.
"매워서 좋았다"와 "안 매워서 좋았다"가 만족도만으로는 같은 값이 되는데, 두 값을 받으면 갈라집니다.
근거 문장을 못 자르면 그 값은 비웁니다. 추측해서 채우지 않습니다.

**둘. 환각을 프롬프트가 아니라 구조로 막습니다.**
추천에서 후보를 고르는 것은 SQL과 알고리즘이고 LLM은 이유 문장만 씁니다.
월간 리포트에서도 숫자는 전부 SQL이 세고 LLM은 문장만 씁니다.
리포트가 가리키는 장소는 서버가 입력 집합과 대조해 밖이면 버립니다.
리뷰 원문에 "이전 지시를 무시하고"를 심어 실제로 호출해 봤고, 지어낸 장소는 출력에 나오지 않았습니다.

**셋. 상태 코드는 DB 제약에서 나옵니다.**
`409`가 붙는 자리는 전부 UNIQUE 제약이 있는 자리입니다.
같은 날 같은 장소 중복은 `UQ (user_id, place_id, visited_on)`, 같은 달 리포트 중복은 `UQ (group_id, report_month)`입니다.
예외 처리를 코드 곳곳에 흩지 않고 제약 하나에 모았습니다.

**넷. 정규화를 깬 세 곳은 전부 조회 경로 위입니다.**
`place_tags`, `user_tags`, `group_places`는 `review_tags`에서 언제든 다시 셀 수 있는 파생입니다.
리뷰 저장과 같은 트랜잭션에서 갱신하고, 더하지 않고 다시 셉니다.
캐시를 둔 이유가 조회를 가볍게 하려는 것이므로 갱신은 쓰기 쪽에서 끝냅니다.

## 구조

```
브라우저 (Vue 3, Vite, Pinia, Leaflet)
      |
Spring Boot 3.5 (Controller - Service - Repository)
      |            |                |
      |     AiClient        ReportWriter      PaymentClient
      |     매칭표 / 실패     OpenAI / 템플릿    Mock 승인
      |
      +-- RecommendationClient --> FastAPI 추천 엔진 (numpy)
      |                            협업 필터링, 태그 매칭, 그룹 합의
      |                            엔진이 없으면 규칙 폴백
      |
PostgreSQL 17 (17개 테이블, Flyway)
```

## 실행

```bash
scripts/demo-up.sh
```

DB 확인, 백엔드, 추천 엔진, 프론트를 차례로 띄웁니다. 로그는 `.demo-logs/`에 쌓입니다.
데모 데이터를 처음 상태로 되돌리려면 다음을 실행합니다.

```bash
scripts/seed-demo.sh
```

내리는 것은 `scripts/demo-down.sh`입니다.

DB를 처음 만들 때는 이렇게 합니다.

```bash
createuser -s lovemaptually
psql -d postgres -c "ALTER USER lovemaptually PASSWORD 'lovemaptually'"
createdb -O lovemaptually lovemaptually
```

### 데모 계정

| 이메일 | 비밀번호 | 닉네임 |
| --- | --- | --- |
| dohyeon@lovemap.dev | demo1234! | 도현 |
| yongmin@lovemap.dev | demo1234! | 용민 |

둘은 같은 커플 그룹이고 프리미엄입니다. FREE 그룹의 잠금 화면을 보려면 `couple01a@lovemap.dev`로 들어갑니다.

## 폴더

| 경로 | 내용 |
| --- | --- |
| `backend/` | Spring Boot API. 마이그레이션과 엔티티, 서비스, 계약 테스트. **실행 대상은 이쪽입니다** |
| `frontend/` | Vue 3 화면 |
| `recommender/` | FastAPI 추천 엔진과 순위 계산 |
| `scripts/` | 데모 기동과 시드, 유사도 배치 |
| `docs/` | 설계 문서 스냅샷, AI 실호출 검증 |

`back/`은 같은 API를 다른 구조로 시도한 별도 디렉터리입니다. 실행과 채점 대상은 `backend/`이고,
데모 스크립트와 테스트도 그쪽을 가리킵니다.

## R&R

| 이름 | 맡은 것 |
| --- | --- |
| 박도현 | 기획, 설계 문서, ERD, API 명세, 백엔드 도메인(리뷰, 취향, 추천, 리포트), 추천 엔진, 시드 |
| 신용민 | 백엔드 인증, 장소 API, 프론트 API 연동 |
| 송기영 | 데이터베이스 스키마, 그룹과 초대 |
| 문지영 | 백엔드 |

## 지시되지 않았지만 한 것

- **계약 테스트 전수** 명세의 엔드포인트별 상태 코드 조합을 MockMvc와 Testcontainers로 확인합니다.
- **협업 필터링을 직접 구현** 라이브러리 없이 피어슨 상관을 numpy로 계산하고, 설계 문서의 손계산 예제를 그대로 테스트로 넣었습니다.
- **AI 실패 경로 시연** `app.ai.client=failing`으로 띄우면 태그 추출이 항상 실패하는데 리뷰는 저장됩니다.
- **추천 엔진 폴백** 엔진을 내려도 태그 점수만으로 추천이 나가고 응답이 `degraded`로 그 사실을 밝힙니다.
- **LLM 실호출 검증** 월간 리포트를 실제로 호출해 결과와 토큰 실측을 `docs/ai-validation/`에 남겼고, 프롬프트 인젝션도 시험했습니다.
- **두 실행 모드** 프론트는 백엔드 없이도 도는 local 모드를 유지합니다.

## 한계

- 카카오 지도 키가 없어 장소 검색은 우리 DB를 봅니다. `MapClient` 자리는 비워 두었습니다.
- 협업 필터링은 새벽 배치가 아니라 시드 스크립트가 채웁니다. 오늘 쓴 리뷰는 태그 점수에는 즉시, 협업 필터링에는 다음 배치부터 반영됩니다.
- 결제는 Mock입니다. 실제 PG 연동은 `PaymentClient` 구현체 교체입니다.
- 리뷰 수정과 삭제는 이번 범위 밖입니다.

## Docker로 띄우기

로컬에 JDK, Node, uv, PostgreSQL을 따로 깔지 않고 Docker만으로 전체 스택을 띄울 수 있습니다.
저장소 루트에서 한 줄이면 DB, 백엔드, 추천 엔진, nginx가 서빙하는 프론트가 모두 올라옵니다.

```bash
docker compose up -d --build
```

포트는 로컬 개발 스택(`scripts/demo-up.sh`)과 겹치지 않게 밀어 두었습니다.
프론트 http://localhost:5174, 백엔드 http://localhost:8081, 추천 엔진 http://localhost:8001,
PostgreSQL 5433입니다. 그래서 두 스택을 동시에 띄워도 서로 방해하지 않습니다.

컨테이너 DB는 처음에 비어 있으므로 데모 데이터는 다음으로 넣습니다.

```bash
docker compose --profile seed run --rm seed
```

서비스별 설명, 포트를 밀어 둔 이유, `OPENAI_API_KEY` 넘기는 법, `scripts/demo-up.sh`와의 차이는
[docs/docker.md](docs/docker.md)에 정리해 두었습니다. 빠른 로컬 개발은 여전히 `scripts/demo-up.sh`가 편합니다.
