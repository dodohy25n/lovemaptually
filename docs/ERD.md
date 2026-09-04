# 러브맵츄얼리 — 데이터 모델 (ERD 설명서)

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v2.3 |
| 대응 PRD | [PRD.md](PRD.md) v6.1 · 데이터 원칙은 이 문서가 담습니다 |
| 함께 보는 것 | [erd.dbml](erd.dbml) 컬럼·제약 · [설계결정.md](설계결정.md) 결정 근거 · [ERD-검토.md](ERD-검토.md) 초안 검토 |
| v2.0에서 바뀐 것 | 추천 순위 결정이 협업 필터링으로 옮겨가며 `recommendation_requests`·`recommendations` 두 테이블이 추가됐습니다(13개→14개). `recommendation_requests.cf_weight`를 새로 설명하고, 질의 해석을 가리키던 옛 "AI-2a" 표기를 규칙 기반이라는 현재 표현으로 바꿨습니다 |
| v2.1에서 바뀐 것 | 온보딩 취향 입력(UC-02)이 없어지고 리뷰 작성(UC-04)으로 통합되면서(D-20), `user_tags`에 있던 "온보딩 설문은 2표로 심는다"는 특수 규칙을 없앴습니다. 테이블 수·컬럼은 바뀌지 않았습니다 |
| v2.2에서 바뀐 것 | ① `place_similarity`를 추가했습니다(14개→15개). API·추천설계문서가 이 테이블을 전제하는데 스키마에 정의가 없어 비어 있던 자리입니다. ② `reviews.tag_status`를 추가했습니다. API 응답의 `tagStatus`를 저장할 자리가 없었습니다 |
| v2.3에서 바뀐 것 | **월간 리포트(UC-08, 유료)를 받았습니다(15개→18개).** ① `relation_groups.plan`(FREE/PREMIUM) ② `monthly_reports` — 그룹의 한 달 리포트 한 편, `UQ (group_id, report_month)` ③ `subscriptions` — Mock 결제 이력. 상태 컬럼을 둔 것은 D-14의 예외이고, 그 이유와 리포트를 저장하는 이유를 새 절에 적었습니다 |

PRD는 **무엇을 왜 만드는가**까지만 담고, 테이블과 제약은 이 문서가 담습니다.

---

테이블은 **18개**입니다. 결정의 근거는 [설계결정.md](설계결정.md)에, 컬럼과 제약은 [erd.dbml](erd.dbml)에 있습니다.

| 테이블 | 키 | 설명 |
| --- | --- | --- |
| users | PK user_id · UQ email | 개인 계정 |
| relation_groups | PK group_id | `group_type`으로 COUPLE / FAMILY / FRIENDS. `plan`으로 FREE / PREMIUM — 월간 리포트 생성 권한은 그룹에 걸립니다 |
| group_members | PK · UQ (group_id, user_id) | 구성원. 탈퇴는 `left_at`으로 표시합니다 |
| invite_codes | PK · UQ code | 다회용. `max_uses` · `use_count` |
| places | PK place_id · UQ (provider, provider_place_id) | 외부 ID를 PK로 쓰지 않습니다 |
| **reviews** | PK · **UQ (user_id, place_id, visited_on)** | **개인이 소유합니다.** 재방문은 날짜로 갈립니다. `with_group_id`로 동반 관계를, `tag_status`로 태그 추출 상태를 남깁니다 |
| **review_tags** | PK · UQ (review_id, tag_id) | **원본입니다.** `fact_value`와 `want_value`를 함께 가집니다 |
| tags | PK tag_id · UQ name | 고정 사전 33개. `axis` · `high_label` · `low_label` |
| place_tags | PK · UQ (place_id, tag_id) | 집계 캐시 — **가게가 어떤가.** `fact_high_count`, `fact_low_count` |
| user_tags | PK · UQ (user_id, tag_id) | 집계 캐시 — **뭘 원하나.** `want_high_count`, `want_low_count` |
| group_places | PK · UQ (group_id, place_id) | 집계 캐시 — 장소 `label`, `reviewed_count`, `liked_count`. 사람 1명당 1표 |
| unmatched_tag_logs | PK log_id | 사전 밖 표현. 미매칭률의 근거입니다 |
| **place_similarity** | PK · UQ (place_id, similar_place_id) | 장소 간 피어슨 유사도. 새벽 배치가 미리 계산하고 추천 때는 조회만 합니다 |
| **recommendation_requests** | PK request_id | 추천 요청 한 건. 질의·해석 결과·`cf_weight`를 남깁니다 |
| **recommendations** | PK · UQ (request_id, place_id) | 요청 하나의 결과 여러 줄. 그 시점 판단의 스냅샷입니다 |
| **monthly_reports** | PK report_id · **UQ (group_id, report_month)** | 그룹의 한 달 리포트 한 편(유료). `status` PENDING / COMPLETED / FAILED, `content` JSONB, `model`·`prompt_tokens`·`completion_tokens`는 원가 실측치입니다 |
| **subscriptions** | PK subscription_id | Mock 결제 이력. `started_at`·`ended_at`·`payment_ref`. 언제부터 유료였는지를 답합니다 |

