"""추천 점수 계산의 순수 함수 모음입니다.

DB 접근은 전혀 하지 않습니다. app.py가 DB에서 재료를 긁어와 이 모듈의 함수에 넘기고,
테스트는 DB 없이 이 모듈만 직접 호출합니다.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable, Mapping, Protocol, Sequence

# 지역이 열리는 최소 후보 수입니다. 이보다 적으면 추천을 내보내지 않습니다.
REGION_OPEN_THRESHOLD = 3

# 그룹 합의 하한선입니다. 1..5 예상 평점 척도에서 "중간보다는 낫다"의 기준입니다.
CONSENSUS_MIN = 2.5

# CF 가중치가 1이 되는 리뷰 수입니다. 환경변수 CF_RAMP_FULL로 덮어씁니다.
DEFAULT_CF_RAMP_FULL = 20

# 방향 판정에 필요한 최소 투표 수입니다. 1표짜리 취향은 우연일 수 있어 제외합니다.
MIN_VOTES_FOR_JUDGEMENT = 2

HIGH = "HIGH"
LOW = "LOW"


# ---------------------------------------------------------------------------
# STEP 4b 태그 방향 일치율
# ---------------------------------------------------------------------------


def member_tag_side(want_high: int, want_low: int) -> str | None:
    """사람 쪽 태그 방향입니다. 2표 이상이면서 한쪽이 더 많을 때만 판정합니다."""
    if want_high >= MIN_VOTES_FOR_JUDGEMENT and want_high > want_low:
        return HIGH
    if want_low >= MIN_VOTES_FOR_JUDGEMENT and want_low > want_high:
        return LOW
    return None


def place_tag_side(fact_high: int, fact_low: int) -> str | None:
    """장소 쪽 태그 방향입니다. 장소는 1인 1표라 표 수 제한 없이 많은 쪽을 씁니다."""
    if fact_high > fact_low:
        return HIGH
    if fact_low > fact_high:
        return LOW
    return None


@dataclass
class TagScore:
    score: float
    matched_tag_ids: list[int]
    judged_tag_ids: list[int]
    member_sides: dict[int, str]


def tag_direction_score(
    member_wants: Mapping[int, tuple[int, int]],
    place_facts: Mapping[int, tuple[int, int]],
) -> TagScore:
    """양쪽 모두 방향이 판정된 태그만 분모에 넣고, 방향이 같은 비율을 돌려줍니다.

    분모가 0이면 0.5를 씁니다. 판정할 근거가 없는 상태를 0점으로 두면 신규 사용자와
    취향이 정말 안 맞는 장소가 구분되지 않아, 중립값으로 두고 CF 쪽에 판단을 맡깁니다.
    """
    matched: list[int] = []
    judged: list[int] = []
    sides: dict[int, str] = {}

    for tag_id, (want_high, want_low) in member_wants.items():
        member_side = member_tag_side(want_high, want_low)
        if member_side is None:
            continue
        sides[tag_id] = member_side
        fact = place_facts.get(tag_id)
        if fact is None:
            continue
        place_side = place_tag_side(fact[0], fact[1])
        if place_side is None:
            continue
        judged.append(tag_id)
        if place_side == member_side:
            matched.append(tag_id)

    score = len(matched) / len(judged) if judged else 0.5
    return TagScore(
        score=score,
        matched_tag_ids=sorted(matched),
        judged_tag_ids=sorted(judged),
        member_sides=sides,
    )


# ---------------------------------------------------------------------------
# STEP 4a CF 예측
# ---------------------------------------------------------------------------


@dataclass
class Neighbour:
    """후보 장소와 유사한, 그리고 해당 멤버가 이미 평가한 장소입니다."""

    place_id: int
    similarity: float
    rating: float
    place_mean: float


def cf_predict(
    member_mean: float,
    neighbours: Sequence[Neighbour],
) -> float | None:
    """아이템 기반 CF 예측입니다. 쓸 이웃이 없으면 None(= CF 없음)을 돌려줍니다."""
    denominator = sum(abs(n.similarity) for n in neighbours)
    if not neighbours or denominator == 0:
        return None
    numerator = sum(n.similarity * (n.rating - n.place_mean) for n in neighbours)
    return clamp_rating(member_mean + numerator / denominator)


def clamp_rating(value: float) -> float:
    return min(5.0, max(1.0, value))


# ---------------------------------------------------------------------------
# STEP 5 ~ 6 가중 결합
# ---------------------------------------------------------------------------


def cf_weight(n: int, ramp_full: int = DEFAULT_CF_RAMP_FULL) -> float:
    """리뷰가 쌓일수록 CF 비중을 올립니다. n은 그룹 멤버 중 최소 리뷰 수입니다."""
    if ramp_full <= 0:
        return 1.0
    return min(1.0, max(0.0, n / ramp_full))


def tag_score_as_rating(tag_score: float) -> float:
    """태그 점수(0..1)를 1..5 예상 평점으로 늘립니다.

    CF 예측이 1..5 평점이라 태그 점수도 같은 척도로 맞춰야 두 값을 섞을 수 있고,
    STEP 7의 2.5 합의 기준선도 두 경로에 똑같이 의미를 갖습니다.
    """
    return 1.0 + 4.0 * tag_score


def combine_scores(
    cf_prediction: float | None,
    tag_score: float,
    w_cf: float,
) -> float:
    """CF가 없으면 태그 점수만, 있으면 가중 평균입니다. 결과는 1..5 척도입니다."""
    tag_rating = tag_score_as_rating(tag_score)
    if cf_prediction is None:
        return tag_rating
    return w_cf * cf_prediction + (1.0 - w_cf) * tag_rating


# ---------------------------------------------------------------------------
# STEP 7 그룹 합의
# ---------------------------------------------------------------------------


@dataclass
class ConsensusResult:
    ranked_place_ids: list[int]
    fallback: bool


def group_consensus(
    member_scores: Mapping[int, Sequence[float]],
    minimum: float = CONSENSUS_MIN,
) -> ConsensusResult:
    """멤버 최저 점수가 기준선 미만인 후보를 떨어뜨리고 평균 내림차순으로 세웁니다.

    전멸하면 기준선을 풀고 전체를 평균순으로 세웁니다(fallback=True).
    동점은 place_id 오름차순으로 갈라 결과를 안정시킵니다.
    """

    def mean(place_id: int) -> float:
        scores = member_scores[place_id]
        return sum(scores) / len(scores)

    def rank(place_ids: Iterable[int]) -> list[int]:
        return sorted(place_ids, key=lambda pid: (-mean(pid), pid))

    survivors = [
        pid for pid, scores in member_scores.items() if scores and min(scores) >= minimum
    ]
    if survivors:
        return ConsensusResult(ranked_place_ids=rank(survivors), fallback=False)
    return ConsensusResult(ranked_place_ids=rank(member_scores.keys()), fallback=True)


# ---------------------------------------------------------------------------
# STEP 9 이유 문장
# ---------------------------------------------------------------------------


def tag_label_phrase(tag_name: str, label: str) -> str:
    """사전 라벨을 문장에 넣을 형태로 다듬습니다.

    라벨이 이미 태그 이름을 품고 있으면(대화/대화하기 좋음) 라벨만 쓰고,
    아니면 "웨이팅 짧음"처럼 이름을 앞에 붙여야 무엇에 대한 말인지 드러납니다.
    """
    if tag_name in label:
        return label
    return f"{tag_name} {label}"


def attach_josa(word: str, with_batchim: str, without_batchim: str) -> str:
    """받침 유무에 맞는 조사를 붙입니다. "맵기은"처럼 어색해지는 것을 막습니다."""
    if not word:
        return word
    last = word[-1]
    if not ("가" <= last <= "힣"):
        return f"{word}{without_batchim}"
    has_batchim = (ord(last) - 0xAC00) % 28 != 0
    return f"{word}{with_batchim if has_batchim else without_batchim}"


@dataclass
class SplitTag:
    """그룹 안에서 방향이 갈린 태그입니다."""

    tag_name: str
    high_label: str
    low_label: str
    high_members: list[str] = field(default_factory=list)
    low_members: list[str] = field(default_factory=list)


@dataclass
class ReasonContext:
    place_id: int
    place_name: str
    matched_labels: list[str]
    split_tags: list[SplitTag]
    cf_weight: float
    basis: str


class ReasonWriter(Protocol):
    """이유 문장 생성기입니다. 구현을 갈아끼워도 파이프라인은 그대로 씁니다."""

    def write(self, context: ReasonContext) -> str: ...


class TemplateReasonWriter:
    """템플릿만으로 문장을 만듭니다. LLM도 네트워크도 쓰지 않습니다."""

    TAG_LEANING = (
        "{labels} 같은 점이 두 분 취향과 맞아 골랐습니다.",
        "두 분이 찾으시던 {labels} 조건을 갖춘 곳입니다.",
        "{labels} 쪽 취향 태그가 겹쳐서 추천합니다.",
    )
    CF_LEANING = (
        "비슷한 취향의 다른 커플들이 다녀온 곳이고 {labels} 점도 잘 맞습니다.",
        "비슷한 취향의 다른 커플들의 평이 좋았고 {labels} 조건도 갖췄습니다.",
        "비슷한 취향의 다른 커플들이 만족한 곳이라 {labels} 면에서도 어울립니다.",
    )
    TAG_LEANING_NO_LABEL = (
        "두 분 취향 태그를 기준으로 추린 곳입니다.",
        "아직 겹치는 취향 태그는 적지만 조건에 맞아 골랐습니다.",
        "두 분 취향에서 크게 벗어나지 않는 곳이라 추천합니다.",
    )
    CF_LEANING_NO_LABEL = (
        "비슷한 취향의 다른 커플들이 다녀온 곳이라 추천합니다.",
        "비슷한 취향의 다른 커플들의 평이 좋아 골랐습니다.",
        "비슷한 취향의 다른 커플들이 만족한 곳입니다.",
    )
    SPLIT = "다만 {tag_subject} {high_members}님이 {high_label}, {low_members}님이 {low_label} 쪽이라 갈릴 수 있습니다."

    # CF 비중이 이 값 이상이면 "비슷한 취향의 다른 커플" 쪽으로 문장을 기울입니다.
    CF_LEAN_THRESHOLD = 0.5

    def write(self, context: ReasonContext) -> str:
        cf_leaning = context.cf_weight >= self.CF_LEAN_THRESHOLD
        if context.matched_labels:
            templates = self.CF_LEANING if cf_leaning else self.TAG_LEANING
        else:
            templates = self.CF_LEANING_NO_LABEL if cf_leaning else self.TAG_LEANING_NO_LABEL

        # place_id로 고르므로 같은 장소는 언제나 같은 문장이 나옵니다.
        template = templates[context.place_id % len(templates)]
        labels = ", ".join(context.matched_labels[:3])
        sentence = template.format(labels=labels)

        split = self._split_sentence(context)
        if split:
            return f"{sentence} {split}"
        return sentence

    def _split_sentence(self, context: ReasonContext) -> str:
        for split in context.split_tags:
            if not split.high_members or not split.low_members:
                continue
            return self.SPLIT.format(
                tag_subject=attach_josa(split.tag_name, "은", "는"),
                high_members=", ".join(split.high_members),
                high_label=split.high_label,
                low_members=", ".join(split.low_members),
                low_label=split.low_label,
            )
        return ""
