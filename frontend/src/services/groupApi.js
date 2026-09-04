import { config } from './config.js'
import { readText, STORAGE_KEYS } from './storageService.js'

export class GroupApiError extends Error {
  constructor(message, code = 'group_api_error') {
    super(message)
    this.name = 'GroupApiError'
    this.code = code
  }
}

function normalizeGroup(group) {
  if (group?.groupId == null) return null
  return {
    id: String(group.groupId),
    groupId: group.groupId,
    type: String(group.groupType ?? '').toUpperCase(),
    name: String(group.name ?? '').trim(),
    createdAt: group.createdAt ?? null,
    members: Array.isArray(group.members)
      ? group.members.flatMap((member) => member?.userId == null ? [] : [{
          userId: member.userId,
          nickname: String(member.nickname ?? '').trim(),
          role: String(member.role ?? '').toUpperCase(),
          joinedAt: member.joinedAt ?? null,
        }])
      : [],
  }
}

function requireApiContext(fetchImpl) {
  if (!config.apiBaseUrl) {
    throw new GroupApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  }
  const token = readText(STORAGE_KEYS.accessToken)
  if (!token) {
    throw new GroupApiError('로그인이 필요합니다.', 'auth_required')
  }
  if (typeof fetchImpl !== 'function') {
    throw new GroupApiError('이 환경에서는 그룹 요청을 보낼 수 없습니다.', 'fetch_unavailable')
  }
  return { token }
}

/** GET /api/groups/me — 로그인 사용자가 참여 중인 그룹 목록. */
export async function fetchMyGroups({ fetchImpl = globalThis.fetch } = {}) {
  const { token } = requireApiContext(fetchImpl)

  const url = new URL('/api/groups/me', config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    })
  } catch {
    throw new GroupApiError('그룹 목록을 불러오지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new GroupApiError(
      payload?.message || '그룹 목록을 불러오지 못했습니다.',
      response.status === 401 ? 'auth_required' : 'http_error',
    )
  }
  if (!Array.isArray(payload?.data?.groups)) {
    throw new GroupApiError('그룹 목록 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return payload.data.groups.map(normalizeGroup).filter(Boolean)
}

/** POST /api/groups — 현재 사용자를 OWNER로 하는 그룹을 생성합니다. */
export async function createGroup(
  { groupType = 'COUPLE', name = '' } = {},
  { fetchImpl = globalThis.fetch } = {},
) {
  const { token } = requireApiContext(fetchImpl)
  const url = new URL('/api/groups', config.apiBaseUrl)

  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        groupType: String(groupType).toUpperCase(),
        name: String(name).trim() || null,
      }),
    })
  } catch {
    throw new GroupApiError('그룹을 만들지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    const alreadyExists = response.status === 409
      && payload?.error?.code === 'COUPLE_GROUP_ALREADY_EXISTS'
    throw new GroupApiError(
      payload?.message || '그룹을 만들지 못했습니다.',
      alreadyExists ? 'couple_group_exists' : 'http_error',
    )
  }

  const group = normalizeGroup(payload?.data)
  if (!group) {
    throw new GroupApiError('그룹 생성 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }
  return group
}

/** POST /api/groups/{groupId}/invites — 그룹 OWNER가 초대 코드를 발급합니다. */
export async function createInvite(
  groupId,
  { maxUses = 1, expiresInHours = 24 } = {},
  { fetchImpl = globalThis.fetch } = {},
) {
  const { token } = requireApiContext(fetchImpl)
  const url = new URL(`/api/groups/${encodeURIComponent(String(groupId))}/invites`, config.apiBaseUrl)

  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ maxUses, expiresInHours }),
    })
  } catch {
    throw new GroupApiError('초대 코드를 만들지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new GroupApiError(
      payload?.message || '초대 코드를 만들지 못했습니다.',
      response.status === 403 ? 'group_access_denied' : 'http_error',
    )
  }

  const invite = payload?.data
  if (invite?.inviteCodeId == null || !invite?.code) {
    throw new GroupApiError('초대 코드 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }
  return {
    id: String(invite.inviteCodeId),
    inviteCodeId: invite.inviteCodeId,
    code: String(invite.code),
    maxUses: invite.maxUses,
    useCount: invite.useCount,
    status: String(invite.status ?? '').toUpperCase(),
    expiresAt: invite.expiresAt ?? null,
    createdAt: invite.createdAt ?? null,
  }
}