### MVP는 커플이고, 스키마는 N명으로 엽니다

`relation_groups.group_type`에 `COUPLE / FAMILY / FRIENDS`를 두되 **인원 제한을 DB에 걸지 않습니다.** 시연과 시드 데이터는 커플 2인으로만 합니다.

커플 전용으로 2인을 DB 제약으로 못 박으면 가족·친구로 넓힐 때 스키마를 다시 설계해야 합니다. 반대로 처음부터 N명 UI까지 만들면 3일 안에 끝나지 않습니다. **설계는 미리, 구현은 나중에** 가릅니다. §5의 라벨을 카운트로 판정하게 만든 것도 같은 이유입니다 — 이름에 "둘"이 들어가는 순간 스키마가 2인에 묶입니다.

**대가는 2인을 DB가 보장해 주던 안전장치입니다.** `COUPLE` 타입의 인원 2명 제한과 사용자당 커플 그룹 1개 제한은 애플리케이션이 확인하고 409로 돌려보냅니다.

### 방문을 테이블로 두지 않습니다

두 사람의 평가를 겹치려면 **무엇을 같은 방문으로 볼 것인가**를 정하는 키가 필요합니다. 그 키를 테이블로 둘 수도 있고 제약으로 둘 수도 있는데, **제약으로 뒀습니다.**

```
reviews  UNIQUE (user_id, place_id, visited_on)
```

`visits` 테이블을 두면 조인이 한 단계 늘어나는 대가로 얻는 것이 "같은 방문"의 정의뿐입니다. 세 컬럼 UNIQUE가 같은 일을 합니다. 같은 장소라도 날짜가 다르면 다른 행이므로 **재방문이 그대로 기록됩니다.**

**대신 라벨의 단위를 장소로 확정합니다.** 재방문이 방문 단위 라벨을 만들면 같은 카페에 세 번 갔을 때 지도 핀 하나에 라벨이 세 개가 되고, 추천에서 `ON_HOLD`를 빼는 필터가 성립하지 않습니다.

### 리뷰가 그룹이 아니라 개인에 달리는 이유

`reviews.user_id`가 소유자이고, `with_group_id`가 그 방문을 함께한 그룹을 가리킵니다.

커플 앱이니 리뷰도 커플에 달아야 할 것 같지만, 그러면 **헤어졌을 때 리뷰가 갈 곳이 없습니다.** 더 중요하게는 취향이 그룹에 쌓이면 **다음 관계에서 처음부터 다시 시작**합니다. §5가 "취향은 개인에 쌓인다"로 시작한 이유가 스키마에서도 그대로 이어집니다.

### AI가 답을 두 개 주고, 각자의 표로 들어갑니다

도현이 "조용해서 얘기하기 좋았는데 웨이팅이 40분이었다"를 남기면 한 문장이 셋으로 갈라집니다.

| 태그 | `fact_value` — 가게 | `want_value` — 사람 | place_tags | user_tags |
| --- | --- | --- | --- | --- |
| 조용함 | 조용함 (HIGH) | 조용함 (HIGH) | fact_high +1 | want_high +1 |
| 대화 | 좋음 (HIGH) | 좋음 (HIGH) | fact_high +1 | want_high +1 |
| 웨이팅 | 김 (HIGH) | 짧음 (LOW) | fact_high +1 | want_low +1 |

**세 번째 줄이 두 값을 나눈 이유입니다.** 가게는 웨이팅이 길었고, 도현은 짧은 쪽을 원합니다. 한 값으로는 담을 수 없습니다.

만족도(`polarity`)를 따로 받지 않습니다. **두 값이 같으면 만족, 다르면 아쉬움**이라 유도되기 때문입니다. 전체적인 만족도는 별점이 이미 담고 있습니다.

