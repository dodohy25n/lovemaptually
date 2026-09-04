# 러브맵츄얼리 — REST API 명세

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v3.3 |
| 최종 수정 | 2026-09-04 |
| 대응 문서 | [PRD.md](PRD.md) v6.1 · [ERD.md](ERD.md) v2.3 · [설계결정.md](설계결정.md) |
| v2.0에서 바뀐 것 | 공통 응답 봉투 도입, 엔드포인트마다 상태 코드 명시, 추천을 2단계 비동기로, `fact`/`want` 반영 |
| v3.0에서 바뀐 것 | 추천 순위 결정을 협업 필터링으로 옮겼습니다. 질의 해석은 규칙이 되어 실패 사유 문구를 갱신했고, 추천 결과에 `cfWeight`를 추가했습니다. 엔드포인트 경로·메서드는 바뀌지 않았습니다 |
| v3.1에서 바뀐 것 | 오류 응답 모양을 팀원 노션 표기에 맞춰 `{status, code, message, field}`에서 `{status, message, error:{code, details}}`로 바꿨습니다. 공통 상태 코드에 `500`을 추가했습니다. 엔드포인트 경로·메서드·성공 응답은 바뀌지 않았습니다 |
| v3.2에서 바뀐 것 | UC-02(온보딩 취향 입력)를 없앴습니다. `GET /api/tags`·`PUT/GET /api/users/me/preferences` 세 엔드포인트가 빠지고, 온보딩은 UC-04(`POST /api/reviews`)로 통합됩니다(PRD D-20) |
| v3.3에서 바뀐 것 | **UC-08 월간 리포트(유료)를 추가했습니다.** 구독 전환 1개, 리포트 생성·목록·조회 3개 — 네 엔드포인트 전부 실동작입니다. 상태 코드에 `402 PLAN_REQUIRED`(플랜 필요 — 403과 다릅니다)·`409 REPORT_ALREADY_EXISTS`·`422 NO_VISITS_IN_MONTH`가 들어왔고, 오류 코드에 `PLAN_REQUIRED`·`REPORT_ALREADY_EXISTS`·`NO_VISITS_IN_MONTH`·`ALREADY_PREMIUM` 네 개를 더했습니다. 기존 엔드포인트는 바뀌지 않았습니다 |

PRD는 **무엇을 왜 만드는가**까지만 담고, 인터페이스 규격은 이 문서가 담습니다.

---

## 1. 공통 규칙

### 응답 봉투

**모든 성공 응답이 같은 모양입니다.** `data`에 들어가는 값만 엔드포인트마다 다릅니다.

```json
{
  "status": 200,
  "message": "조회했습니다",
  "data": { }
}
```

**오류는 상태 코드와 관계없이 하나의 모양입니다.**

