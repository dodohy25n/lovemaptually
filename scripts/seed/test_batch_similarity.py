"""batch_similarity.pearson_pairs 단위 테스트.

설계문서 4장의 손계산 예제를 그대로 씁니다. 문서는 사용자끼리 비교했지만 수식은 대칭이라
축을 뒤집어 "장소 A 를 세 사람이 (5, 2, 4) 로, 장소 B 를 같은 세 사람이 (2, 5, 2) 로 평가"로 옮겼습니다.

실행: python3 -m unittest scripts/seed/test_batch_similarity.py
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from batch_similarity import pearson_pairs  # noqa: E402


class PearsonPairsTest(unittest.TestCase):

    def test_opposite_taste_is_strongly_negative(self):
        # 문서: 도현 (5, 2, 4) 대 민수 (2, 5, 2) -> 분자 -5.000 / 분모 5.290 = -0.945
        ratings = {
            ("도현", "카페A"): 5, ("지민", "카페A"): 2, ("민수", "카페A"): 4,
            ("도현", "카페B"): 2, ("지민", "카페B"): 5, ("민수", "카페B"): 2,
        }
        pairs = pearson_pairs(ratings)
        self.assertEqual(set(pairs), {("카페A", "카페B")})
        self.assertAlmostEqual(pairs[("카페A", "카페B")], -0.9449, places=4)
        self.assertEqual(round(pairs[("카페A", "카페B")], 3), -0.945)

    def test_same_direction_is_positive(self):
        # 문서: 도현 (5, 2, 4) 대 지민 (4, 2, 5) -> 분자 3.667 / 분모 4.667 = +0.786
        ratings = {
            ("도현", "카페A"): 5, ("지민", "카페A"): 2, ("민수", "카페A"): 4,
            ("도현", "카페C"): 4, ("지민", "카페C"): 2, ("민수", "카페C"): 5,
        }
        pairs = pearson_pairs(ratings)
        self.assertAlmostEqual(pairs[("카페A", "카페C")], 0.7857, places=4)

    def test_four_co_raters_nearly_identical(self):
        # 문서: 도현 (5, 2, 4, 3) 대 서연 (5, 1, 4, 2) -> 분자 7.000 / 분모 7.071 = +0.990
        ratings = {
            ("찻집", "A"): 5, ("식당", "A"): 2, ("베이커리", "A"): 4, ("국숫집", "A"): 3,
            ("찻집", "D"): 5, ("식당", "D"): 1, ("베이커리", "D"): 4, ("국숫집", "D"): 2,
        }
        pairs = pearson_pairs(ratings)
        self.assertAlmostEqual(pairs[("A", "D")], 0.99, places=3)

    def test_single_co_rater_is_excluded(self):
        ratings = {
            ("도현", "카페A"): 5, ("지민", "카페A"): 2,
            ("도현", "카페B"): 4, ("서연", "카페B"): 1,
        }
        self.assertEqual(pearson_pairs(ratings), {})
        # 공동 평가자가 한 명뿐이면 분산이 0 이라 기준을 낮춰도 상관계수가 나오지 않습니다
        self.assertEqual(pearson_pairs(ratings, min_co_raters=1), {})

    def test_zero_variance_is_skipped(self):
        ratings = {
            ("도현", "카페A"): 3, ("지민", "카페A"): 3, ("민수", "카페A"): 3,
            ("도현", "카페B"): 1, ("지민", "카페B"): 5, ("민수", "카페B"): 2,
        }
        self.assertEqual(pearson_pairs(ratings), {})

    def test_perfectly_opposite_is_minus_one(self):
        ratings = {
            ("도현", "카페A"): 5, ("지민", "카페A"): 1, ("민수", "카페A"): 4,
            ("도현", "카페B"): 1, ("지민", "카페B"): 5, ("민수", "카페B"): 2,
        }
        self.assertEqual(pearson_pairs(ratings)[("카페A", "카페B")], -1.0)


if __name__ == "__main__":
    unittest.main()