문장에 근거가 없으면 각각 `null`입니다. `fact_value`가 null이면 장소 쪽만, `want_value`가 null이면 사람 쪽만 건너뜁니다. **추측해서 채우지 않습니다** — 아무도 말하지 않은 사실이 데이터가 되면 "우리 기록으로 추천한다"는 주장이 무너집니다.

### 정규화에 대한 방어

`place_tags`·`user_tags`·`group_places`는 `review_tags`와 `reviews`에서 전부 계산 가능한 파생 데이터라 정규화 관점에서는 중복입니다.

그럼에도 두는 이유는 **조회 경로**입니다. 추천 1회는 후보 30곳의 태그와 구성원 전체의 취향을 한 번에 읽습니다. 원본만 두면 매 요청마다 리뷰 전체를 집계해야 합니다. **`review_tags`가 유일한 원본이고 나머지 셋은 트랜잭션 안에서 동기적으로 유지되는 집계 캐시입니다.**

정규화를 깬 곳은 이 세 군데뿐이고, **셋 다 추천 조회 경로 위에 있습니다.** 캐시를 둘지는 정규화가 아니라 조회 경로가 정합니다.

### 온보딩은 별도 경로가 아니라 리뷰입니다

취향 판정식이 "2회 이상"이라 **리뷰가 0건이면 아무것도 뜨지 않습니다.** 처음엔 이 구멍을 태그 칩 설문으로 메우고, 고른 쪽에 `want_high_count`/`want_low_count`를 2 더해 임계를 즉시 넘기는 별도 경로를 뒀습니다.

**지금은 그 경로 자체를 없앴습니다(D-20).** 가입 직후 "먼저 가봤던 곳 2~3곳을 리뷰해 주세요"로 안내해 UC-04(리뷰 작성)를 그대로 씁니다. 칩 설문은 `user_tags`만 채우고 `place_tags`는 못 채웠고, 협업 필터링 가중치 `w_cf(n)`의 `n`(리뷰 수)에도 기여하지 못했습니다. 리뷰로 통일하면 온보딩 한 번이 `user_tags`·`place_tags`·`n` 셋을 동시에 채웁니다.

`want_high_count`/`want_low_count`에 값이 들어오는 경로가 **`review_tags`를 거치는 하나뿐**이라, "이 값이 설문에서 왔는지 리뷰에서 왔는지" 구분해 저장할 컬럼이 애초에 필요 없습니다. 온보딩 전용 예외가 사라지면서 규칙이 오히려 단순해졌습니다.

### 두 요약 테이블의 갱신 규칙이 다릅니다

| | `place_tags` | `user_tags` |
| --- | --- | --- |
| 세는 단위 | **사람마다 1표** (그 사람의 최신 리뷰만) | **리뷰마다 1표** |
| 갱신 방식 | **다시 세기** | 더하기 (수정 시 `-1` / `+1`) |

**표의 주인이 다르기 때문입니다.** `user_tags`는 리뷰 하나가 표 하나라 위치가 안 변합니다. `place_tags`는 사람 하나가 표 하나인데 **그 사람을 대표하는 리뷰가 계속 바뀝니다.** 재방문하면 옛 표를 새 표가 대신하고, 최신 리뷰를 지우면 표가 사라지는 게 아니라 그 전 리뷰로 넘어갑니다.

게다가 `place_tags`에는 **누가 어느 쪽에 던졌는지가 저장돼 있지 않습니다.** 숫자 두 개뿐이라 옛 표를 빼려면 그게 뭐였는지 알 방법이 없습니다. 그 칸 전체를 다시 세면 알 필요가 없어집니다.

```sql
SELECT DISTINCT ON (r.user_id) rt.fact_value      -- 사람별로 한 줄만
FROM review_tags rt JOIN reviews r ON r.review_id = rt.review_id
WHERE r.place_id = ? AND rt.tag_id = ? AND rt.fact_value IS NOT NULL
ORDER BY r.user_id, r.visited_on DESC              -- 그중 가장 최근 방문
```

"최신"의 기준은 수정 시각이 아니라 **방문 날짜**입니다. 3년 전 기록의 오타를 오늘 고쳤다고 그것이 이 가게의 최신 정보가 되지는 않습니다.

