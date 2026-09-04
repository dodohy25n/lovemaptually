import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import { createRecommendationRequest } from '@/services/recommendationApi.js'

const originalBaseUrl = config.apiBaseUrl
afterEach(() => { config.apiBaseUrl = originalBaseUrl })

describe('추천 요청 생성 API', () => {
  it('POST 요청을 만들고 접수 결과를 정규화한다', async () => {
    config.apiBaseUrl = 'https://demo.mock.pstmn.io'
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true, status: 202, json: async () => ({ data: { requestId: 88, status: 'PENDING', createdAt: '2026-09-04T03:00:00Z' } }) })
    await expect(createRecommendationRequest(7001, ' 인사동 맛집 추천 ', { fetchImpl })).resolves.toEqual({ requestId: '88', status: 'PENDING', createdAt: '2026-09-04T03:00:00Z' })
    const [url, request] = fetchImpl.mock.calls[0]
    expect(String(url)).toBe('https://demo.mock.pstmn.io/api/groups/7001/recommendation-requests')
    expect(request.headers.Authorization).toBe('Bearer mock-token')
    expect(JSON.parse(request.body)).toEqual({ query: '인사동 맛집 추천' })
  })

  it('빈 질문은 요청 전에 거부한다', async () => {
    config.apiBaseUrl = 'https://example.test'
    const fetchImpl = vi.fn()
    await expect(createRecommendationRequest(1, ' ', { fetchImpl })).rejects.toMatchObject({ code: 'invalid_query' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })
})
