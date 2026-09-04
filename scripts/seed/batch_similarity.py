"""장소 유사도 새벽 배치 (설계문서 흐름 3).

reviews 를 전부 읽어 사용자 x 장소 평점 행렬을 만들고, 함께 평가한 사람이 2명 이상인 장소 쌍마다
피어슨 상관계수를 구해 place_similarity 를 통째로 다시 씁니다. DB 접근은 psql CLI 로만 합니다.
"""

import math
import os
import subprocess
import sys

import numpy as np

DEFAULT_PG_URL = "postgresql://lovemaptually:lovemaptually@localhost:5432/lovemaptually"


def pearson_pairs(ratings, min_co_raters=2):
    """{(user, place): rating} -> {(place1, place2): score}. place1 < place2 한 방향만 돌려줍니다.

    두 장소를 모두 평가한 사람들의 평점만 골라, 장소별로 그 사람들에 대한 평균을 뺀 뒤 상관계수를 구합니다.
    공동 평가자가 min_co_raters 미만이거나 분산이 0 이면 제외합니다.
    """
    by_place = {}
    for (user, place), rating in ratings.items():
        by_place.setdefault(place, {})[user] = float(rating)
    places = sorted(by_place)
    result = {}
    for i, p1 in enumerate(places):
        raters1 = by_place[p1]
        for p2 in places[i + 1:]:
            raters2 = by_place[p2]
            common = sorted(set(raters1) & set(raters2))
            if len(common) < min_co_raters:
                continue
            x = np.array([raters1[u] for u in common])
            y = np.array([raters2[u] for u in common])
            xc = x - x.mean()
            yc = y - y.mean()
            denom = math.sqrt(float((xc * xc).sum()) * float((yc * yc).sum()))
            if denom == 0.0:
                continue
            score = float(xc @ yc) / denom
            if math.isnan(score):
                continue
            result[(p1, p2)] = max(-1.0, min(1.0, round(score, 4)))
    return result


def run_psql(pg_url, sql, capture=True):
    cmd = ["psql", "-X", "-v", "ON_ERROR_STOP=1", "-q", "-A", "-t", "-F", ",", pg_url]
    proc = subprocess.run(cmd, input=sql, text=True, capture_output=True)
    if proc.returncode != 0:
        print(proc.stderr, file=sys.stderr)
        raise SystemExit(f"psql 실패 (exit {proc.returncode})")
    return proc.stdout if capture else ""


def read_ratings(pg_url):
    out = run_psql(pg_url, "SELECT user_id, place_id, rating FROM reviews;")
    collected = {}
    for line in out.splitlines():
        line = line.strip()
        if not line:
            continue
        user_id, place_id, rating = line.split(",")
        collected.setdefault((int(user_id), int(place_id)), []).append(int(rating))
    # 같은 장소를 여러 번 간 사람은 평균 평점 하나로 봅니다
    return {key: sum(values) / len(values) for key, values in collected.items()}


def write_similarity(pg_url, pairs):
    rows = []
    for (p1, p2), score in sorted(pairs.items()):
        rows.append(f"({p1}, {p2}, {score:.4f})")
        rows.append(f"({p2}, {p1}, {score:.4f})")
    sql = ["BEGIN;", "DELETE FROM place_similarity;"]
    if rows:
        sql.append("INSERT INTO place_similarity (place_id, similar_place_id, score) VALUES\n" + ",\n".join(rows) + ";")
    sql.append("COMMIT;")
    run_psql(pg_url, "\n".join(sql), capture=False)
    return len(rows)


def main():
    pg_url = os.environ.get("PG_URL", DEFAULT_PG_URL)
    ratings = read_ratings(pg_url)
    users = {u for u, _ in ratings}
    places = {p for _, p in ratings}
    print(f"reviews={len(ratings)} users={len(users)} places={len(places)}")

    pairs = pearson_pairs(ratings, min_co_raters=2)
    inserted = write_similarity(pg_url, pairs)
    if pairs:
        scores = np.array(list(pairs.values()))
        print(f"pairs={len(pairs)} rows={inserted} mean={scores.mean():.4f} min={scores.min():.4f} max={scores.max():.4f}")
    else:
        print("pairs=0 (공동 평가자가 2명 이상인 장소 쌍이 없습니다)")


if __name__ == "__main__":
    main()