```json
{
  "status": 409,
  "message": "같은 날 같은 장소에 이미 리뷰를 남겼습니다",
  "error": {
    "code": "REVIEW_DUPLICATED",
    "details": []
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | integer | Y | HTTP 상태 코드와 동일 |
| `message` | string | Y | 사용자에게 보여줘도 되는 오류 요약 |
| `error.code` | string | Y | 클라이언트가 분기할 대문자 스네이크케이스 코드 |
| `error.details` | array | Y | 필드 단위 오류 목록. `[{"field": "rating", "reason": "1~5 범위를 벗어났습니다"}]` 형태. 없으면 `[]` |

**`code`를 최상위가 아니라 `error` 아래에 묶습니다.** 오류 정보(코드·상세)와 사람이 읽는 값(`status`·`message`)을 분리해 두면, 입력값 오류처럼 필드가 여러 개 걸리는 경우에도 `details` 배열 하나로 표현되고 최상위 모양이 안 바뀝니다. 필드 하나짜리 단순 오류였던 `field: "visitedOn"`은 `details: [{"field": "visitedOn", "reason": "..."}]`로 대체됩니다.

`status`가 HTTP 상태 코드와 중복되지만 **본문만 보고도 판단할 수 있어야** 프론트엔드의 공통 처리기가 단순해집니다.

### 인증

`POST /api/auth/login`이 JWT를 주고, 이후 요청은 `Authorization: Bearer <token>`을 답니다. **서버는 토큰에서 `userId`를 꺼내므로 경로에 사용자 ID가 나타나지 않습니다.**

`{groupId}`는 경로에 두되 **그 그룹의 구성원인지 매 요청 확인하고 아니면 403**입니다. 남의 그룹 데이터 요청은 정상적으로 발생하는 일이라 처리 규격이 있어야 합니다.

### 경로 규칙

**동사를 넣지 않습니다.** 초대 수락은 `POST /api/invites/{code}/accept`가 아니라 `POST /api/groups/members`입니다. 수락으로 만들어지는 것이 **구성원**이므로 리소스도 그것입니다.

**`/couples`가 아니라 `/groups`입니다.** 데이터 모델이 `relation_groups`이고 커플은 `group_type` 값 하나이지 별도 리소스가 아닙니다. 지금 `/couples`로 두면 가족·친구 그룹을 열 때 경로를 바꿔야 하는데, **그때 이미 프론트엔드가 그 경로에 의존하고 있습니다.**

---

## 2. 엔드포인트

`실동작` 열은 3일 안에 **DB까지 실제로 연결되는가**입니다. 나머지는 Mock 응답으로 규격만 세웁니다.

### 인증

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 회원가입 | POST | `/api/auth/signup` | Body: `email`, `password`, `nickname` | `userId`, `email`, `nickname`, `accessToken`, `tokenType`, `expiresIn` | `201` · `400` 입력 오류 · `409` 이메일 중복 | **실동작** |
| 로그인 | POST | `/api/auth/login` | Body: `email`, `password` | 위와 동일 | `200` · `400` · `401` 인증 실패 | **실동작** |

**인증은 화면이 아니라 다른 판정들의 전제라서 실동작입니다.** UC-04의 `otherReviewsLocked`(내가 리뷰를 쓰기 전에는 상대 리뷰가 잠깁니다)와 곳곳의 `403`("내 그룹이 아님", "내 리뷰가 아님")이 전부 **서버가 요청자를 식별한다**는 전제 위에 서 있습니다. 인증을 Mock으로 두면 이 판정들은 규격에만 존재하고 시연에서 증명되지 않습니다. 독립 평가는 이 서비스가 일반 지도 앱과 갈라지는 축이므로(설계결정 D-11·D-31), 그 축을 보이지 못하면 설계 설명이 절반만 서게 됩니다.

**대신 경계를 좁게 잡습니다.** JWT 발급·검증과 BCrypt 해시까지이고, 리프레시 토큰·로그아웃 블랙리스트·권한 롤·비밀번호 재설정·소셜 로그인은 만들지 않습니다. `users.password_hash`가 이미 스키마에 있어 테이블 변경은 없습니다.

### UC-01 그룹 참여

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 그룹 생성 | POST | `/api/groups` | Body: `groupType`, `name?` | `groupId`, `groupType`, `name`, `createdAt`, `members[]` | `201` · `400` · `401` · `409` 이미 커플 그룹 소속 | Mock |
| 내 그룹 목록 | GET | `/api/groups/me` | — | `groups[{groupId, groupType, name, createdAt, members[{userId, nickname, role, joinedAt}]}]` | `200` · `401` | Mock |
| 초대 코드 발급 | POST | `/api/groups/{groupId}/invites` | Path: `groupId`<br>Body: `maxUses?`, `expiresInHours?` | `inviteCodeId`, `code`, `maxUses`, `useCount`, `status`, `expiresAt`, `createdAt` | `201` · `401` · `403` 내 그룹 아님 · `404` 그룹 없음 | Mock |
| 코드 확인 | GET | `/api/invites/{code}` | Path: `code` | `groupId`, `groupType`, `name`, `memberCount`, `available`, `expiresAt` | `200` · `404` 코드 없음 · `410` 만료·소진 | Mock |
| 그룹 참여 | POST | `/api/groups/members` | Body: `inviteCode` | `groupId`, `groupType`, `name`, `members[]` | `201` · `401` · `404` · `409` 이미 구성원·이미 커플 소속 · `410` 만료·소진 | Mock |

**수락 전에 코드를 확인하는 엔드포인트를 따로 둡니다.** "어느 그룹에 들어가는지 모르고 수락"을 막습니다. 이 조회는 인증 없이도 됩니다.

**만료·소진에 `410 Gone`을 씁니다.** 코드는 존재하지만 더 쓸 수 없다는 뜻이라 `404`(없음)와 `409`(충돌) 어느 쪽도 정확하지 않습니다.

### UC-02는 결번입니다

원래 온보딩 취향 입력(`GET /api/tags`·`PUT/GET /api/users/me/preferences`)이 있었지만, UC-04(리뷰 작성)로 흡수했습니다(PRD D-20). 가입 직후 "먼저 가봤던 곳 2~3곳을 리뷰해 주세요"로 안내하고 `POST /api/reviews`를 그대로 씁니다. 별도 엔드포인트가 필요 없어졌습니다.

### UC-03 장소

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 장소 검색 | GET | `/api/places` | Query: `query`, `region?`, `page?`, `size?` | `content[{placeId, provider, providerPlaceId, name, region, address, category, priceBand, latitude, longitude}]`, `page`, `size`, `totalElements`, `totalPages` | `200` · `400` 검색어 누락 · `401` · `502` 지도 API 오류 | **실동작** |
| 장소 상세 | GET | `/api/places/{placeId}` | Path: `placeId` | 위 항목 + `tags[{tag, fact, factLabel, count}]` | `200` · `401` · `404` | **실동작** |
| 우리 지도에 담기 | POST | `/api/groups/{groupId}/places` | Path: `groupId`<br>Body: `placeId` 또는 `place{provider, providerPlaceId, ...}` | `groupPlaceId`, `groupId`, `placeId`, `addedByUserId`, `label`, `createdAt` | `201` · `400` · `401` · `403` · `404` · `409` 이미 담은 장소 | Mock |

**장소를 담는 것과 리뷰를 쓰는 것은 별개 단계입니다.** 담기만 하면 `label`이 `null`이고, 리뷰가 붙으면 라벨이 생깁니다.

### UC-04 리뷰

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 리뷰 저장 | POST | `/api/reviews` | Body: `placeId`, `withGroupId?`, `visitedOn`, `rating`, `content` | `reviewId`, `placeId`, `visitedOn`, `rating`, `content`, `tagStatus`, `tags[{tag, fact, want, evidence}]`, `placeLabel{label, reviewedCount, likedCount}`, `createdAt` | `201` · `400` · `401` · `403` 내 그룹 아님 · `404` 장소 없음 · `409` 같은 날 같은 장소 중복 · `422` 별점 범위 밖 | **실동작** |
| 리뷰 조회 | GET | `/api/reviews/{reviewId}` | Path: `reviewId` | 위와 동일 | `200` · `401` · `403` 내 리뷰 아님 · `404` | **실동작** |
| 장소의 그룹 리뷰 | GET | `/api/groups/{groupId}/places/{placeId}/reviews` | Path: `groupId`, `placeId` | `placeLabel`, `reviewedCount`, `likedCount`, `myReview`, `otherReviews[]`, `otherReviewsLocked`, `lockedReason` | `200` · `401` · `403` 내 그룹 아님 · `404` | **실동작** |

**리뷰는 `placeId`만 받습니다.** 장소 등록(`POST /api/groups/{groupId}/places`)은 `placeId` 또는 `place{}`를 받지만 리뷰는 아닙니다. **검색 결과에서 바로 리뷰를 쓸 수 없고**, 화면 흐름이 `S6 장소 등록 → S1 리뷰 작성`이라 리뷰 시점에는 `placeId`가 항상 존재합니다. UC-04 Precondition("그 장소가 우리 기록에 등록되어 있다")이 이 제약입니다.

**AI가 실패해도 `201`입니다.** 리뷰는 저장됐으므로 성공이고, 실패는 `tagStatus`가 알립니다.

```
tagStatus : PENDING | COMPLETED | FAILED
```

`502`를 주면 클라이언트가 실패로 처리해 사용자가 다시 쓰게 됩니다. **PRD의 "리뷰 작성이 AI 실패로 막히지 않습니다"와 어긋납니다.**

**남의 리뷰가 안 보이는 것도 `200`입니다.** 권한이 아니라 **상태**이기 때문입니다. `otherReviewsLocked: true`와 이유를 함께 내려 화면이 안내를 띄웁니다. `403`을 주면 "내 그룹이 아님"과 구분되지 않습니다.

### UC-05 우리 취향

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 그룹 취향 조회 | GET | `/api/groups/{groupId}/preferences` | Path: `groupId` | `groupId`, `preferences[{tagId, tagName, axis, label, side, sideLabel, judgedMemberCount, members[{userId, nickname, side, sideLabel, wantHighCount, wantLowCount}]}]` | `200` · `401` · `403` · `404` | **실동작** |

```
label : ALL_SAME | ONE_SIDED | SPLIT
```

`judgedMemberCount`가 **몇 명을 분모로 판정했는지**입니다. 그룹 인원이 아니라 그 태그에서 판정이 난 사람 수라, 화면에 함께 띄웁니다.

### UC-06 추천

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 추천 요청 | POST | `/api/groups/{groupId}/recommendation-requests` | Path: `groupId`<br>Body: `query` | `requestId`, `status`, `createdAt` | `202` 접수됨 · `400` · `401` · `403` · `404` · `422` 지역 해석 실패 | **실동작** |
| 추천 결과 조회 | GET | `/api/recommendation-requests/{requestId}` | Path: `requestId` | `requestId`, `query`, `intent{region, count, budget}`, `candidateCount`, `cfWeight`, `status`, `recommendations[{recommendationId, placeId, name, category, priceBand, latitude, longitude, matchedTags[], basis, reason, displayOrder}]` | `200` · `401` · `403` · `404` | **실동작** |

**`202 Accepted`입니다.** 요청은 받았지만 결과가 아직 없다는 뜻입니다. `201`은 자원이 완성됐을 때 쓰는 코드라 여기엔 맞지 않습니다.

**이 요청은 세 단계를 거칩니다.** 지역·개수·예산은 규칙으로 즉시 뽑히지만, 그다음이 시간이 걸립니다 — 협업 필터링과 태그 매칭을 결합해 후보 순위를 매기고(알고리즘), 상위 후보에 이유 문장을 붙입니다(AI-2, LLM). 그래서 요청과 결과 조회를 나눴습니다. 화면은 `status`가 `PENDING`이면 스켈레톤을 그리고, `COMPLETED`가 될 때까지 조회합니다.

`candidateCount`는 화면에 쓰지 않지만 응답에 둡니다. **시연에서 알고리즘이 몇 곳을 넘겼는지 보여 줄 수 있어** 파이프라인 구조를 설명하기 쉬워집니다.

`cfWeight`(0~1)는 **이번 추천에 협업 필터링이 얼마나 반영됐는지**입니다. 그룹 구성원의 리뷰 수가 적을수록 0에 가깝고, 20건이 넘으면 1에 가깝습니다. 화면은 이 값으로 "취향 태그로 골랐어요"와 "비슷한 취향의 커플들이 다녀왔어요" 문구를 가릅니다.

**`422`는 지역을 못 읽었을 때입니다.** 형식은 맞지만 처리할 수 없다는 뜻이라 `400`과 구분합니다.

### UC-07 우리 지도

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 지도 조회 | GET | `/api/groups/{groupId}/places` | Path: `groupId`<br>Query: `label?` | `markers[{groupPlaceId, placeId, name, address, category, latitude, longitude, label, reviewedCount, likedCount}]` | `200` · `400` · `401` · `403` · `404` | Mock |
| 핀 상세 | GET | `/api/groups/{groupId}/places/{placeId}` | Path: `groupId`, `placeId` | `groupPlaceId`, `place{}`, `label`, `reviewedCount`, `likedCount`, `labelUpdatedAt`, `visits[{visitedOn, reviewCount}]`, `reviews[]` | `200` · `401` · `403` · `404` | Mock |

```
label : ALL_LIKED | MIXED | ON_HOLD | null
```

`null`은 **장소만 담고 아직 리뷰가 없는 상태**입니다.

`labelUpdatedAt`과 각 리뷰의 `visitedOn`을 함께 내립니다. **재방문으로 라벨이 바뀌었을 때 언제 기준인지 화면이 밝힐 수 있어야** 합니다.

### UC-08 월간 리포트

| 기능 | Method | 경로 | 요청 | 응답 `data` | 상태 코드 | 실동작 |
| --- | --- | --- | --- | --- | --- | --- |
| 프리미엄 전환 | POST | `/api/groups/{groupId}/subscriptions` | Path: `groupId`<br>Body: `plan` (`"PREMIUM"`) | `subscriptionId`, `groupId`, `plan`, `startedAt`, `paymentRef` | `201` · `401` · `403` 내 그룹 아님 · `404` 그룹 없음 · `409` 이미 PREMIUM | **실동작** |
| 리포트 생성 요청 | POST | `/api/groups/{groupId}/reports` | Path: `groupId`<br>Body: `month` (`"YYYY-MM"`) | `reportId`, `groupId`, `reportMonth`, `status`, `createdAt` | `202` 접수됨 · `400` 형식 오류 · `401` · **`402` 플랜 필요** · `403` 내 그룹 아님 · `404` · `409` 그 달 리포트 이미 있음 · `422` 그 달 리뷰 0건 | **실동작** |
| 리포트 목록 | GET | `/api/groups/{groupId}/reports` | Path: `groupId` | `plan`, `reports[{reportId, reportMonth, status, title, summary, createdAt, completedAt}]` | `200` · `401` · `403` · `404` | **실동작** |
| 리포트 조회 | GET | `/api/reports/{reportId}` | Path: `reportId` | `reportId`, `groupId`, `reportMonth`, `status`, `requestedByUserId`, `createdAt`, `completedAt`, `content{title, summary, highlights[], tasteShift[], splitTags[], nextMonth[], closingLine, meta{}}` | `200` · `401` · `403` 내 그룹 아님 · `404` | **실동작** |

**리포트도 구독도 단위는 그룹입니다.** 경로가 `/api/groups/{groupId}/...`이고 `/api/users/me/...`가 아닌 이유입니다. 한 명이 전환하면 구성원 모두가 PREMIUM이고, 누가 생성했든 리포트는 구성원 모두에게 같은 것이 보입니다. `requestedByUserId`는 기록일 뿐 권한과 무관합니다.

**전환은 Mock 결제입니다.** `PaymentClient` 인터페이스 뒤 Mock이 즉시 승인하고 `paymentRef`에 Mock 승인 번호를 줍니다. 실제 PG 연동은 비목표(PRD §8)라 카드 정보를 받는 필드가 없습니다. 이미 PREMIUM인 그룹에 다시 요청하면 `409 ALREADY_PREMIUM`입니다 — 중복 결제를 막는 것이 이 코드의 역할입니다.

**`202 Accepted`입니다.** 추천(UC-06)과 같은 2단계입니다. 집계는 SQL로 즉시 끝나지만 LLM이 문장을 쓰는 데 수 초가 걸리므로, `PENDING` 행을 먼저 만들어 돌려주고 화면은 `GET /api/reports/{reportId}`로 `COMPLETED`가 될 때까지 조회합니다. `status`가 `PENDING`이면 `content: null`이고, `FAILED`면 `content.meta.error`만 있으며 화면은 재시도 버튼을 띄웁니다.

**`402`는 FREE 그룹이 생성을 요청했을 때입니다.** `403`은 "내 그룹이 아니다"라 화면이 "결제하면 열린다"를 구분할 수 없습니다. `402`는 정확히 그 상태를 말합니다. 대가는 브라우저·프록시 관행상 드물게 쓰이는 코드라는 것입니다(설계결정 D-38).

**`422`는 그 달에 이 그룹과 함께 간 리뷰가 0건일 때입니다.** 형식은 맞지만 쓸 재료가 없어 처리할 수 없다는 뜻이라 `400`과 구분하며, 추천의 지역 해석 실패와 같은 자리입니다. 월 경계는 그룹 시간대 Asia/Seoul 기준 1일 00:00부터 말일 23:59:59까지이고, `visitedOn`으로 판정합니다.

**`409`는 같은 달을 두 번 요청했을 때입니다.** `UQ (group_id, report_month)`에서 나오고, `error.details`에 기존 `reportId`를 실어 보내 화면이 새로 만드는 대신 그 리포트를 엽니다. `FAILED`인 리포트를 재시도할 때는 이 엔드포인트를 다시 부르지 않고 같은 `month`로 요청하면 서버가 **기존 행을 `PENDING`으로 되돌려 갱신**하고 `202`를 줍니다 — `FAILED` 행은 완성본이 아니므로 `409`가 아닙니다.

**목록과 조회에는 플랜 조건이 없습니다.** 구독을 취소해도 이미 만든 리포트는 열립니다. 플랜은 새로 만들 때만 검사합니다.

---

## 3. 주요 요청과 응답

### 리뷰 저장

```json
POST /api/reviews
{
  "placeId": 412,
  "withGroupId": 7,
  "visitedOn": "2026-09-01",
  "rating": 4,
  "content": "조용해서 얘기하기 좋았는데 웨이팅이 40분이었어요"
}
```

`withGroupId`는 **누구와 갔는가**입니다. 그룹 페이지 노출과 관계별 통계가 모두 이 값을 씁니다. 생략하면 내 페이지에만 보입니다.

```json
201 Created
{
  "status": 201,
  "message": "리뷰를 저장했습니다",
  "data": {
    "reviewId": 981,
    "placeId": 412,
    "visitedOn": "2026-09-01",
    "rating": 4,
    "tagStatus": "COMPLETED",
    "tags": [
      { "tag": "조용함", "fact": "조용함",       "want": "조용함",       "evidence": "조용해서" },
      { "tag": "대화",   "fact": "대화하기 좋음", "want": "대화하기 좋음", "evidence": "얘기하기 좋았는데" },
      { "tag": "웨이팅", "fact": "김",           "want": "짧음",         "evidence": "웨이팅이 40분이었어요" }
    ],
    "placeLabel": { "label": "ALL_LIKED", "reviewedCount": 2, "likedCount": 2 }
  }
}
```

`placeLabel`에 카운트가 함께 나가는 이유는 **몇 명 기준의 라벨인지**를 화면이 밝혀야 하기 때문입니다. "2명이 평가한 `ALL_LIKED`"와 "5명이 평가한 `ALL_LIKED`"는 무게가 다릅니다.

### 장소의 그룹 리뷰 — 아직 나만 썼을 때

```json
GET /api/groups/7/places/412/reviews