**갱신 시점은 조회가 아니라 쓰기입니다.** 리뷰를 저장하는 트랜잭션 안에서 요약을 만들어 두고, 추천할 때는 꺼내 쓰기만 합니다.

### 추천을 저장합니다 — 요청과 결과를 나눕니다

`recommendation_requests` 한 건에 `recommendations` 여러 줄이 붙습니다. 왜 추천 결과 하나를 즉시 응답에 실어 돌려주지 않고 테이블 두 개로 나눠 저장하는지는 처리 시간 때문입니다.

지역·개수·예산은 규칙으로 즉시 뽑히지만, 그다음 협업 필터링과 태그 매칭을 결합해 후보 순위를 매기고 이유 문장을 붙이는 데는 시간이 걸립니다. 그래서 요청이 들어오면 `recommendation_requests` 행을 `PENDING`으로 먼저 만들고, 처리가 끝나면 `recommendations`에 결과 행을 채운 뒤 `status`를 `COMPLETED`로 바꿉니다. API가 `202 Accepted` 뒤에 별도 조회를 두는 것과 같은 이유이고, 그 상태 전환이 이 두 테이블에 그대로 저장됩니다.

**`cf_weight`(`recommendation_requests`)는 이번 요청에서 협업 필터링이 얼마나 반영됐는지를 0~1로 남깁니다.**

```
w_cf(n) = min(1, n / 20)      n = 리뷰 수
```

**그룹 요청에서는 `n`이 구성원 중 가장 적은 사람의 리뷰 수입니다.** 그래야 신입 회원 한 명이 섞였을 때 그 사람 몫의 태그 매칭 비중이 억지로 눌리지 않고, 값이 하나로 확정되므로 `cf_weight` 컬럼도 요청당 한 줄로 남습니다.

리뷰 0건이면 0, 20건 이상이면 1에 수렴합니다. 이 값을 요청 시점에 굳혀 두는 이유는 **재현성**입니다 — 같은 사용자라도 리뷰가 5건 더 쌓인 다음 조회하면 가중치가 달라지므로, 그 순간의 판단 근거를 스냅샷으로 남기지 않으면 "왜 그때 이렇게 추천했는지"를 나중에 설명할 수 없습니다.

**`recommendations.basis`(OWN/OTHERS)는 LLM이 정하지 않습니다.** 후보를 모으고 순위를 매긴 알고리즘이 그 장소가 우리 그룹 기록에서 나왔는지 다른 그룹 기록에서 나왔는지 이미 알고 있으므로, 그 값을 그대로 실어 보냈다가 응답에 되받습니다. LLM에게 판단하게 하면 "우리가 갔던 곳"과 "남이 간 곳"이 틀린 채로 섞일 수 있고, 그건 이 서비스가 지키려는 신뢰와 정면으로 부딪힙니다.

**`recommendations.matched_tags`는 `tag_id`로 조인하지 않고 그 시점의 태그 이름을 문자열 배열로 굳혀 둡니다.** 다른 집계 테이블은 전부 원본에서 다시 계산되는 파생 데이터라 실시간으로 조인해도 되지만, 추천 결과는 **그 순간 사용자에게 보여준 이유의 스냅샷**입니다. 나중에 태그 사전이 33개에서 늘거나 이름이 바뀌어도 과거 추천이 왜 그렇게 나왔는지는 그대로 남아야 합니다.

`recommendation_id`가 아니라 `(request_id, place_id)`에 UNIQUE를 건 이유도 같은 맥락입니다 — 같은 요청 안에서 같은 장소가 두 번 추천되는 것은 알고리즘의 버그이지 정상 상태가 아닙니다.

### 협업 필터링 유사도를 미리 계산해 둡니다

`place_similarity`는 **장소 대 장소**의 피어슨 상관계수를 담습니다. 두 장소를 함께 평가한 사람들의 별점으로 계산하며, 추천 요청 때는 조회만 합니다.

```
place_similarity
  place_id           BIGINT        -- 기준 장소
  similar_place_id   BIGINT        -- 비교 대상
  score              NUMERIC(5,4)  -- -1.0000 ~ 1.0000
  computed_at        TIMESTAMPTZ
  UNIQUE (place_id, similar_place_id)
```

**사용자 기반이 아니라 아이템 기반인 이유는 갱신 주기입니다.** 사람의 취향은 리뷰를 쓸 때마다 바뀌지만 장소의 성격은 훨씬 천천히 바뀝니다. 그래서 하루 한 번 다시 계산해도 충분하고, 그 덕분에 추천 요청 경로에서 계산이 통째로 빠집니다. 사용자 수가 늘어도 계산량이 사용자 수의 제곱으로 튀지 않는 것도 같은 선택의 결과입니다.

