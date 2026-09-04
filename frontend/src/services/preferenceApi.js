import { config } from './config.js'
import { readText, STORAGE_KEYS } from './storageService.js'

/**
 * 그룹 취향(우리 취향) API 어댑터.
 *
 * 반환 형식 (고정 계약):
 *   Preference = {
 *     tagId, tagName, axis, label, side, sideLabel, judgedMemberCount,
 *     members: [{ userId, nickname, side, sideLabel, wantHighCount, wantLowCount }]
 *   }
 *   label 은 ALL_SAME | ONE_SIDED | SPLIT 세 가지입니다.
 */

export class PreferenceApiError extends Error {
  constructor(message, code = 'preference_api_error') {
    super(message)
    this.name = 'PreferenceApiError'
    this.code = code
  }
}

const LABELS = ['ALL_SAME', 'ONE_SIDED', 'SPLIT']

function requireApiContext(fetchImpl) {
  if (!config.apiBaseUrl) {
    throw new PreferenceApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  }
  const token = readText(STORAGE_KEYS.accessToken)
  if (!token) {
    throw new PreferenceApiError('로그인이 필요합니다.', 'auth_required')
  }
  if (typeof fetchImpl !== 'function') {
    throw new PreferenceApiError('이 환경에서는 취향 요청을 보낼 수 없습니다.', 'fetch_unavailable')
  }
  return { token }
}

function normalizeMember(member) {
  if (member?.userId == null) return null
  return {
    userId: member.userId,
    nickname: String(member.nickname ?? '').trim(),
    side: member.side ?? null,
    sideLabel: member.sideLabel ?? null,
    wantHighCount: Number(member.wantHighCount ?? 0),
    wantLowCount: Number(member.wantLowCount ?? 0),
  }
}

function normalizePreference(preference) {
  if (preference?.tagId == null) return null
  const label = LABELS.includes(preference.label) ? preference.label : 'ALL_SAME'
  return {
    tagId: preference.tagId,
    tagName: String(preference.tagName ?? '').trim(),
    axis: String(preference.axis ?? '').toUpperCase(),
    label,
    side: preference.side ?? null,
    sideLabel: preference.sideLabel ?? null,
    judgedMemberCount: Number(preference.judgedMemberCount ?? 0),
    members: Array.isArray(preference.members)
      ? preference.members.map(normalizeMember).filter(Boolean)
      : [],
  }
}

/** GET /api/groups/{groupId}/preferences — 그룹이 쌓아 온 태그별 취향. */
export async function fetchGroupPreferences(groupId, { fetchImpl = globalThis.fetch } = {}) {
  const { token } = requireApiContext(fetchImpl)
  if (groupId == null) {
    throw new PreferenceApiError('그룹을 먼저 선택해주세요.', 'group_required')
  }

  const url = new URL(`/api/groups/${groupId}/preferences`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    })
  } catch {
    throw new PreferenceApiError('우리 취향을 불러오지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new PreferenceApiError(
      payload?.message || '우리 취향을 불러오지 못했습니다.',
      payload?.error?.code || (response.status === 401 ? 'auth_required' : 'http_error'),
    )
  }
  if (!Array.isArray(payload?.data?.preferences)) {
    throw new PreferenceApiError('취향 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return payload.data.preferences.map(normalizePreference).filter(Boolean)
}

/** 취향이 갈린 태그만 추립니다. 화면에서 가장 먼저 보여주는 값입니다. */
export function splitPreferences(preferences) {
  return (preferences ?? []).filter((preference) => preference.label === 'SPLIT')
}
