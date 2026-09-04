import { config } from './config.js'
import { readText, STORAGE_KEYS } from './storageService.js'

export class RecommendationApiError extends Error {
  constructor(message, code = 'recommendation_api_error') {
    super(message)
    this.name = 'RecommendationApiError'
    this.code = code
  }
}

function authorizationFor(url) {
  const token = readText(STORAGE_KEYS.accessToken)
  return `Bearer ${token || (url.hostname.endsWith('.mock.pstmn.io') ? 'mock-token' : '')}`
}

/** POST /api/groups/{groupId}/recommendation-requests — 비동기 추천 요청 생성. */
export async function createRecommendationRequest(groupId, query, { fetchImpl = globalThis.fetch } = {}) {
  if (!config.apiBaseUrl) throw new RecommendationApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  if (groupId == null || String(groupId).trim() === '') throw new RecommendationApiError('그룹을 선택해 주세요.', 'missing_group_id')
  const normalizedQuery = String(query ?? '').trim()
  if (!normalizedQuery) throw new RecommendationApiError('추천 질문을 입력해 주세요.', 'invalid_query')
  if (typeof fetchImpl !== 'function') throw new RecommendationApiError('추천 요청을 보낼 수 없습니다.', 'fetch_unavailable')

  const url = new URL(`/api/groups/${encodeURIComponent(String(groupId))}/recommendation-requests`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: { Accept: 'application/json', Authorization: authorizationFor(url), 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: normalizedQuery }),
    })
  } catch {
    throw new RecommendationApiError('추천 요청을 보내지 못했습니다.', 'network_error')
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new RecommendationApiError(payload?.message || '추천 요청을 보내지 못했습니다.', 'http_error')
  if (payload?.data?.requestId == null || !payload.data.status) {
    throw new RecommendationApiError('추천 요청 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }
  return { requestId: String(payload.data.requestId), status: String(payload.data.status), createdAt: payload.data.createdAt ?? null }
}
