# Love Maptually · Frontend

커플이 함께 방문한 장소를 지도에 기록하고, 각자 점수와 리뷰를 남기는 서비스의 프론트엔드입니다.

> **백엔드는 아직 구현 전입니다.**
> 지금은 모든 데이터가 브라우저 `localStorage`에 저장되며, 챗봇은 정해진 mock 응답을 돌려줍니다.
> 백엔드가 완성되면 **저장소 계층(Repository)만 교체**하면 화면 코드는 그대로 동작합니다.

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

## 환경 변수

`.env.example`을 복사해 `.env.local`을 만들어 사용하세요. `.env*`는 커밋되지 않습니다
(`.env.example`만 예외).

```bash
cp .env.example .env.local
```

| 변수 | 값 | 설명 |
|---|---|---|
| `VITE_DATA_MODE` | `local` \| `api` | 장소 데이터 출처. `local`은 localStorage, `api`는 백엔드(미구현) |
| `VITE_KAKAO_JS_KEY` | 문자열 | 카카오 **JavaScript 키**. 지도와 가게 검색이 함께 씁니다 |
| `VITE_AI_MODE` | `mock` \| `api` | 챗봇 응답 출처 |
| `VITE_API_BASE_URL` | URL | 백엔드 base URL (`VITE_DATA_MODE=api`일 때만) |

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

```
src/
├─ components/     재사용 컴포넌트 (헤더, 지도, 하트 핀, 리뷰 카드, 챗봇 …)
├─ views/          라우트 단위 화면
├─ stores/         Pinia 스토어 (places, chatbot)
├─ services/       저장소·API 어댑터 계층  ← 백엔드 교체 지점
├─ utils/          순수 함수 (하트 등급, 좌표 검증, 사용자 정보)
└─ styles/         디자인 토큰과 공통 스타일
```

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
| `services/placeRepository.js` | `LocalPlaceRepository` | `ApiPlaceRepository`의 메서드에 fetch 구현 |
| `services/placeApi.js` | 모드에 따라 Repository 선택 | 수정 불필요 |
| `services/reviewApi.js` | 장소 Repository 경유 | `/places/:id/reviews` 호출로 교체 |
| `services/chatbotApi.js` | 키워드 기반 mock 응답 | `sendToAi()`에 실제 AI 호출 구현 |
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
