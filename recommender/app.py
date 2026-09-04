"""추천 엔진 FastAPI 서비스입니다.

설계 문서 흐름 2의 STEP 2 ~ STEP 10을 그대로 구현합니다. 순위 계산은 전부
scoring.py의 순수 함수가 맡고, 이 파일은 DB에서 재료를 모아 넘기고 응답을 조립합니다.
"""

from __future__ import annotations

import os
from collections import defaultdict
from dataclasses import dataclass

import psycopg
from fastapi import FastAPI, Query
from pydantic import BaseModel, Field

from query_parser import parse as parse_query
from scoring import (
    CONSENSUS_MIN,
    DEFAULT_CF_RAMP_FULL,
    HIGH,
    LOW,
    REGION_OPEN_THRESHOLD,
    Neighbour,
    ReasonContext,
    ReasonWriter,
    SplitTag,
    TemplateReasonWriter,
    cf_predict,
    cf_weight,
    combine_scores,
    group_consensus,
    place_tag_side,
    tag_direction_score,
    tag_label_phrase,
)

PG_URL = os.getenv(
    "PG_URL", "postgresql://lovemaptually:lovemaptually@localhost:5432/lovemaptually"
)

NOTICE_REGION_CLOSED = "이 지역은 아직 추천이 열리지 않았습니다"
NOTICE_FALLBACK = "모두가 좋아할 만한 곳이 없어 평균 순으로 채웠습니다"

app = FastAPI(title="lovemaptually recommender", version="1.0.0")

reason_writer: ReasonWriter = TemplateReasonWriter()


def cf_ramp_full() -> int:
    """운영 중에 바꿀 수 있도록 요청 시점에 환경변수를 읽습니다."""
    raw = os.getenv("CF_RAMP_FULL")
    if not raw:
        return DEFAULT_CF_RAMP_FULL
    try:
        value = int(raw)
    except ValueError:
        return DEFAULT_CF_RAMP_FULL
    return value if value > 0 else DEFAULT_CF_RAMP_FULL


# ---------------------------------------------------------------------------
# 요청, 응답 스키마
# ---------------------------------------------------------------------------


class RecommendationRequest(BaseModel):
    groupId: int
    memberIds: list[int]
    region: str
    count: int = 3
    budget: int | None = None


class RecommendationItem(BaseModel):
    placeId: int
    name: str
    category: str
    priceBand: int | None
    latitude: float
    longitude: float
    matchedTags: list[str]
    basis: str
    reason: str
    displayOrder: int


class RecommendationResponse(BaseModel):
    region: str
    candidateCount: int
    cfWeight: float
    degraded: bool = False
    recommendations: list[RecommendationItem] = Field(default_factory=list)
    notice: str | None = None


class ParseResponse(BaseModel):
    region: str | None
    count: int
    budget: int | None


# ---------------------------------------------------------------------------
# DB 조회
# ---------------------------------------------------------------------------


@dataclass
class Candidate:
    place_id: int
    name: str
    category: str
    price_band: int | None
    latitude: float
    longitude: float


CANDIDATE_SQL = """
SELECT p.place_id, p.name, p.category, p.price_band, p.latitude, p.longitude
FROM places p
WHERE p.region = %(region)s
  AND EXISTS (SELECT 1 FROM place_tags pt WHERE pt.place_id = p.place_id)
  AND NOT EXISTS (
        SELECT 1 FROM group_places gp
        WHERE gp.group_id = %(group_id)s AND gp.place_id = p.place_id
          AND gp.label = 'ON_HOLD'
      )
  AND NOT EXISTS (
        SELECT 1 FROM reviews r
        WHERE r.place_id = p.place_id AND r.user_id = ANY(%(member_ids)s)
      )
  AND (
        %(budget)s::smallint IS NULL
        OR (p.price_band IS NOT NULL AND p.price_band <= %(budget)s::smallint)
      )
ORDER BY p.place_id
"""


def fetch_candidates(
    cur, region: str, group_id: int, member_ids: list[int], budget: int | None
) -> list[Candidate]:
    cur.execute(
        CANDIDATE_SQL,
        {
            "region": region,
            "group_id": group_id,
            "member_ids": member_ids,
            "budget": budget,
        },
    )
    return [
        Candidate(
            place_id=row[0],
            name=row[1],
            category=row[2],
            price_band=row[3],
            latitude=float(row[4]),
            longitude=float(row[5]),
        )
        for row in cur.fetchall()
    ]


def fetch_tag_dictionary(cur) -> dict[int, tuple[str, str, str]]:
    cur.execute("SELECT tag_id, name, high_label, low_label FROM tags")
    return {row[0]: (row[1], row[2], row[3]) for row in cur.fetchall()}


def fetch_place_facts(cur, place_ids: list[int]) -> dict[int, dict[int, tuple[int, int]]]:
    cur.execute(
        """
        SELECT place_id, tag_id, fact_high_count, fact_low_count
        FROM place_tags WHERE place_id = ANY(%s)
        """,
        (place_ids,),
    )
    facts: dict[int, dict[int, tuple[int, int]]] = defaultdict(dict)
    for place_id, tag_id, high, low in cur.fetchall():
        facts[place_id][tag_id] = (high, low)
    return facts


