# 프론트엔드 Mock API 연동 현황

최종 갱신: 2026-09-04  
담당: 신용민 (`AshinYM`)

## 실행 환경

- Postman Mock Server: `https://eb065d73-185a-4912-8c34-fe7f0896e97b.mock.pstmn.io`
- 현재 로컬 기본값은 `VITE_DATA_MODE=local`이다. 장소 데이터 전체를 API 모드로 전환하는 작업은 아래 PR들을 통합한 뒤 진행해야 한다.
- 카카오 지도는 `VITE_KAKAO_JS_KEY`와 카카오 개발자 콘솔의 등록 도메인이 필요하다.
- 로컬 등록 도메인은 `http://localhost:5173`을 사용한다. `127.0.0.1`에서는 도메인 제한으로 지도가 로드되지 않을 수 있다.
- `.env.local`은 Git에 포함하지 않는다.

## main에 병합된 연동

| 기능 | API | PR | 상태 |
|---|---|---:|---|
| 태그 조회 | `GET /api/tags` | [#5](https://github.com/dodohy25n/lovemaptually/pull/5) | 병합 |
| 장소 검색 | `GET /api/places?query=...` | [#8](https://github.com/dodohy25n/lovemaptually/pull/8) | 병합 |
| 장소 상세 | `GET /api/places/{placeId}` | [#10](https://github.com/dodohy25n/lovemaptually/pull/10) | 병합 |
| 장소 목록 기반 코드 | `GET /api/places` | [#11](https://github.com/dodohy25n/lovemaptually/pull/11) | 병합·그룹 목록 API로 교체 예정 |
| 리뷰 등록 | `POST /api/reviews` | [#14](https://github.com/dodohy25n/lovemaptually/pull/14) | 병합 |
| 로그인 | `POST /api/auth/login` | [#15](https://github.com/dodohy25n/lovemaptually/pull/15) | 병합 |
| 회원가입 | `POST /api/auth/signup` | [#16](https://github.com/dodohy25n/lovemaptually/pull/16) | 병합 |
| 내 그룹 목록 | `GET /api/groups/me` | [#17](https://github.com/dodohy25n/lovemaptually/pull/17) | 병합 |
| 그룹 생성 | `POST /api/groups` | [#18](https://github.com/dodohy25n/lovemaptually/pull/18) | 병합 |

## 구현 완료, main 병합 대기

| 기능 | API 또는 변경 | 커밋 | PR |
|---|---|---|---:|
| 초대 코드 생성 | `POST /api/groups/{groupId}/invites` | `c998dc2` | [#19](https://github.com/dodohy25n/lovemaptually/pull/19) |
| 리뷰 점수 형식 수정 | 리뷰 `rating`을 백엔드 정수 계약에 맞춤 | `8c96649` | [#20](https://github.com/dodohy25n/lovemaptually/pull/20) |
| 지도·추억 저장소 반응형 수정 | 낮은 화면에서 UI 잘림 방지 | `a4be222`, `93a14f8`, `2825c71` | [#21](https://github.com/dodohy25n/lovemaptually/pull/21) |
| 그룹 장소 목록 | `GET /api/groups/{groupId}/places` | `a0b3b1b` | [#22](https://github.com/dodohy25n/lovemaptually/pull/22) |
| 그룹 장소 저장 | `POST /api/groups/{groupId}/places` | `2c20c13` | [#23](https://github.com/dodohy25n/lovemaptually/pull/23) |
| 그룹 지도 핀 상세 | `GET /api/groups/{groupId}/places/{placeId}` | `1cbf6dc` | [#24](https://github.com/dodohy25n/lovemaptually/pull/24) |
| 그룹 장소 리뷰 목록 | `GET /api/groups/{groupId}/places/{placeId}/reviews` | `f8c5c9e` | [#25](https://github.com/dodohy25n/lovemaptually/pull/25) |
| 리뷰 상세 조회 | `GET /api/reviews/{reviewId}` | `0d628fd` | [#26](https://github.com/dodohy25n/lovemaptually/pull/26) |
| 그룹 취향 조회 | `GET /api/groups/{groupId}/preferences` | `6b42c04` | [#27](https://github.com/dodohy25n/lovemaptually/pull/27) |
| AI 추천 요청 생성 | `POST /api/groups/{groupId}/recommendation-requests` | `7783e3d` | [#28](https://github.com/dodohy25n/lovemaptually/pull/28) |

각 API 브랜치에서는 해당 시점의 단위 테스트와 프로덕션 빌드를 통과했다. 모든 PR은 frontend만 변경했으며 임의 병합하지 않았다.

## 아직 구현하지 못한 API

| 우선순위 | 기능 | API | 필요한 작업 |
|---:|---|---|---|
| 1 | AI 추천 결과 조회 | `GET /api/recommendation-requests/{requestId}` | `PENDING` 폴링, 완료·실패·빈 결과를 추천 모달에 표시 |
| 2 | 초대 코드 확인 | `GET /api/invites/{code}` | 코드 유효성·만료·그룹 미리보기 응답 정규화 및 화면 연결 |
| 3 | 초대 코드로 그룹 참여 | `POST /api/groups/members` | 참여 요청, 중복 참여·만료 오류 처리 및 완료 후 그룹 상태 갱신 |

## 통합 후 남는 검증 작업

1. 열린 PR은 모두 `main`에서 독립적으로 분기했으므로 겹치는 `MapView.vue`, `placeRepository.js`, `reviewApi.js` 변경을 순서대로 병합하고 충돌을 해결해야 한다.
2. API PR 통합 후 `VITE_DATA_MODE=api`로 전환하여 로그인 → 그룹 조회 → 장소 조회·저장 → 리뷰 → 취향 → 추천 전체 흐름을 재검증해야 한다.
3. Postman Mock Server에 각 경로의 예시 응답이 실제로 등록되어 있는지 확인해야 한다. 코드 계약은 현재 백엔드 DTO를 기준으로 작성했다.
4. 그룹 장소 목록 응답에는 방문일이 없으므로 추억 저장소의 월별 분류에는 핀 상세 또는 리뷰 목록의 방문일을 합치는 통합 작업이 필요하다.
5. 장소 수정·삭제와 리뷰 수정·삭제는 현재 백엔드 명세에 대응 엔드포인트가 없어 실제 API 연동을 완료할 수 없다.
6. 지도 화면 전환 시 Leaflet 지연 애니메이션 오류 수정은 원격 `fix/memories-map-cleanup` 브랜치의 커밋 `7b7c5e6`에 있으나 아직 PR이 생성되지 않았다.

## 병합 시 권장 순서

충돌과 의존성을 줄이기 위한 권장 순서다.

1. #19 → #20 → #21
2. #22 → #23 → #24
3. #25 → #26
4. #27 → #28
5. 남은 API 3개
6. 통합 브랜치에서 전체 테스트·빌드·브라우저 검증