200 OK
{
  "status": 200,
  "message": "조회했습니다",
  "data": {
    "placeLabel": "MIXED",
    "reviewedCount": 1,
    "likedCount": 1,
    "myReview": { "reviewId": 981, "rating": 4, "content": "...", "visitedOn": "2026-09-01" },
    "otherReviews": [],
    "otherReviewsLocked": true,
    "lockedReason": "다른 구성원이 리뷰를 남기면 함께 공개됩니다"
  }
}
```

### 추천 — 두 단계

```json
POST /api/groups/7/recommendation-requests
{ "query": "오늘 인사동 갈 건데 3곳 정도 추천해줘" }

202 Accepted
{ "status": 202, "message": "추천을 준비하고 있습니다",
  "data": { "requestId": 3301, "status": "PENDING", "createdAt": "2026-09-03T14:22:10" } }
```

```json
GET /api/recommendation-requests/3301

200 OK
{
  "status": 200,
  "message": "조회했습니다",
  "data": {
    "requestId": 3301,
    "query": "오늘 인사동 갈 건데 3곳 정도 추천해줘",
    "intent": { "region": "인사동", "count": 3, "budget": null },
    "candidateCount": 27,
    "cfWeight": 0.62,
    "status": "COMPLETED",
    "recommendations": [
      {
        "recommendationId": 8801,
        "placeId": 412,
        "name": "○○찻집",
        "category": "카페",
        "priceBand": 2,
        "matchedTags": ["조용함", "대화"],
        "basis": "OWN",
        "reason": "두 분 다 조용한 곳을 좋아하셔서 골랐습니다. 지난번 웨이팅이 길었으니 평일 낮이 낫습니다.",
        "displayOrder": 1
      }
    ]
  }
}
```

**`intent`는 규칙이 채웁니다. `recommendations`의 순서와 `matchedTags`는 협업 필터링과 태그 매칭을 가중 결합한 알고리즘이 정합니다. `reason`만 LLM(AI-2)이 씁니다.** 셋 중 무엇도 서로의 실패에 영향을 주지 않습니다 — 알고리즘이 고른 장소는 확정돼 있으므로 이유 생성이 실패해도 템플릿 문장으로 대체될 뿐 추천 자체는 유지됩니다.

**`basis`는 LLM이 판단하지 않습니다.** 어느 갈래에서 나왔는지 알고리즘이 이미 알고 있으므로 그대로 실어 보냈다 응답에 되받습니다. LLM에게 맡기면 틀릴 수 있고, 틀리면 **"우리가 갔던 곳"과 "남이 간 곳"이 섞여** 서비스 신뢰가 깨집니다.

### 프리미엄 전환 — Mock 결제

```json
POST /api/groups/7/subscriptions
{ "plan": "PREMIUM" }

