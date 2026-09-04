#!/usr/bin/env bash
# 데모 시드 한 번에 넣기. 몇 번을 다시 돌려도 같은 상태가 됩니다.
#   PG_URL        기본 postgresql://lovemaptually:lovemaptually@localhost:5432/lovemaptually
#   API_BASE_URL  기본 http://localhost:8080
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PG_URL="${PG_URL:-postgresql://lovemaptually:lovemaptually@localhost:5432/lovemaptually}"
export API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"

echo "[1/5] 도메인 테이블 비우기 (tags 는 Flyway 시드라 남깁니다)"
psql -X -v ON_ERROR_STOP=1 -q "$PG_URL" <<'SQL'
TRUNCATE TABLE
  recommendations, recommendation_requests, place_similarity, unmatched_tag_logs,
  user_tags, place_tags, review_tags, reviews, group_places, monthly_reports,
  subscriptions, invite_codes, group_members, relation_groups, places, users
RESTART IDENTITY CASCADE;
SQL

echo "[2/5] 백엔드 대기: $API_BASE_URL/v3/api-docs"
for i in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$API_BASE_URL/v3/api-docs" || true)" = "200" ]; then
    echo "  백엔드 응답 확인 (${i}s)"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "  백엔드가 60초 안에 뜨지 않았습니다. $API_BASE_URL 에서 Spring Boot 가 실행 중인지 확인하세요." >&2
    exit 1
  fi
  sleep 1
done

echo "[3/5] 시드 데이터 생성"
python3 "$SCRIPT_DIR/seed/generate_seed.py"

echo "[4/5] API 로 적재"
python3 "$SCRIPT_DIR/seed/load_seed.py"

echo "[5/5] 장소 유사도 배치"
python3 "$SCRIPT_DIR/seed/batch_similarity.py"

echo "seed complete"
