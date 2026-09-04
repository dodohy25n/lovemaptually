#!/usr/bin/env bash
# 명세의 (엔드포인트 x 상태 코드) 조합을 실제로 때려 기대와 실제를 표로 냅니다.
# 백엔드가 떠 있어야 하고, 데모 시드가 들어간 상태를 전제합니다.
set -uo pipefail

API="${API_BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0

login() {
  curl -s -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"demo1234!\"}" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("accessToken",""))'
}

check() {
  local label="$1" expected="$2" method="$3" path="$4" token="${5:-}" body="${6:-}"
  local args=(-s -o /tmp/cc-body.json -w '%{http_code}' -X "$method" "$API$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  local actual
  actual=$(curl "${args[@]}")
  if [ "$actual" = "$expected" ]; then
    printf '  %-46s %-4s %-4s 일치\n' "$label" "$expected" "$actual"
    PASS=$((PASS + 1))
  else
    printf '  %-46s %-4s %-4s 불일치\n' "$label" "$expected" "$actual"
    FAIL=$((FAIL + 1))
  fi
}

echo "계약 점검 $API"
echo

TOKEN=$(login dohyeon@lovemap.dev)
OTHER=$(login yongmin@lovemap.dev)
# 데모 리허설이 couple01a 를 PREMIUM 으로 올렸을 수 있어 아직 FREE 인 계정을 찾습니다.
FREE=""
FREE_GROUP=""
for account in couple01a couple02a couple03a couple04a; do
  candidate=$(login "$account@lovemap.dev")
  [ -z "$candidate" ] && continue
  group=$(curl -s "$API/api/groups/me" -H "Authorization: Bearer $candidate" \
    | python3 -c 'import sys,json;g=json.load(sys.stdin)["data"]["groups"];print(g[0]["groupId"] if g else "")')
  [ -z "$group" ] && continue
  plan=$(curl -s "$API/api/groups/$group/reports" -H "Authorization: Bearer $candidate" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["plan"])')
  if [ "$plan" = "FREE" ]; then
    FREE="$candidate"
    FREE_GROUP="$group"
    break
  fi
done
if [ -z "$TOKEN" ]; then
  echo "데모 계정으로 로그인하지 못했습니다. scripts/seed-demo.sh 를 먼저 실행하십시오."
  exit 1
fi

GROUP=$(curl -s "$API/api/groups/me" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["groups"][0]["groupId"])')
PLACE=$(curl -s "$API/api/groups/$GROUP/places" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["markers"][0]["placeId"])')
# 중복 리뷰 409 는 이미 있는 방문 날짜로 때려야 나옵니다. 없는 날짜로 보내면 새 리뷰가 생깁니다.
VISITED=$(curl -s "$API/api/groups/$GROUP/places/$PLACE/reviews" -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print((d.get("myReview") or {}).get("visitedOn",""))')

echo "인증"
check "회원가입 이메일 중복" 409 POST /api/auth/signup "" '{"email":"dohyeon@lovemap.dev","password":"demo1234!","nickname":"중복"}'
check "로그인 성공" 200 POST /api/auth/login "" '{"email":"dohyeon@lovemap.dev","password":"demo1234!"}'
check "로그인 비밀번호 오류" 401 POST /api/auth/login "" '{"email":"dohyeon@lovemap.dev","password":"wrong-password"}'
check "입력값 오류" 400 POST /api/auth/signup "" '{"email":"bad","password":"x","nickname":""}'

echo "그룹과 초대"
check "내 그룹 목록" 200 GET /api/groups/me "$TOKEN"
check "토큰 없이 조회" 401 GET /api/groups/me
check "이미 커플 그룹 소속" 409 POST /api/groups "$TOKEN" '{"groupType":"COUPLE"}'
check "없는 초대 코드" 404 GET /api/invites/NOPE-CODE

echo "장소와 우리 지도"
check "장소 검색" 200 GET "/api/places?query=%EC%B0%BB%EC%A7%91" "$TOKEN"
check "검색어 누락" 400 GET /api/places "$TOKEN"
check "없는 장소 상세" 404 GET /api/places/99999 "$TOKEN"
check "우리 지도 조회" 200 GET "/api/groups/$GROUP/places" "$TOKEN"
check "이미 담은 장소" 409 POST "/api/groups/$GROUP/places" "$TOKEN" "{\"placeId\":$PLACE}"
check "내 그룹 아님" 403 GET "/api/groups/$GROUP/places" "$FREE"

echo "리뷰"
if [ -z "$VISITED" ]; then
  echo "  데모 계정에 리뷰가 없어 중복 검사를 건너뜁니다. seed-demo.sh 를 먼저 실행하십시오."
fi
check "같은 날 같은 장소 중복" 409 POST /api/reviews "$TOKEN" "{\"placeId\":$PLACE,\"withGroupId\":$GROUP,\"visitedOn\":\"$VISITED\",\"rating\":4,\"content\":\"조용해서 좋았어요\"}"
check "별점 범위 밖" 422 POST /api/reviews "$TOKEN" "{\"placeId\":$PLACE,\"withGroupId\":$GROUP,\"visitedOn\":\"2026-12-25\",\"rating\":9,\"content\":\"조용해서 좋았어요\"}"
check "없는 장소" 404 POST /api/reviews "$TOKEN" "{\"placeId\":99999,\"withGroupId\":$GROUP,\"visitedOn\":\"2026-12-25\",\"rating\":4,\"content\":\"조용해서 좋았어요\"}"
check "내 그룹 아닌 곳에 리뷰" 403 POST /api/reviews "$FREE" "{\"placeId\":$PLACE,\"withGroupId\":$GROUP,\"visitedOn\":\"2026-12-25\",\"rating\":4,\"content\":\"조용해서 좋았어요\"}"
check "장소의 그룹 리뷰" 200 GET "/api/groups/$GROUP/places/$PLACE/reviews" "$TOKEN"
check "없는 리뷰" 404 GET /api/reviews/999999 "$TOKEN"

echo "우리 취향"
check "그룹 취향 조회" 200 GET "/api/groups/$GROUP/preferences" "$TOKEN"
check "내 그룹 아님" 403 GET "/api/groups/$GROUP/preferences" "$FREE"

echo "추천"
check "지역 해석 실패" 422 POST "/api/groups/$GROUP/recommendation-requests" "$TOKEN" '{"query":"세 곳 추천해줘"}'
check "요청 접수" 202 POST "/api/groups/$GROUP/recommendation-requests" "$TOKEN" '{"query":"오늘 인사동 갈 건데 3곳 추천해줘"}'
check "없는 요청" 404 GET /api/recommendation-requests/999999 "$TOKEN"

echo "월간 리포트"
check "FREE 그룹 생성 요청" 402 POST "/api/groups/$FREE_GROUP/reports" "$FREE" '{"month":"2026-08"}'
check "그 달 기록 0건" 422 POST "/api/groups/$GROUP/reports" "$TOKEN" '{"month":"2026-01"}'
check "월 형식 오류" 400 POST "/api/groups/$GROUP/reports" "$TOKEN" '{"month":"2026년 8월"}'
check "이미 PREMIUM" 409 POST "/api/groups/$GROUP/subscriptions" "$TOKEN" '{"plan":"PREMIUM"}'
check "리포트 목록" 200 GET "/api/groups/$GROUP/reports" "$TOKEN"
check "없는 리포트" 404 GET /api/reports/999999 "$TOKEN"

echo
echo "일치 $PASS / 불일치 $FAIL"
[ "$FAIL" -eq 0 ]
