import { config } from './config.js'
import { readText, STORAGE_KEYS } from './storageService.js'

export class PreferenceApiError extends Error {
  constructor(message, code = 'preference_api_error') {
    super(message)
    this.name = 'PreferenceApiError'
    this.code = code
  }
}

/** GET /api/groups/{groupId}/preferences — 그룹 구성원의 집계 취향. */
export async function fetchGroupPreferences(groupId, { fetchImpl = globalThis.fetch } = {}) {
  if (!config.apiBaseUrl) throw new PreferenceApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  if (groupId == null || String(groupId).trim() === '') throw new PreferenceApiError('그룹을 선택해 주세요.', 'missing_group_id')
  if (typeof fetchImpl !== 'function') throw new PreferenceApiError('취향 정보를 요청할 수 없습니다.', 'fetch_unavailable')

  const url = new URL(`/api/groups/${encodeURIComponent(String(groupId))}/preferences`, config.apiBaseUrl)
  const token = readText(STORAGE_KEYS.accessToken)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token || (url.hostname.endsWith('.mock.pstmn.io') ? 'mock-token' : '')}`,
      },
    })
  } catch {
    throw new PreferenceApiError('취향 정보를 불러오지 못했습니다.', 'network_error')
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new PreferenceApiError(payload?.message || '취향 정보를 불러오지 못했습니다.', 'http_error')
  if (!Array.isArray(payload?.data?.preferences)) {
    throw new PreferenceApiError('취향 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }
  return {
    groupId: String(payload.data.groupId ?? groupId),
    preferences: payload.data.preferences.flatMap((item) => item?.tagId == null ? [] : [{
      tagId: String(item.tagId),
      tagName: String(item.tagName ?? ''),
      axis: String(item.axis ?? ''),
      label: String(item.label ?? ''),
      side: item.side ?? null,
      sideLabel: item.sideLabel ?? null,
      judgedMemberCount: Number(item.judgedMemberCount) || 0,
      members: Array.isArray(item.members) ? item.members : [],
    }]),
  }
}
