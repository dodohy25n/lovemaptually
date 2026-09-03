/**
 * 점수 → 하트 등급 변환.
 * 등급 경계는 여기 한 곳에서만 정의하며, 지도 핀·리뷰 카드·요약 카드가 모두 이 값을 씁니다.
 *
 *  4.0 ~ 5.0 : good   (좋아요)
 *  2.0 ~ 3.9 : normal (보통이에요)
 *  0   ~ 1.9 : bad    (아쉬워요)
 */
import heartGood from '@/assets/icons/heart-good.svg'
import heartNormal from '@/assets/icons/heart-normal.svg'
import heartBad from '@/assets/icons/heart-bad.svg'

export const HEART_GRADES = {
  good: { key: 'good', label: '좋아요', min: 4, asset: heartGood, color: 'var(--lm-grade-good)' },
  normal: { key: 'normal', label: '보통이에요', min: 2, asset: heartNormal, color: 'var(--lm-grade-normal)' },
  bad: { key: 'bad', label: '아쉬워요', min: 0, asset: heartBad, color: 'var(--lm-grade-bad)' },
}

export const HEART_GRADE_LEGEND = [HEART_GRADES.good, HEART_GRADES.normal, HEART_GRADES.bad]

/** 점수(0~5)를 등급 key로 변환. 숫자가 아니면 'bad'로 처리합니다. */
export function toHeartGrade(score) {
  const value = Number(score)
  if (!Number.isFinite(value)) return HEART_GRADES.bad.key
  if (value >= HEART_GRADES.good.min) return HEART_GRADES.good.key
  if (value >= HEART_GRADES.normal.min) return HEART_GRADES.normal.key
  return HEART_GRADES.bad.key
}

/** 등급 key → 표시 정보. 알 수 없는 key는 bad로 폴백합니다. */
export function heartGradeInfo(gradeKey) {
  return HEART_GRADES[gradeKey] ?? HEART_GRADES.bad
}

/** 점수를 항상 소수점 한 자리 문자열로. 화면의 숫자 점수는 이미지가 아닌 이 텍스트로 렌더링합니다. */
export function formatScore(score) {
  const value = Number(score)
  if (!Number.isFinite(value)) return '0.0'
  return clampScore(value).toFixed(1)
}

/** 0~5 범위로 자릅니다. */
export function clampScore(score) {
  const value = Number(score)
  if (!Number.isFinite(value)) return 0
  return Math.min(5, Math.max(0, value))
}

/** 리뷰 한 건의 세부 점수(분위기·맛·가성비·서비스) 평균. */
export function reviewAverage(review) {
  if (!review) return 0
  const parts = [review.atmosphere, review.taste, review.value, review.service]
    .map(Number)
    .filter((n) => Number.isFinite(n))
  if (parts.length === 0) return 0
  const sum = parts.reduce((acc, n) => acc + clampScore(n), 0)
  return round1(sum / parts.length)
}

/** 두 사람의 리뷰 평균 = 커플 통합 점수. 리뷰가 없으면 0. */
export function coupleScoreFromReviews(reviews) {
  const list = Array.isArray(reviews) ? reviews : []
  if (list.length === 0) return 0
  const sum = list.reduce((acc, review) => acc + reviewAverage(review), 0)
  return round1(sum / list.length)
}

export function round1(value) {
  return Math.round(Number(value) * 10) / 10
}