**두 장소를 함께 평가한 사람이 2명 미만이면 행을 만들지 않습니다.** 1명뿐이면 상관계수가 의미를 갖지 못하기 때문입니다. 그래서 서비스 초기에는 이 테이블이 대부분 비어 있고, 그 구간은 태그 매칭이 받습니다(PRD §7).

**증분이 아니라 통째로 다시 씁니다.** 리뷰 하나가 여러 장소 쌍의 유사도를 동시에 흔들기 때문에 부분 갱신이 성립하지 않습니다. `place_tags`를 재계산하는 것과 같은 이유입니다.

3일 범위에서는 새벽 배치 스케줄러를 만들지 않고 **시드 스크립트가 한 번 계산해 채웁니다.** 값 자체는 실제 계산 결과라 추천 순위 로직은 시연에서 그대로 동작합니다.

### 월간 리포트를 저장합니다 — 그룹에 한 달 한 편

`monthly_reports`는 **그룹의 한 달 리포트 한 편**입니다. 리포트의 주인은 요청한 사람이 아니라 그룹이고, 그래서 `group_id`가 주인이고 `requested_by_user_id`는 기록일 뿐입니다. 한 명이 결제하면 둘 다 보고, 한 명이 생성하면 둘 다 읽습니다.

```
monthly_reports
  report_id             BIGINT       PK
  group_id              BIGINT       FK  -- 주인
  report_month          DATE             -- 해당 월 1일. Asia/Seoul 기준
  status                PENDING | COMPLETED | FAILED
  model                 VARCHAR      -- gpt-4o-mini 등. 무엇으로 썼는지
  prompt_tokens         INTEGER      -- usage 실측
  completion_tokens     INTEGER      -- usage 실측
  content               JSONB        -- 출력 스키마 그대로
  requested_by_user_id  BIGINT       FK  -- 기록
  created_at · completed_at
  UNIQUE (group_id, report_month)
```

**왜 저장하나 — 유료 재생성 비용 때문입니다.** 추천 이유 문장은 템플릿으로 대체 가능하고 실패해도 다시 요청하면 그만이지만, 리포트는 호출 한 번에 실제 비용이 나가는 유료 기능입니다. 열 때마다 다시 쓰면 같은 달을 열 번 열면 열 번 결제 원가가 나가고, 같은 입력에 매번 다른 문장이 나와 "우리의 8월"이 열 번 다르게 읽힙니다. 한 번 쓴 것을 저장하고 이후로는 조회만 합니다.

**`UNIQUE (group_id, report_month)`가 월 1회를 강제합니다.** 같은 달 두 번째 요청은 이 제약에 걸려 `409`가 되고, 응답이 기존 `reportId`를 함께 돌려주므로 화면은 새로 만들지 않고 기존 리포트를 엽니다. 상태 코드가 DB 제약에서 나온다는 D-17이 여기서도 그대로입니다. 실패한 리포트를 재시도할 때는 새 행을 만들지 않고 **같은 행의 `status`를 `PENDING`으로 되돌려 갱신**하므로 UQ와 충돌하지 않습니다.

**`content`를 JSONB 한 컬럼으로 두는 이유는 출력 스키마가 고정이기 때문입니다.** `title`·`summary`·`highlights`·`tasteShift`·`splitTags`·`nextMonth`·`closingLine`을 테이블 일곱 개로 펼치면 조인만 늘고 얻는 것이 없습니다. 리포트를 항목별로 검색하거나 집계할 일이 없고, 화면은 항상 한 편을 통째로 읽습니다. `recommendations.matched_tags`를 배열로 굳힌 것과 같은 이유로 **그 시점에 보여 준 문장의 스냅샷**이라 나중에 사전이나 장소 이름이 바뀌어도 과거 리포트는 그대로 읽혀야 합니다.

**`model`·`prompt_tokens`·`completion_tokens`는 원가의 실측치입니다.** 응답의 usage를 그대로 저장하며, 리포트 1건 원가 = `prompt_tokens × 입력 단가 + completion_tokens × 출력 단가`가 이 세 컬럼에서 나옵니다. 가격을 정할 때 추정이 아니라 실측으로 손익분기를 계산할 수 있습니다([기획-정리.md](기획-정리.md) §5.6).

