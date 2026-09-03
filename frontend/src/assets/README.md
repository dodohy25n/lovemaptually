# 에셋

원본은 `frontend/frontend-assets/`(팀 디자인 산출물)에 그대로 두고,
여기에는 **웹에서 바로 쓸 수 있게 줄인 사본**만 둡니다. 원본을 고쳤으면 사본도 다시 만들어 주세요.

## 왜 두 군데로 나뉘나

| 위치 | 참조 방법 | 쓰는 경우 |
|---|---|---|
| `src/assets` | `@/assets/...` import (CSS는 `../assets/...`) | 코드가 직접 쓰는 에셋. 번들러가 해시를 붙여 캐시가 안전합니다. |
| `public` | `/favicon.svg`, `/samples/*.jpg` | 파비콘, 그리고 **localStorage에 저장되는** 시연용 사진. 해시가 붙으면 다시 빌드했을 때 저장된 URL이 깨지므로 고정 경로가 필요합니다. |

## 목록

| 파일 | 사용처 | 원본 |
|---|---|---|
| `icons/heart-good.svg` | 4.0~5.0 하트 (등급 안내·최근 방문·지도 핀) | `frontend-assets/icons` |
| `icons/heart-normal.svg` | 2.0~3.9 하트 | 〃 |
| `icons/heart-bad.svg` | 0~1.9 하트 | 〃 |
| `characters/raccoon-lovey.png` | 마스코트 '러비' (챗봇 버튼·패널 헤더·빈 상태) | `frontend-assets/characters/love_maptually_raccoon_chatbot.png` (1380×1140 → 320×264) |
| `decorations/paper-texture.jpg` | body 배경 종이 질감 | `frontend-assets/decorations/love_maptually_paper_texture.png` (2.2MB PNG → 130KB JPEG) |
| `decorations/pink-tape.svg` | 카드 모서리 테이프 (`.lm-tape`) | `frontend-assets/decorations` |
| `couple-avatar.svg` | '우리의 러브맵' 커플 일러스트 | **임시** — 디자인 에셋 대기 중 |
| `photo-placeholder.svg` | 사진이 없을 때의 자리표시 | **임시** — 디자인 에셋 대기 중 |

## 축소본 다시 만들기

```sh
cd frontend
sips -Z 320 frontend-assets/characters/love_maptually_raccoon_chatbot.png \
  --out src/assets/characters/raccoon-lovey.png
sips -s format jpeg -s formatOptions 72 -Z 900 \
  frontend-assets/decorations/love_maptually_paper_texture.png \
  --out src/assets/decorations/paper-texture.jpg
```

시연용 사진은 `frontend-assets/samples/love_maptually_cafe_photo_grid.png`(3×2 묶음)을
6장으로 잘라 `public/samples/`에 넣은 것입니다.

## 주의

- 하트 3종은 **크기와 시각 무게가 같아야** 합니다. 지도 핀과 목록에서 나란히 놓입니다.
- 마스코트 PNG의 가로세로비는 **1380:1140**입니다. `<img>`의 width/height를 바꿀 때 이 비율을 지켜주세요.
- 텍스트(장소명·점수)는 절대 이미지에 넣지 마세요. 전부 HTML로 렌더링합니다.

## 아직 붙이지 않은 것

- `frontend-assets/decorations/love_maptually_scrapbook_overlay{,_v2}.png` — 896×1755 세로 프레임이라
  반응형 레이아웃에 늘려 넣으면 스프링 장식이 찌그러집니다. 쓸 화면이 정해지면 그때 붙입니다.
- `frontend-assets/characters/love_maptually_raccoon_chatbot.svg`,
  `love_maptually_chatbot_popup.svg` — 말풍선·버튼까지 그려진 **시안**이라 단독 에셋으로는 쓸 수 없습니다.
  (말풍선과 버튼은 이미 `ChatbotButton.vue`가 HTML/CSS로 그립니다.) 시안 참고용으로만 두세요.
