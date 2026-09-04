import { config } from './config.js'
import { readText, STORAGE_KEYS } from './storageService.js'

/**
 * 추천 API 어댑터.
 *
 * 추천은 비동기 작업이라 두 단계로 나뉩니다.
 *   1. POST /api/groups/{groupId}/recommendation-requests → 202 {requestId, status:'PENDING'}
 *   2. GET  /api/recommendation-requests/{requestId} → status 가 PENDING 을 벗어날 때까지 폴링
 *
 * requestRecommendation() 이 두 단계를 한 번에 처리하고 완성된 결과만 돌려줍니다.
 *
 * 반환 형식 (고정 계약):
 *   Recommendation = {
 *     requestId, query, intent:{region,count,budget}, candidateCount, cfWeight, status,
 *     recommendations: [{ recommendationId, placeId, name, category, priceBand,
 *                         latitude, longitude, matchedTags, basis, reason, displayOrder }]
 *   }
 */

export const POLL_INTERVAL_MS = 500
export const POLL_MAX_ATTEMPTS = 20

export class RecommendationApiError extends Error {
  constructor(message, code = 'recommendation_api_error') {
    super(message)
    this.name = 'RecommendationApiError'
    this.code = code
  }
}

function requireApiContext(fetchImpl) {
  if (!config.apiBaseUrl) {
    throw new RecommendationApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  }
  const token = readText(STORAGE_KEYS.accessToken)
  if (!token) {
    throw new RecommendationApiError('로그인이 필요합니다.', 'auth_required')
  }
  if (typeof fetchImpl !== 'function') {
    throw new RecommendationApiError('이 환경에서는 추천 요청을 보낼 수 없습니다.', 'fetch_unavailable')
  }
  return { token }
}

function normalizeItem(item, index) {
  return {
    recommendationId: item?.recommendationId ?? `rec_${index}`,
    placeId: item?.placeId ?? null,
    name: String(item?.name ?? '').trim(),
    category: String(item?.category ?? '').trim(),
    priceBand: item?.priceBand ?? null,
    latitude: item?.latitude ?? null,
    longitude: item?.longitude ?? null,
    matchedTags: Array.isArray(item?.matchedTags) ? item.matchedTags.map(String) : [],
    basis: item?.basis ?? null,
    reason: String(item?.reason ?? '').trim(),
    displayOrder: Number(item?.displayOrder ?? index + 1),
  }
}

function normalizeResult(data) {
  return {
    requestId: data?.requestId ?? null,
    query: String(data?.query ?? ''),
    intent: {
      region: data?.intent?.region ?? null,
      count: data?.intent?.count ?? null,
      budget: data?.intent?.budget ?? null,
    },
    candidateCount: Number(data?.candidateCount ?? 0),
    cfWeight: data?.cfWeight ?? null,
    status: String(data?.status ?? 'PENDING').toUpperCase(),
    recommendations: Array.isArray(data?.recommendations)
      ? data.recommendations
          .map(normalizeItem)
          .sort((a, b) => a.displayOrder - b.displayOrder)
      : [],
  }
}

/** POST /api/groups/{groupId}/recommendation-requests — 추천 작업을 만들고 requestId를 받습니다. */
export async function createRecommendationRequest(
  groupId,
  query,
  { fetchImpl = globalThis.fetch } = {},
) {
  const { token } = requireApiContext(fetchImpl)
  if (groupId == null) {
    throw new RecommendationApiError('그룹을 먼저 선택해주세요.', 'group_required')
  }
  const text = String(query ?? '').trim()
  if (!text) {
    throw new RecommendationApiError('어떤 곳을 찾고 계신지 적어주세요.', 'empty_query')
  }

  const url = new URL(`/api/groups/${groupId}/recommendation-requests`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ query: text }),
    })
  } catch {
    throw new RecommendationApiError('추천 서버에 연결하지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new RecommendationApiError(
      payload?.message || '추천을 요청하지 못했습니다.',
      payload?.error?.code || (response.status === 401 ? 'auth_required' : 'http_error'),
    )
  }
  if (payload?.data?.requestId == null) {
    throw new RecommendationApiError('추천 요청 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return {
    requestId: payload.data.requestId,
    status: String(payload.data.status ?? 'PENDING').toUpperCase(),
    createdAt: payload.data.createdAt ?? null,
  }
}

/** GET /api/recommendation-requests/{requestId} — 추천 작업 한 건의 현재 상태. */
export async function fetchRecommendationRequest(
  requestId,
  { fetchImpl = globalThis.fetch } = {},
) {
  const { token } = requireApiContext(fetchImpl)

  const url = new URL(`/api/recommendation-requests/${requestId}`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    })
  } catch {
    throw new RecommendationApiError('추천 결과를 불러오지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new RecommendationApiError(
      payload?.message || '추천 결과를 불러오지 못했습니다.',
      payload?.error?.code || (response.status === 401 ? 'auth_required' : 'http_error'),
    )
  }
  if (!payload?.data) {
    throw new RecommendationApiError('추천 결과 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return normalizeResult(payload.data)
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * 추천을 요청하고 완성될 때까지 폴링합니다.
 * PENDING 을 벗어나면 그 결과를 그대로 돌려주고, 정해진 횟수를 넘기면 timeout 으로 실패합니다.
 */
export async function requestRecommendation(
  groupId,
  query,
  {
    fetchImpl = globalThis.fetch,
    intervalMs = POLL_INTERVAL_MS,
    maxAttempts = POLL_MAX_ATTEMPTS,
    onPending,
  } = {},
) {
  const created = await createRecommendationRequest(groupId, query, { fetchImpl })
  if (typeof onPending === 'function') onPending(created)

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    await wait(intervalMs)
    const result = await fetchRecommendationRequest(created.requestId, { fetchImpl })
    if (result.status !== 'PENDING') return result
  }

  throw new RecommendationApiError(
    '추천을 만드는 데 시간이 오래 걸리고 있습니다. 잠시 뒤에 다시 시도해주세요.',
    'timeout',
  )
}
