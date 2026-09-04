import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import { createReviewFromApi, ReviewApiError, toApiRating } from '@/services/reviewApi.js'

const REVIEW = {
  userId: 'him',
  userName: '도현',
  content: '조용해서 대화하기 좋았어요.',
  atmosphere: 5,
  taste: 4,
  value: 5,
  service: 4,
  revisitIntent: true,
  images: [],
}

const originalBaseUrl = config.apiBaseUrl

afterEach(() => {
  config.apiBaseUrl = originalBaseUrl
})

describe('리뷰 등록 API', () => {
  it('POST /api/reviews 요청과 성공 응답을 화면 모델로 변환한다', async () => {
    config.apiBaseUrl = 'https://demo.mock.pstmn.io'
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({
        status: 201,
        message: '리뷰를 저장했습니다',
        data: {
          reviewId: 501,
          placeId: 412,
          visitedOn: '2026-09-01',
          rating: 5,
          content: '조용해서 대화하기 좋았어요.',
          tagStatus: 'COMPLETED',
          tags: [{ tag: '조용함', fact: 'HIGH', want: 'HIGH', evidence: '조용해서' }],
          createdAt: '2026-09-03T14:22:10',
        },
      }),
    })

    await expect(createReviewFromApi(412, REVIEW, {
      visitedOn: '2026-09-01',
      fetchImpl,
    })).resolves.toMatchObject({
      reviewId: 501,
      userId: 'him',
      tagStatus: 'COMPLETED',
      extractedTags: [{ tag: '조용함' }],
    })

    const [url, request] = fetchImpl.mock.calls[0]
    expect(String(url)).toBe('https://demo.mock.pstmn.io/api/reviews')
    expect(request.method).toBe('POST')
    expect(request.headers).toMatchObject({
      Authorization: 'Bearer mock-token',
      'Content-Type': 'application/json',
      'x-mock-response-code': '201',
    })
    expect(JSON.parse(request.body)).toEqual({
      placeId: 412,
      visitedOn: '2026-09-01',
      rating: 5,
      content: '조용해서 대화하기 좋았어요.',
    })
  })

  it('세부 점수 평균을 backend 계약인 1~5 정수로 반올림한다', () => {
    expect(toApiRating({ atmosphere: 3, taste: 4, value: 4, service: 3 })).toBe(4)
    expect(toApiRating(REVIEW)).toBe(5)
  })

  it('선택한 점수가 없으면 API 요청 전에 거부한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn()
    await expect(createReviewFromApi(412, {
      ...REVIEW,
      atmosphere: 0,
      taste: 0,
      value: 0,
      service: 0,
    }, {
      visitedOn: '2026-09-01',
      fetchImpl,
    })).rejects.toMatchObject({ code: 'invalid_rating' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('중복 리뷰 오류를 구분해 전달한다', async () => {
    config.apiBaseUrl = 'https://example.test'
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        message: '같은 날 같은 장소에 이미 리뷰를 남겼습니다',
        error: { code: 'REVIEW_DUPLICATED', details: [] },
      }),
    })

    await expect(createReviewFromApi(412, REVIEW, {
      visitedOn: '2026-09-01',
      fetchImpl,
    })).rejects.toMatchObject({
      name: 'ReviewApiError',
      code: 'duplicate_review',
      message: '같은 날 같은 장소에 이미 리뷰를 남겼습니다',
    })
  })

  it('API 기본 주소가 없으면 요청하지 않는다', async () => {
    config.apiBaseUrl = ''
    await expect(createReviewFromApi(412, REVIEW, {
      visitedOn: '2026-09-01',
    })).rejects.toBeInstanceOf(ReviewApiError)
  })
})
