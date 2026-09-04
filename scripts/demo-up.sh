#!/usr/bin/env bash
# 데모 스택을 한 번에 띄웁니다. 로그는 .demo-logs/ 아래에 쌓입니다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGS="$ROOT/.demo-logs"
PG_URL="${PG_URL:-postgresql://lovemaptually:lovemaptually@localhost:5432/lovemaptually}"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
RECOMMENDER_URL="${RECOMMENDER_URL:-http://localhost:8000}"
mkdir -p "$LOGS"

echo "[1/4] DB 확인"
psql "$PG_URL" -c 'select 1' > /dev/null
echo "  ok $PG_URL"

echo "[2/4] 백엔드"
if curl -sf "$API_BASE_URL/v3/api-docs" > /dev/null 2>&1; then
  echo "  이미 떠 있습니다"
else
  if [ -f "$ROOT/backend/.env" ]; then
    set -a; source "$ROOT/backend/.env"; set +a
  fi
  export REPORT_WRITER="${REPORT_WRITER:-openai}"
  (cd "$ROOT/backend" && nohup ./gradlew bootRun --console=plain > "$LOGS/backend.log" 2>&1 &)
  for _ in $(seq 1 90); do
    sleep 2
    curl -sf "$API_BASE_URL/v3/api-docs" > /dev/null 2>&1 && break
  done
  curl -sf "$API_BASE_URL/v3/api-docs" > /dev/null 2>&1 || {
    echo "  백엔드가 뜨지 않았습니다. $LOGS/backend.log 를 보십시오"; exit 1; }
  echo "  ok $API_BASE_URL"
fi

echo "[3/4] 추천 엔진"
if curl -sf "$RECOMMENDER_URL/health" > /dev/null 2>&1; then
  echo "  이미 떠 있습니다"
else
  (cd "$ROOT/recommender" && nohup uv run uvicorn app:app --port 8000 > "$LOGS/recommender.log" 2>&1 &)
  for _ in $(seq 1 30); do
    sleep 1
    curl -sf "$RECOMMENDER_URL/health" > /dev/null 2>&1 && break
  done
  if curl -sf "$RECOMMENDER_URL/health" > /dev/null 2>&1; then
    echo "  ok $RECOMMENDER_URL"
  else
    echo "  엔진이 뜨지 않았습니다. 규칙 폴백으로 추천이 나갑니다"
  fi
fi

echo "[4/4] 프론트"
if curl -sf http://localhost:5173 > /dev/null 2>&1; then
  echo "  이미 떠 있습니다"
else
  (cd "$ROOT/frontend" && nohup npm run dev > "$LOGS/frontend.log" 2>&1 &)
  for _ in $(seq 1 30); do
    sleep 1
    curl -sf http://localhost:5173 > /dev/null 2>&1 && break
  done
  echo "  http://localhost:5173"
fi

echo
echo "데모 준비 완료. 시드를 새로 넣으려면 scripts/seed-demo.sh 를 실행하십시오."