201 Created
{ "status": 201, "message": "프리미엄으로 전환했습니다",
  "data": { "subscriptionId": 41, "groupId": 7, "plan": "PREMIUM",
            "startedAt": "2026-09-04T09:12:30", "paymentRef": "MOCK-20260904-000041" } }
```

`paymentRef`의 `MOCK-` 접두어가 Mock 결제라는 표시입니다. 실제 PG로 바꾸면 이 자리에 PG 거래 ID가 들어오고 응답 모양은 바뀌지 않습니다.

### 월간 리포트 — 두 단계

```json
POST /api/groups/7/reports
{ "month": "2026-08" }

202 Accepted
{ "status": 202, "message": "리포트를 쓰고 있습니다",
  "data": { "reportId": 512, "groupId": 7, "reportMonth": "2026-08-01",
            "status": "PENDING", "createdAt": "2026-09-04T09:13:02" } }
```

FREE 그룹이면 `202` 대신 이렇게 옵니다.

```json
402 Payment Required
{
  "status": 402,
  "message": "월간 리포트는 프리미엄 그룹에서 만들 수 있습니다",
  "error": { "code": "PLAN_REQUIRED", "details": [ { "field": "plan", "reason": "현재 FREE, 필요 PREMIUM" } ] }
}
```

같은 달을 다시 요청하면 기존 `reportId`를 함께 돌려줍니다.

```json
409 Conflict
{
  "status": 409,
  "message": "2026-08 리포트가 이미 있습니다",
  "error": { "code": "REPORT_ALREADY_EXISTS", "details": [ { "field": "month", "reason": "reportId=512" } ] }
}
```

```json
GET /api/reports/512

