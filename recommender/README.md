# recommender — 추천 엔진 서비스

lovemaptually의 장소 추천 순위를 계산하는 FastAPI 서비스입니다. Spring API 계층이
"어느 그룹이 어느 지역에서 몇 곳을 원한다"를 넘기면, 이 서비스가 후보를 모으고 점수를 매겨
정렬된 추천 목록과 이유 문장을 돌려줍니다.

## 왜 Spring 안이 아니라 별도 프로세스인가

순위 계산은 결국 행렬과 숫자를 다루는 일입니다. 유사도 조회, 평균 보정, 가중 결합, 그룹 합의는
파이썬이 자연스럽게 하는 작업이고 자바에서는 같은 일을 하는 데 코드가 훨씬 길어집니다.
그래서 랭킹만 파이썬 프로세스로 떼어냈습니다.

떼어 놓으면 얻는 것이 하나 더 있습니다. **랭킹 방식을 바꿔도 API 계층은 건드리지 않아도 됩니다.**
Spring은 `POST /recommendations`의 요청과 응답 계약만 알고 있으면 되고, 그 안에서 CF 비중을
바꾸든 태그 점수 공식을 갈아엎든 이 저장소 안에서 끝납니다. 이유 문장도 `ReasonWriter` 인터페이스
뒤에 있어서, 지금의 템플릿 구현을 다른 구현으로 갈아끼워도 파이프라인은 그대로입니다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `app.py` | FastAPI 앱. DB에서 재료를 모아 파이프라인을 돌리고 응답을 조립합니다. |
| `scoring.py` | 점수 계산 순수 함수와 이유 문장 생성기입니다. DB를 모릅니다. |
| `query_parser.py` | 자연어에서 지역, 개수, 예산대를 뽑는 규칙 기반 파서입니다. LLM을 쓰지 않습니다. |
| `tests/test_pipeline.py` | DB 없이 도는 순수 함수 테스트입니다. |

## 실행

```bash
cd recommender
uv venv
uv pip install fastapi uvicorn "psycopg[binary]" numpy pytest httpx
uv run uvicorn app:app --port 8000
```

DB 접속 주소는 환경변수 `PG_URL`로 바꿉니다. 기본값은
`postgresql://lovemaptually:lovemaptually@localhost:5432/lovemaptually`입니다.

## 테스트

```bash
cd recommender
uv run pytest
```

DB 연결 없이 돕니다. 태그 방향 일치율, CF 예측, 가중치 곡선, 그룹 합의, 질의 파싱을 검증합니다.

## 요청과 응답 계약

### `POST /recommendations`

```json
{
  "groupId": 1,
  "memberIds": [1, 2],
  "region": "인사동",
  "count": 3,
  "budget": null
}
```

```json
{
  "region": "인사동",
  "candidateCount": 12,
  "cfWeight": 0.35,
  "degraded": false,
  "recommendations": [
    {
      "placeId": 42,
      "name": "달빛찻집",
      "category": "카페",
      "priceBand": 2,
      "latitude": 37.5741,
      "longitude": 126.9851,
      "matchedTags": ["조용함", "대화"],
      "basis": "OWN",
      "reason": "조용함, 대화하기 좋음 같은 점이 두 분 취향과 맞아 골랐습니다.",
      "displayOrder": 1
    }
  ],
  "notice": null
}
```

- `candidateCount`: STEP 2를 통과한 후보 장소 수입니다.
- `cfWeight`: 이번 요청에 쓰인 CF 비중입니다.
- `degraded`: CF를 쓸 만큼 리뷰가 쌓였는데 유사도 데이터가 하나도 없어 태그 점수만으로 내려간 경우 `true`입니다.
- `basis`: `OWN`은 우리 그룹이 이미 평가한 장소를 이웃으로 써서 예측했다는 뜻이고,
  `OTHERS`는 그렇지 않다는 뜻입니다. 알고리즘이 정하며 LLM이 개입하지 않습니다.
- `notice`: 지역이 안 열렸거나 합의 기준선을 아무도 못 넘었을 때만 채워집니다.

### `GET /parse?query=...`

```bash
curl 'http://localhost:8000/parse?query=오늘 인사동 갈 건데 3곳 정도 추천해줘'
# {"region":"인사동","count":3,"budget":null}
```

지역을 못 찾으면 `region`이 `null`입니다. Spring 계층이 이 경우를 422로 되돌립니다.

### `GET /health`

서비스 상태와 현재 `CF_RAMP_FULL` 값을 돌려줍니다.

## 파이프라인 (설계 문서 흐름 2, STEP 2 ~ STEP 10)

