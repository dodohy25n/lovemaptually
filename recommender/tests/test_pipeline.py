"""DB 없이 도는 순수 함수 테스트입니다."""

from __future__ import annotations

import pytest

from query_parser import parse
from scoring import (
    HIGH,
    LOW,
    Neighbour,
    ReasonContext,
    SplitTag,
    TemplateReasonWriter,
    attach_josa,
    cf_predict,
    cf_weight,
    combine_scores,
    group_consensus,
    member_tag_side,
    place_tag_side,
    tag_direction_score,
    tag_label_phrase,
    tag_score_as_rating,
)

# 태그 사전의 실제 id입니다. 조용함=1, 맵기=11, 웨이팅=18, 대화=27
TAG_QUIET = 1
TAG_SPICY = 11
TAG_WAITING = 18
TAG_TALK = 27


class TestTagDirectionScore:
    def test_design_doc_example_is_two_thirds(self):
        """설계 문서 예시입니다. 조용함, 대화는 맞고 웨이팅만 어긋나 2/3입니다."""
        place_facts = {
            TAG_QUIET: (3, 0),  # 조용함 HIGH
            TAG_TALK: (2, 0),  # 대화하기 좋음 HIGH
            TAG_WAITING: (4, 1),  # 웨이팅 김 HIGH
        }
        member_wants = {
            TAG_QUIET: (3, 0),  # 조용한 곳을 원함 HIGH
            TAG_TALK: (2, 0),  # 대화하기 좋은 곳을 원함 HIGH
            TAG_WAITING: (0, 3),  # 웨이팅은 짧은 곳을 원함 LOW
        }

        result = tag_direction_score(member_wants, place_facts)

        assert result.score == pytest.approx(2 / 3)
        assert result.matched_tag_ids == [TAG_QUIET, TAG_TALK]
        assert sorted(result.judged_tag_ids) == [TAG_QUIET, TAG_WAITING, TAG_TALK]

    def test_single_vote_is_not_judged(self):
        """1표짜리 취향은 판정하지 않아 분모에서 빠집니다."""
        place_facts = {TAG_QUIET: (3, 0), TAG_TALK: (2, 0)}
        member_wants = {
            TAG_QUIET: (1, 0),  # 1표라 판정 없음
            TAG_TALK: (2, 0),
        }

        result = tag_direction_score(member_wants, place_facts)

        assert result.judged_tag_ids == [TAG_TALK]
        assert result.score == pytest.approx(1.0)

    def test_no_judgeable_tag_falls_back_to_neutral(self):
        result = tag_direction_score({TAG_QUIET: (1, 0)}, {TAG_QUIET: (3, 0)})
        assert result.score == pytest.approx(0.5)
        assert result.judged_tag_ids == []

    def test_place_tie_is_not_judged(self):
        """장소 쪽 표가 동수면 방향을 못 정해 분모에서 빠집니다."""
        result = tag_direction_score({TAG_QUIET: (3, 0)}, {TAG_QUIET: (2, 2)})
        assert result.judged_tag_ids == []
        assert result.score == pytest.approx(0.5)

    def test_side_helpers(self):
        assert member_tag_side(2, 0) == HIGH
        assert member_tag_side(0, 2) == LOW
        assert member_tag_side(1, 0) is None
        assert member_tag_side(2, 2) is None
        assert place_tag_side(1, 0) == HIGH
        assert place_tag_side(0, 1) == LOW
        assert place_tag_side(0, 0) is None


class TestCfWeight:
    @pytest.mark.parametrize(
        "n, expected",
        [(0, 0.0), (5, 0.25), (15, 0.75), (20, 1.0), (30, 1.0)],
    )
    def test_ramp(self, n, expected):
        assert cf_weight(n, 20) == pytest.approx(expected)


class TestCfPrediction:
    def test_hand_made_example(self):
        """평균 3.0인 사람이, 자기 평점이 이웃 평균보다 높은 곳과 닮은 장소를 만난 경우입니다."""
        neighbours = [
            Neighbour(place_id=10, similarity=0.8, rating=5.0, place_mean=4.0),
            Neighbour(place_id=11, similarity=0.4, rating=2.0, place_mean=3.0),
        ]

        prediction = cf_predict(3.0, neighbours)

        # 3.0 + (0.8 * 1.0 + 0.4 * -1.0) / 1.2
        assert prediction == pytest.approx(3.0 + 0.4 / 1.2)

    def test_prediction_is_clamped_to_rating_scale(self):
        neighbours = [Neighbour(place_id=10, similarity=1.0, rating=5.0, place_mean=1.0)]
        assert cf_predict(4.5, neighbours) == pytest.approx(5.0)

        neighbours = [Neighbour(place_id=10, similarity=1.0, rating=1.0, place_mean=5.0)]
        assert cf_predict(1.5, neighbours) == pytest.approx(1.0)

    def test_no_neighbour_means_no_cf(self):
        assert cf_predict(3.0, []) is None

    def test_zero_similarity_sum_means_no_cf(self):
        neighbours = [Neighbour(place_id=10, similarity=0.0, rating=5.0, place_mean=3.0)]
        assert cf_predict(3.0, neighbours) is None

    def test_place_without_similarity_rows_falls_back_to_tag_score(self):
        """유사도 배치가 아직 안 돌았으면 태그 점수만으로 1..5 예상 평점을 만듭니다."""
        tag_score = 2 / 3
        prediction = cf_predict(3.0, [])

        final = combine_scores(prediction, tag_score, w_cf=0.75)

        assert final == pytest.approx(tag_score_as_rating(tag_score))
        assert final == pytest.approx(1 + 4 * 2 / 3)