200 OK
{
  "status": 200,
  "message": "조회했습니다",
  "data": {
    "reportId": 512,
    "groupId": 7,
    "reportMonth": "2026-08-01",
    "status": "COMPLETED",
    "requestedByUserId": 12,
    "createdAt": "2026-09-04T09:13:02",
    "completedAt": "2026-09-04T09:13:09",
    "content": {
      "title": "조용한 곳을 찾아다닌 8월",
      "summary": "8월에는 여섯 곳을 함께 다녀왔고 그중 네 곳이 둘 다 좋았던 곳이었습니다. 지난달보다 두 곳 더 다녔고, 카페가 절반이었습니다.",
      "highlights": [
        { "placeId": 412, "name": "○○찻집", "why": "두 분 모두 5점을 준 유일한 곳이었고, 조용하고 대화하기 좋았다는 평이 양쪽에서 나왔습니다." },
        { "placeId": 418, "name": "△△식당", "why": "8월에 두 번 다시 찾은 곳입니다. 한 번 간 곳을 또 간 것은 이달에 여기뿐이었습니다." }
      ],
      "tasteShift": [
        { "tag": "야외석", "direction": "HIGH", "evidence": "7월에는 언급이 없었는데 8월 리뷰 세 건에서 야외석을 원한다고 적었습니다." }
      ],
      "splitTags": [
        { "tag": "맵기", "memberA": "매움", "memberB": "순함" }
      ],
      "nextMonth": [
        { "placeId": 433, "name": "□□카페", "reason": "조용함과 대화 태그가 두 분 취향과 맞고, 아직 안 가 본 곳입니다." }
      ],
      "closingLine": "9월에는 갈린 맵기를 피해서 둘 다 편한 곳으로 시작해 보시길 바랍니다.",
      "meta": { "model": "gpt-4o-mini", "promptTokens": 1840, "completionTokens": 620, "discarded": 1 }
    }
  }
}
```

**`content`의 숫자와 집계는 전부 서버가 SQL로 센 것이고, LLM은 문장만 썼습니다.** `highlights`·`nextMonth`의 `placeId`는 서버가 입력으로 준 집합 안에서만 유효하며, 밖을 가리키는 항목은 저장 전에 버리고 `meta.discarded`에 개수를 남깁니다. 위 예시의 `discarded: 1`은 LLM이 입력에 없는 장소를 하나 지어냈고 서버가 그것을 걸러 냈다는 뜻입니다. 추천에서 후보 밖 장소를 막은 것과 같은 원칙이 두 번째로 적용되는 자리입니다.

`splitTags`의 `memberA`·`memberB`는 **익명화된 구성원**입니다. LLM에는 이메일·닉네임·`userId`를 보내지 않고 A·B로만 보내며, 화면이 그룹 구성원 순서로 실제 닉네임을 붙입니다. `meta`의 토큰 수는 응답 usage 실측값이고 원가 계산의 근거입니다.

실패하면 `status: FAILED`이고 `content`에는 `meta.error`만 있습니다. 화면은 재시도 버튼을 띄우고, 재시도는 같은 `month`로 `POST /api/groups/{groupId}/reports`를 다시 부릅니다.

---

## 4. 상태 코드 — DB 제약에서 나옵니다

**409가 붙는 자리는 전부 UNIQUE 제약이 있는 자리입니다.** 예외 처리를 코드 곳곳에 흩지 않고 제약 하나에 모았습니다.

| 코드 | 사용처 | 근거 |
| --- | --- | --- |
| `200` | 조회 성공 | |
| `201` | 생성 성공 — 리뷰·그룹·구성원·초대 코드·구독 | |
| `202` | 추천 요청 접수 · 리포트 생성 접수 | 결과가 아직 없습니다 |
| `400` | 본문 형식 오류, 필수 값 누락 | |
| `401` | 토큰이 없거나 만료됨 | |
| `402` | FREE 그룹이 월간 리포트 생성을 요청 | `relation_groups.plan = 'FREE'`. **`403`과 다릅니다** — 403은 "내 것이 아니다"이고 402는 "내 것인데 결제하면 열린다"입니다. 화면이 두 안내를 갈라야 하므로 코드를 나눴습니다(D-38) |
| `403` | 내 그룹이 아닌 `{groupId}`에 접근 | `group_members`에 행이 없습니다 |
| `404` | 없는 장소·리뷰·그룹·초대 코드·리포트 | |
| `409` | 같은 날 같은 장소에 리뷰 중복 | `UQ (user_id, place_id, visited_on)` |
| `409` | 이미 그 그룹의 구성원 | `UQ (group_id, user_id)` |
| `409` | 이미 담은 장소 | `UQ (group_id, place_id)` |
| `409` | 이미 커플 그룹에 속한 사용자 | 애플리케이션이 확인합니다 (2인 제한은 DB에 걸지 않았습니다) |
| `409` | 같은 달 리포트가 이미 있음 (`REPORT_ALREADY_EXISTS`) | `UQ (group_id, report_month)`. `details`에 기존 `reportId`를 실어 화면이 그것을 엽니다 |
| `409` | 이미 PREMIUM인 그룹의 전환 요청 (`ALREADY_PREMIUM`) | `relation_groups.plan = 'PREMIUM'`. 중복 결제를 막습니다 |
| `410` | 초대 코드 만료·소진 | 존재하지만 더 쓸 수 없습니다 |
| `422` | 지역을 읽지 못해 추천 불가 | 질의 해석 규칙이 `region: null` |
| `422` | 별점이 1~5 밖 | `CHECK (rating BETWEEN 1 AND 5)` |
| `422` | 그 달에 함께 간 리뷰가 0건이라 리포트 불가 (`NO_VISITS_IN_MONTH`) | `reviews`에 `with_group_id = ? AND visited_on BETWEEN 월초 AND 월말` 행이 없습니다. 형식은 맞지만 쓸 재료가 없습니다 |
| `500` | 처리하지 못한 서버 내부 오류 | 위 어느 경우에도 안 걸리는 예외의 안전망입니다 |
| `502` | 지도 API 오류 | 외부 게이트웨이입니다 |

**`204`와 `429`는 쓰지 않습니다.** `204`(삭제 성공)는 이번 범위에 DELETE 엔드포인트가 하나도 없어서이고, `429`(요청 한도 초과)는 rate limit 자체가 3일 범위 밖이라 뺐습니다. 둘 다 나중에 R1에서 필요해지면 이 표에 추가하면 됩니다.

**`502`를 AI 실패에 쓰지 않습니다.** AI-1이 실패해도 리뷰는 저장되므로 `201` + `tagStatus: FAILED`이고, AI-2가 실패하면 `status: FAILED`로 조회됩니다. AI-3(리포트)도 같아서 생성 요청은 `202`로 접수되고 실패는 조회 응답의 `status: FAILED`가 알립니다. **부분 성공을 오류로 처리하면 사용자가 한 일이 사라집니다.**

**`402`를 쓰는 곳은 리포트 생성 하나뿐입니다.** 이 서비스에서 플랜에 걸리는 기능이 그것 하나이기 때문입니다. `402 Payment Required`는 RFC에 "장래 사용을 위해 예약"으로 남아 있어 브라우저·프록시가 특별히 다루지 않는 비표준에 가까운 코드입니다. 그 대가를 알고도 고른 이유는 `403`으로 합치면 화면이 "결제하면 열린다"와 "내 그룹이 아니다"를 `error.code`로만 갈라야 해서, 상태 코드만 보는 공통 처리기가 둘을 같은 오류로 다루기 때문입니다.

### 오류 코드 목록

| `code` | 상황 |
| --- | --- |
| `EMAIL_DUPLICATED` | 가입 시 이메일 중복 |
| `INVALID_CREDENTIALS` | 로그인 실패 |
| `NOT_GROUP_MEMBER` | 내 그룹이 아닌 접근 |
| `ALREADY_IN_COUPLE` | 이미 커플 그룹 소속 |
| `INVITE_EXPIRED` | 초대 코드 만료·소진 |
| `REVIEW_DUPLICATED` | 같은 날 같은 장소에 리뷰 중복 |
| `PLACE_ALREADY_ADDED` | 이미 담은 장소 |
| `RATING_OUT_OF_RANGE` | 별점 범위 밖 |
| `REGION_NOT_FOUND` | 지역을 읽지 못함 |
| `MAP_PROVIDER_ERROR` | 지도 API 오류 |
| `PLAN_REQUIRED` | FREE 그룹이 월간 리포트 생성을 요청 (`402`) |
| `REPORT_ALREADY_EXISTS` | 같은 달 리포트가 이미 있음 (`409`). `details`에 기존 `reportId` |
| `NO_VISITS_IN_MONTH` | 그 달에 함께 간 리뷰가 0건 (`422`) |
| `ALREADY_PREMIUM` | 이미 PREMIUM인 그룹의 전환 요청 (`409`) |
