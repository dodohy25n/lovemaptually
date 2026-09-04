#!/usr/bin/env bash
# 데모 스택을 내립니다. DB는 건드리지 않습니다.
set -uo pipefail

pkill -f LoveMaptuallyApplication 2>/dev/null && echo "백엔드를 내렸습니다" || echo "백엔드는 떠 있지 않았습니다"
pkill -f "uvicorn app:app" 2>/dev/null && echo "추천 엔진을 내렸습니다" || echo "추천 엔진은 떠 있지 않았습니다"
pkill -f "vite" 2>/dev/null && echo "프론트를 내렸습니다" || echo "프론트는 떠 있지 않았습니다"
exit 0