def fetch_member_wants(cur, member_ids: list[int]) -> dict[int, dict[int, tuple[int, int]]]:
    cur.execute(
        """
        SELECT user_id, tag_id, want_high_count, want_low_count
        FROM user_tags WHERE user_id = ANY(%s)
        """,
        (member_ids,),
    )
    wants: dict[int, dict[int, tuple[int, int]]] = {mid: {} for mid in member_ids}
    for user_id, tag_id, high, low in cur.fetchall():
        wants.setdefault(user_id, {})[tag_id] = (high, low)
    return wants


def fetch_member_ratings(cur, member_ids: list[int]) -> dict[int, dict[int, float]]:
    cur.execute(
        "SELECT user_id, place_id, avg(rating)::float FROM reviews "
        "WHERE user_id = ANY(%s) GROUP BY user_id, place_id",
        (member_ids,),
    )
    ratings: dict[int, dict[int, float]] = {mid: {} for mid in member_ids}
    for user_id, place_id, rating in cur.fetchall():
        ratings.setdefault(user_id, {})[place_id] = float(rating)
    return ratings


def fetch_review_counts(cur, member_ids: list[int]) -> dict[int, int]:
    cur.execute(
        "SELECT user_id, count(*) FROM reviews WHERE user_id = ANY(%s) GROUP BY user_id",
        (member_ids,),
    )
    counts = {mid: 0 for mid in member_ids}
    for user_id, count in cur.fetchall():
        counts[user_id] = int(count)
    return counts


def fetch_place_means(cur, place_ids: list[int]) -> dict[int, float]:
    if not place_ids:
        return {}
    cur.execute(
        "SELECT place_id, avg(rating)::float FROM reviews WHERE place_id = ANY(%s) GROUP BY place_id",
        (place_ids,),
    )
    return {row[0]: float(row[1]) for row in cur.fetchall()}


def fetch_similarity(cur, place_ids: list[int]) -> dict[int, list[tuple[int, float]]]:
    """후보 장소별 유사 장소입니다. 배치가 아직 안 돌았으면 빈 딕셔너리가 됩니다."""
    cur.execute(
        "SELECT place_id, similar_place_id, score FROM place_similarity WHERE place_id = ANY(%s)",
        (place_ids,),
    )
    similarity: dict[int, list[tuple[int, float]]] = defaultdict(list)
    for place_id, similar_place_id, score in cur.fetchall():
        similarity[place_id].append((similar_place_id, float(score)))
    return similarity


def fetch_nicknames(cur, member_ids: list[int]) -> dict[int, str]:
    cur.execute("SELECT user_id, nickname FROM users WHERE user_id = ANY(%s)", (member_ids,))
    return {row[0]: row[1] for row in cur.fetchall()}


# ---------------------------------------------------------------------------
# 파이프라인
# ---------------------------------------------------------------------------


