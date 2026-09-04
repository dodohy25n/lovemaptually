"""자연어 요청에서 지역, 개수, 예산대를 뽑아내는 규칙 기반 파서입니다.

LLM을 쓰지 않습니다. 사전에 있는 지역만 인식하고, 못 찾으면 region을 None으로 돌려줍니다.
Spring 계층은 region이 None이면 422로 되돌려줍니다.
"""

from __future__ import annotations

import re

DEFAULT_COUNT = 3
MIN_COUNT = 1
MAX_COUNT = 10

# 같은 곳을 가리키는 표기를 대표 이름으로 모읍니다.
REGION_ALIASES = {
    "성수동": "성수",
    "연남동": "연남",
    "홍대입구": "홍대",
    "이태원동": "이태원",
}

REGION_DICTIONARY = [
    "인사동",
    "여의도",
    "성수동",
    "연남동",
    "성수",
    "연남",
    "홍대입구",
    "홍대",
    "종로",
    "강남",
    "이태원",
    "서촌",
    "북촌",
    "삼청동",
    "익선동",
    "가로수길",
    "망원",
    "합정",
    "잠실",
    "건대",
    "신촌",
]

KOREAN_NUMERALS = {
    "한": 1,
    "하나": 1,
    "두": 2,
    "둘": 2,
    "세": 3,
    "셋": 3,
    "네": 4,
    "넷": 4,
    "다섯": 5,
    "여섯": 6,
    "일곱": 7,
    "여덟": 8,
    "아홉": 9,
    "열": 10,
}

COUNTERS = "곳|개|군데|군대"

_DIGIT_COUNT_RE = re.compile(rf"(\d+)\s*(?:{COUNTERS})")
_KOREAN_COUNT_RE = re.compile(
    r"(하나|한|둘|두|셋|세|넷|네|다섯|여섯|일곱|여덟|아홉|열)\s*(?:" + COUNTERS + r")"
)

_MAN_WON_RE = re.compile(r"(\d+(?:\.\d+)?)\s*만\s*원?")
_CHEON_WON_RE = re.compile(r"(\d+(?:\.\d+)?)\s*천\s*원?")

CHEAP_WORDS = ("저렴", "가성비", "싼", "싸게", "저가")
MODERATE_WORDS = ("적당", "무난", "보통")
EXPENSIVE_WORDS = ("비싼", "비싸", "고급", "럭셔리", "특별한 날", "파인다이닝")


def normalise_region(region: str) -> str:
    return REGION_ALIASES.get(region, region)


def parse_region(query: str) -> str | None:
    """사전에서 가장 먼저 등장하는 지역을 고릅니다. 같은 위치면 긴 표기를 우선합니다."""
    best: tuple[int, int, str] | None = None
    for candidate in REGION_DICTIONARY:
        index = query.find(candidate)
        if index == -1:
            continue
        key = (index, -len(candidate), candidate)
        if best is None or key < best:
            best = key
    if best is None:
        return None
    return normalise_region(best[2])


def parse_count(query: str) -> int:
    match = _DIGIT_COUNT_RE.search(query)
    if match:
        return _clamp_count(int(match.group(1)))
    match = _KOREAN_COUNT_RE.search(query)
    if match:
        return _clamp_count(KOREAN_NUMERALS[match.group(1)])
    return DEFAULT_COUNT


def _clamp_count(value: int) -> int:
    return min(MAX_COUNT, max(MIN_COUNT, value))


def band_from_amount(man_won: float) -> int:
    """금액(만원 단위)을 1..4 예산대로 바꿉니다."""
    if man_won < 1:
        return 1
    if man_won <= 3:
        return 2
    if man_won <= 5:
        return 3
    return 4


def parse_budget(query: str) -> int | None:
    match = _MAN_WON_RE.search(query)
    if match:
        return band_from_amount(float(match.group(1)))
    match = _CHEON_WON_RE.search(query)
    if match:
        return band_from_amount(float(match.group(1)) / 10)
    if any(word in query for word in CHEAP_WORDS):
        return 1
    if any(word in query for word in EXPENSIVE_WORDS):
        return 4
    if any(word in query for word in MODERATE_WORDS):
        return 2
    return None


def parse(query: str) -> dict:
    text = query or ""
    return {
        "region": parse_region(text),
        "count": parse_count(text),
        "budget": parse_budget(text),
    }
