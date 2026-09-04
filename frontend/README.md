# Love Maptually · Frontend

커플이 함께 방문한 장소를 지도에 기록하고, 각자 점수와 리뷰를 남기는 서비스의 프론트엔드입니다.

> **두 가지 모드로 돕니다.** `VITE_DATA_MODE` 하나로 갈립니다.
>
> - `api` — Spring 백엔드(`http://localhost:8080`)를 그대로 씁니다. 로그인, 그룹, 지도 핀,
>   장소 검색, 우리 취향, 추천, 월간 리포트가 모두 실제 API를 탑니다.
> - `local` — 백엔드 없이 브라우저 `localStorage`만으로 돕니다. 데모와 E2E 테스트가 쓰는 모드입니다.
>
> 화면 코드는 모드를 알지 못합니다. 모드 판정은 `services/config.js` 한 곳에서만 합니다.

## 실행

```bash
cd frontend
npm install
npm run dev            # 개발 서버 (http://localhost:5173)
```

> 문제가 생기면 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)를 먼저 보세요.
> 원인 파악에 시간이 걸렸던 사례를 증상별로 모아 두었습니다.

## 스크립트

| 명령 | 설명 |
|---|---|
| `npm run dev` | 개발 서버 |
| `npm run build` | 프로덕션 빌드 (`dist/`) |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm test` | 단위 테스트 (Vitest) |
| `npm run test:watch` | 단위 테스트 watch 모드 |
| `npm run test:e2e` | E2E 테스트 (Playwright) |
| `npm run test:e2e:install` | E2E용 Chromium 설치 (최초 1회) |

E2E는 실행할 때마다 빌드 후 `127.0.0.1:4399`에 미리보기 서버를 띄웁니다.
포트를 바꾸려면 `PLAYWRIGHT_PORT=4500 npm run test:e2e`.
E2E는 백엔드 없이 도는 것을 검증하므로 `.env` 값과 무관하게 **항상 로컬 모드로 빌드**합니다
(`playwright.config.js`의 `webServer.env`).

## 환경 변수

`.env.example`을 복사해 `.env`를 만들어 사용하세요. `.env*`는 커밋되지 않습니다
(`.env.example`만 예외).

```bash
cp .env.example .env
```

| 변수 | 값 | 설명 |
|---|---|---|
| `VITE_DATA_MODE` | `local` \| `api` | 데이터 출처. `local`은 localStorage, `api`는 백엔드 |
| `VITE_API_BASE_URL` | URL | 백엔드 base URL. 로컬 개발은 `http://localhost:8080` |
| `VITE_KAKAO_JS_KEY` | 문자열 | 카카오 **JavaScript 키**. 지도와 가게 검색이 함께 씁니다 |
| `VITE_AI_MODE` | `mock` \| `api` | 챗봇 응답 출처. 챗봇 백엔드가 없어 `api` 모드에서는 위젯을 숨깁니다 |

### 개발 서버 포트가 고정입니다

백엔드 CORS가 `http://localhost:5173` 하나만 허용하므로 `vite.config.js`에 `strictPort: true`를
두었습니다. 5173이 이미 쓰이고 있으면 서버가 다른 포트로 밀리는 대신 **실행이 실패합니다.**
포트가 밀린 채로 뜨면 모든 API 호출이 preflight에서 막혀 원인을 찾기 어렵기 때문입니다.

### ⚠️ 키 관련 주의

- `VITE_` 로 시작하는 값은 **전부 브라우저 번들에 그대로 포함되어 사용자에게 공개됩니다.**
  서버 전용 REST/Secret 키는 어떤 경우에도 넣지 마세요.
- 카카오 키는 **두 종류이고 안전성이 다릅니다.** JavaScript 키(`VITE_KAKAO_JS_KEY`)는
  도메인 제한을 걸면 프론트엔드에 두어도 되지만, **REST API 키는 서버 전용**이라
  프론트엔드에 절대 두지 않습니다. REST 키는 backend의 환경 변수로 관리하세요.
