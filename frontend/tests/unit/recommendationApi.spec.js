import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import {
  createRecommendationRequest,
  fetchRecommendationRequest,
  requestRecommendation,
} from '@/services/recommendationApi.js'
import { STORAGE_KEYS, writeText } from '@/services/storageService.js'

const originalBaseUrl = config.apiBaseUrl

afterEach(() => {
  config.apiBaseUrl = originalBaseUrl
})

function signIn() {
  config.apiBaseUrl = 'https://api.example.test'
  writeText(STORAGE_KEYS.accessToken, 'signed-token')
}

function jsonResponse(body, { ok = true, status = 200 } = {}) {
  return { ok, status, json: async () => body }
}

const ACCEPTED = jsonResponse(
  { status: 202, message: '추천을 준비하고 있습니다', data: { requestId: 5, status: 'PENDING', createdAt: '2026-09-04T02:48:51Z' } },
  { status: 202 },
)

const PENDING = jsonResponse({
  status: 200,
  data: { requestId: 5, query: '오늘 인사동 갈 건데 조용한 카페 3곳 추천해줘', status: 'PENDING', recommendations: [] },
})

const COMPLETED = jsonResponse({
  status: 200,
  data: {
    requestId: 5,
    query: '오늘 인사동 갈 건데 조용한 카페 3곳 추천해줘',
    intent: { region: '인사동', count: 3, budget: null },
    candidateCount: 42,
    cfWeight: 0.35,
    status: 'COMPLETED',
    recommendations: [
      { recommendationId: 22, placeId: 9, name: '소나무뜰', category: '카페', matchedTags: ['조용함'], basis: 'CF', reason: '조용합니다.', displayOrder: 2 },
      { recommendationId: 21, placeId: 4, name: '쌍계약과점', category: '카페', matchedTags: ['조용함', '뷰'], basis: 'OWN', reason: '한적합니다.', displayOrder: 1 },
    ],
  },
})

describe('추천 요청 API', () => {
  it('POST /api/groups/{id}/recommendation-requests로 질문을 보내고 requestId를 받는다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(ACCEPTED)

    await expect(createRecommendationRequest(1, '  인사동 카페  ', { fetchImpl })).resolves.toMatchObject({
      requestId: 5,
      status: 'PENDING',
    })
    expect(fetchImpl).toHaveBeenCalledWith(
      new URL('https://api.example.test/api/groups/1/recommendation-requests'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ query: '인사동 카페' }) }),
    )
  })

  it('동네를 못 읽으면 REGION_NOT_FOUND 코드를 그대로 올려준다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(
      { status: 422, message: '어느 동네를 찾으시는지 알려 주세요', error: { code: 'REGION_NOT_FOUND', details: [] } },
      { ok: false, status: 422 },
    ))

    await expect(createRecommendationRequest(1, '세 곳 추천해줘', { fetchImpl })).rejects.toMatchObject({
      code: 'REGION_NOT_FOUND',
      message: '어느 동네를 찾으시는지 알려 주세요',
    })
  })

  it('빈 질문은 네트워크 요청 전에 막는다', async () => {
    signIn()
    const fetchImpl = vi.fn()
    await expect(createRecommendationRequest(1, '   ', { fetchImpl })).rejects.toMatchObject({ code: 'empty_query' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('결과를 displayOrder 순으로 정렬해 돌려준다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(COMPLETED)
    const result = await fetchRecommendationRequest(5, { fetchImpl })

    expect(result.status).toBe('COMPLETED')
    expect(result.candidateCount).toBe(42)
    expect(result.cfWeight).toBe(0.35)
    expect(result.recommendations.map((item) => item.name)).toEqual(['쌍계약과점', '소나무뜰'])
    expect(fetchImpl).toHaveBeenCalledWith(
      new URL('https://api.example.test/api/recommendation-requests/5'),
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('PENDING이 끝날 때까지 폴링하고 COMPLETED가 되면 결과를 돌려준다', async () => {
    signIn()
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(ACCEPTED)
      .mockResolvedValueOnce(PENDING)
      .mockResolvedValueOnce(PENDING)
      .mockResolvedValueOnce(COMPLETED)

    const result = await requestRecommendation(1, '인사동 카페', { fetchImpl, intervalMs: 0 })

    expect(result.status).toBe('COMPLETED')
    expect(result.recommendations).toHaveLength(2)
    expect(fetchImpl).toHaveBeenCalledTimes(4)
  })

  it('정해진 횟수 안에 끝나지 않으면 timeout으로 실패한다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockImplementation((url, init) =>
      (init?.method === 'POST' ? ACCEPTED : PENDING))

    await expect(
      requestRecommendation(1, '인사동 카페', { fetchImpl, intervalMs: 0, maxAttempts: 3 }),
    ).rejects.toMatchObject({
      code: 'timeout',
      message: '추천을 만드는 데 시간이 오래 걸리고 있습니다. 잠시 뒤에 다시 시도해주세요.',
    })
    expect(fetchImpl).toHaveBeenCalledTimes(4)
  })

  it('토큰이 없으면 네트워크 요청 전에 auth_required로 실패한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn()
    await expect(requestRecommendation(1, '인사동 카페', { fetchImpl })).rejects.toMatchObject({ code: 'auth_required' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })
})
