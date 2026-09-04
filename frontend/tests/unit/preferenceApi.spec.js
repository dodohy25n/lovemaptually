import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import { fetchGroupPreferences, splitPreferences } from '@/services/preferenceApi.js'
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

const SPICY = {
  tagId: 11,
  tagName: '맵기',
  axis: 'TASTE',
  label: 'SPLIT',
  side: null,
  sideLabel: null,
  judgedMemberCount: 2,
  members: [
    { userId: 1, nickname: '도현', side: 'LOW', sideLabel: '순함', wantHighCount: 0, wantLowCount: 4 },
    { userId: 2, nickname: '지우', side: 'HIGH', sideLabel: '매움', wantHighCount: 3, wantLowCount: 1 },
  ],
}

describe('그룹 취향 API', () => {
  it('인증 토큰으로 GET /api/groups/{id}/preferences를 호출하고 취향을 정규화한다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({
      status: 200,
      message: '조회했습니다',
      data: { groupId: 1, preferences: [SPICY] },
    }))

    await expect(fetchGroupPreferences(1, { fetchImpl })).resolves.toMatchObject([
      {
        tagId: 11,
        tagName: '맵기',
        label: 'SPLIT',
        judgedMemberCount: 2,
        members: [{ nickname: '도현', sideLabel: '순함' }, { nickname: '지우', sideLabel: '매움' }],
      },
    ])
    expect(fetchImpl).toHaveBeenCalledWith(
      new URL('https://api.example.test/api/groups/1/preferences'),
      expect.objectContaining({
        method: 'GET',
        headers: { Accept: 'application/json', Authorization: 'Bearer signed-token' },
      }),
    )
  })

  it('토큰이 없으면 네트워크 요청 전에 auth_required로 실패한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn()
    await expect(fetchGroupPreferences(1, { fetchImpl })).rejects.toMatchObject({ code: 'auth_required' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('그룹 번호가 없으면 group_required로 실패한다', async () => {
    signIn()
    const fetchImpl = vi.fn()
    await expect(fetchGroupPreferences(null, { fetchImpl })).rejects.toMatchObject({ code: 'group_required' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('preferences 배열이 없는 응답을 거부한다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ status: 200, data: {} }))
    await expect(fetchGroupPreferences(1, { fetchImpl })).rejects.toMatchObject({ code: 'invalid_response' })
  })

  it('백엔드 오류 코드를 그대로 노출한다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(
      { status: 403, message: '그룹 구성원이 아닙니다', error: { code: 'NOT_A_MEMBER', details: [] } },
      { ok: false, status: 403 },
    ))
    await expect(fetchGroupPreferences(1, { fetchImpl })).rejects.toMatchObject({ code: 'NOT_A_MEMBER' })
  })

  it('SPLIT 태그만 골라낸다', () => {
    const all = [
      { tagId: 1, label: 'ALL_SAME' },
      { tagId: 11, label: 'SPLIT' },
      { tagId: 18, label: 'ONE_SIDED' },
    ]
    expect(splitPreferences(all)).toEqual([{ tagId: 11, label: 'SPLIT' }])
  })
})
