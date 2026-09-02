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
| `VITE_MAP_PROVIDER` | `osm` \| `carto` | 지도 타일 공급자. 둘 다 키 불필요 |
| `VITE_AI_MODE` | `mock` \| `api` | 챗봇 응답 출처 |
| `VITE_API_BASE_URL` | URL | 백엔드 base URL (`VITE_DATA_MODE=api`일 때만) |
| `VITE_MAP_CLIENT_KEY` | 문자열 | 키가 필요한 지도 공급자로 바꿀 때 쓰는 **공개용** 클라이언트 키 |

### ⚠️ 키 관련 주의

- `VITE_` 로 시작하는 값은 **전부 브라우저 번들에 그대로 포함되어 사용자에게 공개됩니다.**
  서버 전용 REST/Secret 키는 어떤 경우에도 넣지 마세요.
- 현재 기본 지도(OpenStreetMap)는 키가 필요 없습니다.
- 키가 필요한 공급자(카카오/네이버/구글 등)로 바꾼다면, 공개용 클라이언트 키라도
  **공급자 콘솔에서 도메인(Referer) 제한을 반드시 걸어야 합니다.** 제한이 없으면
  번들에서 키를 복사해 그대로 도용할 수 있습니다.
- 지도 API 호출이 실패해도 앱은 죽지 않습니다. 타일이 안 뜨면 fallback 배경과 안내를 보여주고,
  기록한 장소와 핀은 그대로 동작합니다.

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
- 좌표는 저장 전 `Number`로 검증·정규화하며, 같은 이름 + 거의 같은 좌표는 중복 등록을 막습니다.
- 커플 통합 점수와 하트 등급은 저장 시점에 리뷰로부터 항상 다시 계산합니다.

### 백엔드 연결 지점

| 파일 | 현재 | 연결 후 |
|---|---|---|
| `services/placeRepository.js` | `LocalPlaceRepository` | `ApiPlaceRepository`의 메서드에 fetch 구현 |
| `services/placeApi.js` | 모드에 따라 Repository 선택 | 수정 불필요 |
| `services/reviewApi.js` | 장소 Repository 경유 | `/places/:id/reviews` 호출로 교체 |
| `services/chatbotApi.js` | 키워드 기반 mock 응답 | `sendToAi()`에 실제 AI 호출 구현 |

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
- 에셋 교체 방법은 `public/assets/README.md` 참고.