1. **STEP 2 후보 수집** — 요청 지역의 장소 중 `place_tags`가 하나라도 있고, 이 그룹에서
   `ON_HOLD`로 접어둔 곳이 아니며, 멤버 누구도 리뷰한 적 없는 곳을 모읍니다.
   `budget`이 오면 `price_band <= budget`도 겁니다.
2. **STEP 3 지역 게이트** — 후보가 `REGION_OPEN_THRESHOLD`(3) 미만이면 추천을 내보내지 않고
   `notice`에 "이 지역은 아직 추천이 열리지 않았습니다"를 담아 빈 목록을 돌려줍니다.
3. **STEP 4a CF 점수** — 아이템 기반입니다. 후보 장소와 유사한 장소 중 그 멤버가 이미 평가한
   곳을 이웃으로 삼아 `멤버평균 + Σ(유사도 x (이웃평점 - 이웃평균)) / Σ|유사도|`를 예측하고
   1..5로 자릅니다. 쓸 이웃이 없으면 "CF 없음"입니다.
4. **STEP 4b 태그 점수** — 방향 일치율입니다. 사람 쪽은 2표 이상이면서 한쪽이 많을 때만,
   장소 쪽은 `fact_high_count`와 `fact_low_count` 중 많은 쪽으로 방향을 정합니다.
   양쪽 다 판정된 태그만 분모에 넣고 방향이 같은 비율을 점수로 씁니다.
   분모가 0이면 0.5(중립)를 씁니다. 판정할 근거가 없는 상태와 취향이 정말 안 맞는 상태를
   똑같이 0점으로 두면 신규 사용자가 손해를 보기 때문입니다.
5. **STEP 5 n** — 그룹 멤버 각자의 총 리뷰 수 중 **최솟값**입니다. 한 명이라도 리뷰가 적으면
   그 그룹의 CF는 아직 못 믿는다는 뜻입니다.
6. **STEP 6 가중 결합** — `w_cf = min(1, n / CF_RAMP_FULL)`입니다. CF가 없으면 태그 점수를
   `1 + 4 x 태그점수`로 늘려 그대로 쓰고, 있으면 두 값을 `w_cf`로 섞습니다.
   두 경로 모두 1..5 예상 평점 척도라 STEP 7의 2.5 기준선이 양쪽에 똑같이 의미를 갖습니다.
7. **STEP 7 그룹 합의** — 멤버 최저 점수가 2.5 미만인 후보를 떨어뜨리고 평균 내림차순으로
   세웁니다. 전멸하면 기준선을 풀고 전체를 평균순으로 세운 뒤
   `notice`에 "모두가 좋아할 만한 곳이 없어 평균 순으로 채웠습니다"를 담습니다.
8. **STEP 8** — 상위 `count`개(기본 3개)를 자릅니다.
9. **STEP 9 이유 문장** — `TemplateReasonWriter`가 템플릿으로 만듭니다. 맞은 태그를
   사전 라벨 형태로 넣고, 그룹 안에서 방향이 갈린 태그가 있으면 누가 어느 쪽인지를 닉네임으로
   밝힙니다. `cfWeight`가 0.5 미만이면 "취향 태그" 쪽으로, 0.5 이상이면
   "비슷한 취향의 다른 커플들이 다녀온 곳" 쪽으로 문장을 기울입니다.
   템플릿은 `placeId`로 고르므로 같은 장소는 언제나 같은 문장이 나옵니다.
10. **STEP 10** — `displayOrder`를 1부터 매겨 응답을 조립합니다.

## 튜닝 손잡이 — `CF_RAMP_FULL`

`w_cf = min(1, n / CF_RAMP_FULL)`의 분모입니다. 기본값은 20이고, 환경변수로 바꿉니다.

```bash
CF_RAMP_FULL=30 uv run uvicorn app:app --port 8000
```

- **값을 키우면** CF 비중이 천천히 올라갑니다. 리뷰가 웬만큼 쌓이기 전까지는 취향 태그를 믿습니다.
  유사도 데이터가 아직 얇을 때 안전한 선택입니다.
- **값을 줄이면** 적은 리뷰로도 CF에 무게가 실립니다. 데이터가 충분히 모인 뒤에 내리는 게 맞습니다.

값은 요청마다 다시 읽으므로 재배포 없이 환경변수만 바꿔 반영할 수 있습니다.

## 스키마 관련 가정

- `budget`이 들어오면 `price_band`가 `NULL`인 장소는 제외합니다. 예산대를 확인할 수 없는 곳을
  예산 조건에 통과시킬 근거가 없기 때문입니다.
- CF의 이웃 평균은 그 장소의 전체 리뷰 평균입니다.
- 같은 사람이 같은 장소를 여러 번 방문해 리뷰가 여러 건이면 평균 평점 한 개로 접어서 씁니다.
- 리뷰가 하나도 없는 멤버의 평균 평점은 3.0으로 둡니다.