### 상태 컬럼을 둔 이유 — D-14의 예외입니다

D-14는 "비동기 파이프라인을 전제한 상태 컬럼을 두지 않는다"였고, 리뷰의 `tagStatus`는 응답 필드로만 두었다가 v2.2에서 컬럼(`reviews.tag_status`)이 됐습니다. 리포트의 `status`도 같은 예외입니다.

**리포트 생성은 추천과 같은 2단계 비동기입니다.** 요청이 오면 집계를 SQL로 끝내고 `PENDING` 행을 먼저 만든 뒤 `202`로 돌려보내고, 그다음 LLM 호출(수 초)과 검증·저장이 트랜잭션 밖에서 이어집니다. 화면은 `PENDING`이면 스켈레톤을 그리고 `COMPLETED`가 될 때까지 조회합니다. `recommendation_requests.status`와 역할이 같아 이름과 값(PENDING / COMPLETED / FAILED)을 그대로 맞췄습니다.

**`FAILED`가 별도 상태인 이유는 유료이기 때문입니다.** 추천은 이유 생성이 실패하면 템플릿 문장으로 채워 `COMPLETED`로 끝내도 되지만, 돈을 낸 사용자에게 템플릿을 완성본으로 줄 수는 없습니다. 그래서 `FAILED`로 남기고 화면에 재시도 버튼을 둡니다. `content`가 비어 있는 행이 `PENDING`인지 `FAILED`인지는 `review_tags` 유무로 유추할 수 없었던 `tag_status`와 똑같이, 상태 컬럼이 있어야만 구분됩니다.

### 구독을 테이블로 두는 이유 — 현재 값과 이력을 나눕니다

`relation_groups.plan`은 **현재 요금제의 캐시**이고, `subscriptions`가 **이력**입니다. 플랜 검사는 서비스 계층 한 곳에서 `plan` 컬럼만 읽으면 되므로 매 요청마다 구독 이력을 조인하지 않습니다.

```
subscriptions
  subscription_id  BIGINT       PK
  group_id         BIGINT       FK   -- 구독 주체는 그룹
  plan             VARCHAR           -- PREMIUM
  started_at       TIMESTAMPTZ       -- 언제부터 유료였나
  ended_at         TIMESTAMPTZ NULL  -- 해지·만료. 지금은 채우는 경로가 없음 (R8)
  payment_ref      VARCHAR           -- Mock 승인 번호. 실제 PG면 거래 ID
```

**결제 연동은 비목표인데도 테이블을 두는 이유는 "언제부터 유료였는가"가 없으면 환불·분쟁 대응이 불가하기 때문입니다.** 플래그 하나만 뒤집으면 지난달에 유료였는지 오늘 유료가 됐는지 알 수 없습니다. `payment_ref`에는 3일 범위에서 `PaymentClient` Mock이 만든 승인 번호가 들어가고, 실제 PG로 바꾸면(R7) PG 거래 ID가 들어옵니다. **컬럼은 바뀌지 않고 구현체만 바뀝니다.** `AiClient`·`RecommendationClient`에 이어 Interface First의 세 번째 사례입니다.

**구독 취소 후에도 과거 리포트는 열립니다.** `monthly_reports`에는 플랜 조건이 없고, 열람 권한은 `group_members`(내 그룹인가)만 봅니다. 플랜은 **새로 만들 때**만 검사합니다. 이미 결제해서 만든 것을 해지했다고 다시 잠그면 그것이 분쟁의 시작입니다.

### 지도 공급자 분리

```
place
  place_id            BIGINT   PK   -- 내부 식별자
  provider            VARCHAR       -- 'KAKAO'
  provider_place_id   VARCHAR       -- 카카오가 준 값
  region              VARCHAR       -- '인사동'. 질의 해석 규칙이 읽은 지역으로 후보를 거릅니다
  price_band          TINYINT       -- 1~4. 예산은 필터이지 취향이 아닙니다
  UNIQUE (provider, provider_place_id)
```

중복 등록 판단은 이 UNIQUE 제약이 담당합니다. 외부 ID를 PK로 삼지 않았으므로 공급자를 바꿔도 리뷰와 태그가 끊기지 않습니다. 지도 API는 **카카오맵**으로 확정합니다. 국내 장소 밀도가 높고 place ID를 제공하기 때문입니다.