class TestCombine:
    def test_weighted_mix(self):
        final = combine_scores(cf_prediction=5.0, tag_score=0.5, w_cf=0.5)
        # 0.5 * 5.0 + 0.5 * 3.0
        assert final == pytest.approx(4.0)

    def test_zero_weight_uses_tag_only(self):
        assert combine_scores(5.0, 0.25, 0.0) == pytest.approx(2.0)


class TestGroupConsensus:
    def test_minimum_drops_a_split_candidate(self):
        member_scores = {101: [5.0, 1.0], 102: [3.0, 3.0]}

        result = group_consensus(member_scores)

        assert result.fallback is False
        assert result.ranked_place_ids == [102]

    def test_fallback_ranks_everything_by_mean(self):
        member_scores = {101: [2.4, 1.0], 102: [2.0, 2.2]}

        result = group_consensus(member_scores)

        assert result.fallback is True
        # 평균 2.1 > 1.7
        assert result.ranked_place_ids == [102, 101]

    def test_survivors_are_sorted_by_mean_desc(self):
        member_scores = {101: [3.0, 3.0], 102: [4.0, 4.5], 103: [2.6, 2.6]}

        result = group_consensus(member_scores)

        assert result.ranked_place_ids == [102, 101, 103]


class TestLabelPhrase:
    def test_label_containing_tag_name_is_used_alone(self):
        assert tag_label_phrase("조용함", "조용함") == "조용함"
        assert tag_label_phrase("대화", "대화하기 좋음") == "대화하기 좋음"

    def test_other_labels_get_the_tag_name_in_front(self):
        assert tag_label_phrase("웨이팅", "짧음") == "웨이팅 짧음"
        assert tag_label_phrase("주차", "편함") == "주차 편함"

    def test_josa_follows_batchim(self):
        assert attach_josa("맵기", "은", "는") == "맵기는"
        assert attach_josa("조용함", "은", "는") == "조용함은"


class TestReasonWriter:
    def _context(self, cf_weight_value: float, place_id: int = 1) -> ReasonContext:
        return ReasonContext(
            place_id=place_id,
            place_name="달빛찻집",
            matched_labels=["조용함", "대화하기 좋음"],
            split_tags=[],
            cf_weight=cf_weight_value,
            basis="OTHERS",
        )

    def test_low_cf_weight_leans_on_tags(self):
        reason = TemplateReasonWriter().write(self._context(0.2))
        assert "비슷한 취향의 다른 커플" not in reason
        assert "조용함" in reason

    def test_high_cf_weight_mentions_similar_couples(self):
        reason = TemplateReasonWriter().write(self._context(0.8))
        assert "비슷한 취향의 다른 커플" in reason

    def test_output_is_deterministic_per_place(self):
        writer = TemplateReasonWriter()
        first = writer.write(self._context(0.2, place_id=7))
        second = writer.write(self._context(0.2, place_id=7))
        assert first == second

    def test_split_tag_names_both_sides(self):
        context = ReasonContext(
            place_id=1,
            place_name="달빛찻집",
            matched_labels=["조용함"],
            split_tags=[
                SplitTag(
                    tag_name="맵기",
                    high_label="매움",
                    low_label="순함",
                    high_members=["도현"],
                    low_members=["지우"],
                )
            ],
            cf_weight=0.2,
            basis="OWN",
        )

        reason = TemplateReasonWriter().write(context)

        assert "도현" in reason and "지우" in reason
        assert "매움" in reason and "순함" in reason
        assert reason.endswith("갈릴 수 있습니다.")


class TestQueryParser:
    def test_region_count_without_budget(self):
        assert parse("오늘 인사동 갈 건데 3곳 정도 추천해줘") == {
            "region": "인사동",
            "count": 3,
            "budget": None,
        }

    def test_no_region_returns_none(self):
        assert parse("세 곳 추천해줘") == {"region": None, "count": 3, "budget": None}

    def test_cheap_korean_numeral(self):
        assert parse("여의도에서 저렴한 데 다섯 곳") == {
            "region": "여의도",
            "count": 5,
            "budget": 1,
        }

    def test_region_alias_is_normalised(self):
        assert parse("성수동 카페 두 곳")["region"] == "성수"
        assert parse("연남동 가자")["region"] == "연남"

    def test_count_is_clamped(self):
        assert parse("강남 50곳 추천")["count"] == 10

    @pytest.mark.parametrize(
        "text, expected",
        [
            ("종로 5천원짜리", 1),
            ("종로 2만원 정도", 2),
            ("종로 4만원짜리", 3),
            ("종로 10만원짜리", 4),
            ("북촌 고급진 곳", 4),
            ("북촌 적당한 곳", 2),
        ],
    )
    def test_budget_band(self, text, expected):
        assert parse(text)["budget"] == expected
