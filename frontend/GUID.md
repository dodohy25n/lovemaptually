# Love Maptually 에셋 묶음

## 폴더 구성

- `frontend-assets/icons`: 점수별 하트 SVG
- `frontend-assets/decorations`: 종이 질감, 스크랩북 오버레이, 테이프
- `frontend-assets/characters`: 너구리 챗봇 PNG/SVG
- `frontend-assets/samples`: UI 시연용 카페 사진 모음
- `design-references`: Figma 및 Vue 구현 참고용 전체 화면 SVG

## Vue 프로젝트 적용

`frontend-assets` 아래의 폴더를 Vue 프로젝트의 `frontend/src/assets`에 복사하세요.
코드에서는 `/Users/.../Desktop/...` 같은 절대경로를 사용하지 말고 `@/assets/...` 상대경로로 불러오세요.

예시:

```js
import heartGood from '@/assets/icons/heart-good.svg'
import raccoon from '@/assets/characters/raccoon-chatbot.png'
```

`samples`의 이미지는 시연용입니다. 실제 사진 업로드 기능이 연결되면 사용자 데이터로 교체하세요.


## 적용 결과 (2026-09-03)

이 묶음은 프론트엔드에 반영됐습니다. 실제로 어떤 파일이 어디로 갔는지는
[src/assets/README.md](src/assets/README.md)에 정리해 뒀습니다. 요약하면:

- `icons`, `decorations/pink-tape.svg`는 그대로 `src/assets`로.
- 큰 PNG 두 개(마스코트·종이 질감)는 **웹용 축소본**을 만들어 `src/assets`에 넣었습니다.
  원본은 여기 `frontend-assets`에 그대로 두니 계속 원본을 갱신해 주세요.
- `samples`는 6장으로 잘라 `public/samples/`에 두고 seed 데이터에 연결했습니다.
  (localStorage에 저장되는 값이라 해시가 붙지 않는 고정 경로가 필요합니다.)
- 스크랩북 오버레이와 챗봇 시안 SVG는 아직 붙이지 않았습니다 — 이유는 위 README에.