- JavaScript 키는 카카오 콘솔에서 **사이트 도메인 등록이 필수**입니다. 등록하지 않으면
  지도와 검색이 모두 조용히 실패합니다 → [트러블슈팅 1번](TROUBLESHOOTING.md#1)
- 키가 없거나 SDK를 못 불러와도 앱은 죽지 않습니다. 지도는 타일 없는 대체 화면으로,
  검색은 직접 입력으로 각각 물러나며 **기록한 장소와 핀은 그대로 동작합니다.**


## 구조

화면은 뷰 파일 6개(404 제외 실제 화면 5개), 컴포넌트 25개, 서비스 모듈 19개입니다.

| 폴더 | 담는 것 |
|---|---|
| `src/views/` | 라우트 단위 화면 6개 (지도, 로그인, 회원가입, 기억, 추억 저장소, 404) |
| `src/components/` | 화면을 이루는 조각 25개 (지도 캔버스, 하트 핀, 리뷰 카드, 모달, 챗봇) |
| `src/stores/` | Pinia 스토어 2개 (`places`, `chatbot`) |
| `src/services/` | 저장소와 API 어댑터 계층. 화면은 이 계층 너머를 알지 못합니다 |
| `src/utils/` | 순수 함수 (하트 등급, 좌표 검증, 장소 동일성, 구성원 이름) |
| `src/styles/` | 디자인 토큰(`tokens.css`)과 공통 스타일 |
| `src/router/` | 라우트 정의 |
| `src/assets/` | 아이콘과 장식 이미지 |

### 라우트

| 경로 | 화면 |
|---|---|
| `/` | `/login` 으로 이동 |
| `/login` | 로그인 |
| `/signup` | 회원가입 |
| `/map` | 메인 지도 (우리 취향, 추천 모달이 여기에 붙습니다) |
| `/reviews/me` | 로그인한 사용자가 남긴 리뷰 |
| `/reviews/partner` | 함께 기록하는 구성원이 남긴 리뷰 |
| `/memories` | 추억 저장소 (월간 리포트가 여기에 있습니다) |
| `/reviews/him`, `/reviews/her` | 옛 주소. 각각 `/reviews/me`, `/reviews/partner` 로 넘깁니다 |

### 서비스 모듈과 API 대응

| 모듈 | 호출하는 엔드포인트 |
|---|---|
| `authApi.js` | `POST /api/auth/login`, `POST /api/auth/signup` |
| `groupApi.js` | `GET /api/groups/me`, `POST /api/groups` |
| `placeRepository.js` | `GET /api/groups/{groupId}/places` (지도 핀), `GET /api/places` (검색), `GET /api/places/{id}` |
| `placeApi.js` | 모드에 따라 Repository를 고르고 활성 그룹을 알려줍니다 |
| `reviewApi.js` | `POST /api/reviews` |
| `preferenceApi.js` | `GET /api/groups/{groupId}/preferences` |
| `recommendationApi.js` | `POST /api/groups/{groupId}/recommendation-requests`, `GET /api/recommendation-requests/{id}` |
| `reportApi.js` | `GET`·`POST /api/groups/{groupId}/reports`, `GET /api/reports/{id}`, `POST /api/groups/{groupId}/subscriptions` |
| `placeSearchApi.js` | 카카오 JS SDK (백엔드 아님) |
| `tagApi.js` | `GET /api/tags` — 백엔드에 아직 없어 실패 시 기본 키워드로 물러납니다 |
| `chatbotApi.js` | 없음. 백엔드가 없어 `api` 모드에서는 챗봇 위젯을 숨깁니다 |

### 비동기 작업 두 가지

추천과 월간 리포트는 요청하면 `202 PENDING` 이 오고 결과는 나중에 채워집니다.
두 모듈 모두 **0.5초 간격으로 최대 20회 폴링**하고, 그 안에 끝나지 않으면 안내 문구와 함께
`timeout` 으로 실패합니다. 화면이 분기할 수 있도록 오류에 코드를 실어 보냅니다.

| 코드 | 화면 처리 |
|---|---|
| `REGION_NOT_FOUND` | 오류가 아니라 "어느 동네인지" 되묻는 안내로 보여줍니다 |
| `plan_required` (402) | 자물쇠와 '프리미엄으로 열기' 버튼. 구독 후 곧바로 다시 시도합니다 |
| `report_already_exists` (409) | 새로 만들지 않고 응답에 담긴 기존 리포트를 엽니다 |
| `no_visits_in_month` (422) | 그 달에 기록이 없다고 안내합니다 |

### 지금 화면에 쓰이지 않는 파일

지우지 않고 남겨 둔 것들입니다. 정리하려면 확인 후 지우세요.

- `src/components/AppHeader.vue`
- `src/components/HeartGradeLegend.vue`
- `src/components/MemoryCard.vue`
- `src/components/PlaceDetailPanel.vue`

### 저장소 계층

```
PlaceRepository (인터페이스)
 ├─ LocalPlaceRepository   ← 현재 사용 (localStorage)
 └─ ApiPlaceRepository     ← 백엔드 완성 후 여기만 구현
```

- 화면과 스토어는 `services/placeApi.js`의 함수만 호출하며, 뒤가 localStorage인지 HTTP인지 모릅니다.
- 저장 키에는 버전이 붙습니다: `love-maptually:places:v1`
- 깨진 JSON, 저장소 접근 불가(프라이빗 모드), 용량 초과 상황에서도 앱은 중단되지 않습니다.
- 좌표는 저장 전 `Number`로 검증·정규화합니다.
- 장소 동일성(= 리뷰가 붙는 대상)은 `utils/placeIdentity.js` 한 곳에서만 판정합니다. 아래 참고.
- 커플 통합 점수와 하트 등급은 저장 시점에 리뷰로부터 항상 다시 계산합니다.

### 장소 동일성

리뷰는 '가게'에 붙으므로, 같은 가게가 두 번 등록되면 커플 점수가 갈라집니다.
그래서 모든 장소는 공급자 식별자를 함께 저장합니다:

| 필드 | 값 | 설명 |
|---|---|---|
| `provider` | `manual` \| `kakao` \| … | 장소를 어디서 가져왔는지 |
| `providerPlaceId` | 문자열 | 공급자가 부여한 가게 ID. 수기 입력이면 `''` |

`providerPlaceId === '' ⟺ provider === 'manual'` 은 저장된 모든 장소가 지키는 규칙입니다.

중복 판정 순서 (`isSamePlace`):

1. **양쪽 다 공급자 ID가 있으면 ID만 비교합니다.** 같은 건물 2층·3층은 좌표가 거의
   같아도 다른 가게이므로, 좌표를 섞어 보면 안 됩니다.
2. 한쪽이라도 수기 입력이면 이름 + 좌표(약 11m)로 최선을 다해 잡습니다.
   이 방식은 `디어 모먼트`/`디어모먼트` 같은 표기 차이를 구분하지 못하므로,
   **공급자 검색으로 등록하는 것이 항상 낫습니다.**

### 가게 검색 (카카오)

등록 모달의 **가게 검색**에서 고르면 `provider: 'kakao'` 와 `providerPlaceId` 가 함께
저장되고, 이름·주소·카테고리·좌표가 자동으로 채워집니다. 직접 입력하면 `manual` 입니다.

- 검색 SDK는 사용자가 검색을 시작할 때만 불러옵니다(첫 화면 로딩에 영향 없음).
- 키가 없거나 SDK를 못 불러와도 **직접 입력 경로는 그대로 동작합니다.**
- 좌표를 지도에서 다시 찍거나 '연결 끊고 직접 입력'을 누르면 `manual` 로 돌아갑니다.

#### 검색이 안 될 때

대부분 카카오 콘솔의 **사이트 도메인 미등록**입니다. 증상이 원인을 전혀 드러내지 않으니
[트러블슈팅 1번](TROUBLESHOOTING.md#1)의 진단 방법을 따라가세요.

### 백엔드 연결 지점

| 파일 | 현재 | 연결 후 |
|---|---|---|
| `services/placeRepository.js` | 목록·상세 연결 완료 | 등록·수정·삭제는 아직 미구현 |
| `services/reviewApi.js` | `POST /api/reviews` 연결 완료 | 조회는 아직 장소 Repository 경유 |
| `services/chatbotApi.js` | 키워드 기반 mock 응답 | `sendToAi()`에 실제 AI 호출 구현 |
| `services/tagApi.js` | `GET /api/tags` 호출 | 백엔드에 엔드포인트가 생기면 그대로 동작 |
| `services/placeSearchApi.js` | 카카오 JS SDK 직접 호출 | 서버 경유로 옮기면 REST 키를 백엔드에 둘 수 있습니다 |

## 하트 등급

| 점수 | 등급 | 에셋 |
|---|---|---|
| 4.0 ~ 5.0 | 좋아요 | `heart-good.svg` |
| 2.0 ~ 3.9 | 보통이에요 | `heart-normal.svg` |
| 0 ~ 1.9 | 아쉬워요 | `heart-bad.svg` |

경계값은 `src/utils/heartGrade.js` 한 곳에서만 정의합니다.
숫자 점수는 **이미지가 아니라 항상 HTML 텍스트**로 렌더링합니다.

## 디자인

- 디자인 원본: `frontend/design/Love Maptually - Wireframe@2x.png`
- 색상·간격·타이포는 `src/styles/tokens.css`의 CSS 변수로만 관리합니다.
- 장식(테이프, 스프링, 마스코트)은 `pointer-events: none` 이거나 별도 레이어에 있어
  본문 텍스트를 가리거나 클릭을 가로채지 않습니다.
- 에셋 교체 방법은 `src/assets/README.md` 참고 (원본 묶음은 `frontend-assets/`, 안내는 `GUID.md`).
