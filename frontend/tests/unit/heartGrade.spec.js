import { describe, it, expect } from 'vitest'
import {
  toHeartGrade,
  heartGradeInfo,
  formatScore,
  clampScore,
  reviewAverage,
  coupleScoreFromReviews,
} from '@/utils/heartGrade.js'

describe('평점에 따른 하트 등급 계산', () => {
  it.each([
    [5, 'good'],
    [4.8, 'good'],
    [4, 'good'],
    [3.9, 'normal'],
    [3.2, 'normal'],
    [2, 'normal'],
    [1.9, 'bad'],
    [1.5, 'bad'],
    [0, 'bad'],
  ])('%s점은 %s 등급', (score, expected) => {
    expect(toHeartGrade(score)).toBe(expected)
  })

  it('경계값 4.0과 2.0은 위쪽 등급에 포함된다', () => {
    expect(toHeartGrade(4.0)).toBe('good')
    expect(toHeartGrade(3.99)).toBe('normal')
    expect(toHeartGrade(2.0)).toBe('normal')
    expect(toHeartGrade(1.99)).toBe('bad')
  })

  it('숫자가 아니면 아쉬워요로 처리한다', () => {
    expect(toHeartGrade(undefined)).toBe('bad')
    expect(toHeartGrade('abc')).toBe('bad')
    expect(toHeartGrade(null)).toBe('bad')
  })

  it('문자열 점수도 숫자로 해석한다', () => {
    expect(toHeartGrade('4.5')).toBe('good')
  })

  it('등급마다 라벨과 서로 다른 아이콘이 있다', () => {
    // 아이콘은 번들러가 처리합니다(해시가 붙거나 data URI로 인라인됨).
    // 경로 모양 대신 '세 등급의 아이콘이 서로 다르다'는 실제 규칙을 확인합니다.
    const assets = ['good', 'normal', 'bad'].map((key) => {
      const info = heartGradeInfo(key)
      expect(info.label).toBeTruthy()
      expect(typeof info.asset).toBe('string')
      expect(info.asset.length).toBeGreaterThan(0)
      return info.asset
    })
    expect(new Set(assets).size).toBe(3)
  })

  it('알 수 없는 등급은 bad로 폴백한다', () => {
    expect(heartGradeInfo('unknown').key).toBe('bad')
  })
})

describe('점수 포맷', () => {
  it('항상 소수점 한 자리 문자열이다', () => {
    expect(formatScore(4)).toBe('4.0')
    expect(formatScore(3.24)).toBe('3.2')
    expect(formatScore('1.5')).toBe('1.5')
  })

  it('범위를 벗어나면 0~5로 자른다', () => {
    expect(formatScore(9)).toBe('5.0')
    expect(formatScore(-3)).toBe('0.0')
    expect(clampScore(7)).toBe(5)
  })

  it('숫자가 아니면 0.0', () => {
    expect(formatScore('없음')).toBe('0.0')
  })
})

describe('리뷰 평균과 커플 통합 점수', () => {
  const him = { atmosphere: 5, taste: 4.5, value: 4, service: 5 }
  const her = { atmosphere: 4, taste: 4, value: 4, service: 4 }

  it('세부 점수 네 개의 평균을 낸다', () => {
    expect(reviewAverage(him)).toBe(4.6)
    expect(reviewAverage(her)).toBe(4)
  })

  it('두 사람 리뷰의 평균이 커플 점수다', () => {
    expect(coupleScoreFromReviews([him, her])).toBe(4.3)
  })

  it('리뷰가 없으면 0이다', () => {
    expect(coupleScoreFromReviews([])).toBe(0)
    expect(coupleScoreFromReviews(undefined)).toBe(0)
  })
})