def build_recommendations(cur, request: RecommendationRequest) -> RecommendationResponse:
    member_ids = list(dict.fromkeys(request.memberIds))
    count = max(1, request.count or 3)

    # STEP 2 후보 수집
    candidates = fetch_candidates(
        cur, request.region, request.groupId, member_ids, request.budget
    )
    candidate_count = len(candidates)

    # STEP 5 n은 그룹에서 가장 리뷰가 적은 사람 기준입니다. 한 명이라도 처음이면 CF를 믿지 않습니다.
    review_counts = fetch_review_counts(cur, member_ids)
    n = min(review_counts.values()) if review_counts else 0
    w_cf = cf_weight(n, cf_ramp_full())

    # STEP 3 지역 게이트
    if candidate_count < REGION_OPEN_THRESHOLD:
        return RecommendationResponse(
            region=request.region,
            candidateCount=candidate_count,
            cfWeight=round(w_cf, 4),
            degraded=False,
            recommendations=[],
            notice=NOTICE_REGION_CLOSED,
        )

    place_ids = [c.place_id for c in candidates]
    tag_dictionary = fetch_tag_dictionary(cur)
    place_facts = fetch_place_facts(cur, place_ids)
    member_wants = fetch_member_wants(cur, member_ids)
    member_ratings = fetch_member_ratings(cur, member_ids)
    similarity = fetch_similarity(cur, place_ids)

    rated_place_ids = sorted({pid for r in member_ratings.values() for pid in r})
    place_means = fetch_place_means(cur, rated_place_ids)

    member_means = {
        mid: (sum(r.values()) / len(r) if r else 3.0) for mid, r in member_ratings.items()
    }

    final_scores: dict[int, list[float]] = {}
    matched_by_place: dict[int, set[int]] = defaultdict(set)
    sides_by_place: dict[int, dict[int, dict[int, str]]] = defaultdict(dict)
    own_basis: dict[int, bool] = {}
    any_cf = False

    # STEP 4 ~ 6 멤버별 점수 계산
    for candidate in candidates:
        pid = candidate.place_id
        facts = place_facts.get(pid, {})
        neighbours_pool = similarity.get(pid, [])
        scores: list[float] = []
        used_own_rating = False

        for member_id in member_ids:
            # STEP 4b 태그 방향 일치율
            tag_result = tag_direction_score(member_wants.get(member_id, {}), facts)
            matched_by_place[pid].update(tag_result.matched_tag_ids)
            sides_by_place[pid][member_id] = {
                tag_id: side
                for tag_id, side in tag_result.member_sides.items()
                if tag_id in facts and place_tag_side(*facts[tag_id]) is not None
            }

            # STEP 4a CF 예측
            ratings = member_ratings.get(member_id, {})
            neighbours = [
                Neighbour(
                    place_id=similar_id,
                    similarity=score,
                    rating=ratings[similar_id],
                    place_mean=place_means.get(similar_id, ratings[similar_id]),
                )
                for similar_id, score in neighbours_pool
                if similar_id in ratings
            ]
            prediction = cf_predict(member_means.get(member_id, 3.0), neighbours)
            if prediction is not None:
                any_cf = True
                used_own_rating = True

            # STEP 6 가중 결합
            scores.append(combine_scores(prediction, tag_result.score, w_cf))

        final_scores[pid] = scores
        own_basis[pid] = used_own_rating

    # STEP 7 그룹 합의
    consensus = group_consensus(final_scores, CONSENSUS_MIN)

    # STEP 8 상위 count개
    top_ids = consensus.ranked_place_ids[:count]
    candidate_map = {c.place_id: c for c in candidates}
    nicknames = fetch_nicknames(cur, member_ids)

    # STEP 9 이유 문장
    items: list[RecommendationItem] = []
    for order, pid in enumerate(top_ids, start=1):
        candidate = candidate_map[pid]
        matched_ids = sorted(matched_by_place[pid])
        matched_names = [tag_dictionary[t][0] for t in matched_ids if t in tag_dictionary]
        matched_labels = [
            _matched_label(tag_dictionary, t, place_facts.get(pid, {}).get(t, (0, 0)))
            for t in matched_ids
            if t in tag_dictionary
        ]
        context = ReasonContext(
            place_id=pid,
            place_name=candidate.name,
            matched_labels=matched_labels,
            split_tags=_split_tags(tag_dictionary, sides_by_place[pid], nicknames, matched_ids),
            cf_weight=w_cf,
            basis="OWN" if own_basis[pid] else "OTHERS",
        )
        items.append(
            RecommendationItem(
                placeId=pid,
                name=candidate.name,
                category=candidate.category,
                priceBand=candidate.price_band,
                latitude=candidate.latitude,
                longitude=candidate.longitude,
                matchedTags=matched_names,
                basis=context.basis,
                reason=reason_writer.write(context),
                displayOrder=order,
            )
        )

    notice = NOTICE_FALLBACK if consensus.fallback else None
    # CF를 쓸 만큼 리뷰가 쌓였는데 유사도 데이터가 하나도 없으면 태그만으로 내려간 상태입니다.
    degraded = w_cf > 0 and not any_cf

    return RecommendationResponse(
        region=request.region,
        candidateCount=candidate_count,
        cfWeight=round(w_cf, 4),
        degraded=degraded,
        recommendations=items,
        notice=notice,
    )


def _matched_label(
    tag_dictionary: dict[int, tuple[str, str, str]],
    tag_id: int,
    fact: tuple[int, int],
) -> str:
    name, high_label, low_label = tag_dictionary[tag_id]
    label = high_label if place_tag_side(*fact) == HIGH else low_label
    return tag_label_phrase(name, label)


def _split_tags(
    tag_dictionary: dict[int, tuple[str, str, str]],
    sides: dict[int, dict[int, str]],
    nicknames: dict[int, str],
    matched_ids: list[int],
) -> list[SplitTag]:
    """한 명은 HIGH, 다른 한 명은 LOW로 판정한 태그를 골라냅니다."""
    result: list[SplitTag] = []
    for tag_id in matched_ids:
        if tag_id not in tag_dictionary:
            continue
        high_members = [
            nicknames.get(mid, f"멤버{mid}")
            for mid, member_sides in sides.items()
            if member_sides.get(tag_id) == HIGH
        ]
        low_members = [
            nicknames.get(mid, f"멤버{mid}")
            for mid, member_sides in sides.items()
            if member_sides.get(tag_id) == LOW
        ]
        if high_members and low_members:
            name, high_label, low_label = tag_dictionary[tag_id]
            result.append(
                SplitTag(
                    tag_name=name,
                    high_label=high_label,
                    low_label=low_label,
                    high_members=high_members,
                    low_members=low_members,
                )
            )
    return result


# ---------------------------------------------------------------------------
# 엔드포인트
# ---------------------------------------------------------------------------


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "cfRampFull": cf_ramp_full()}


@app.get("/parse", response_model=ParseResponse)
def parse_endpoint(query: str = Query(default="")) -> ParseResponse:
    return ParseResponse(**parse_query(query))


@app.post("/recommendations", response_model=RecommendationResponse)
def recommendations(request: RecommendationRequest) -> RecommendationResponse:
    with psycopg.connect(PG_URL) as conn:
        with conn.cursor() as cur:
            return build_recommendations(cur, request)
