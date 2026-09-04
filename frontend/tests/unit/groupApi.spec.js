import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import { createGroup, createInvite, fetchMyGroups } from '@/services/groupApi.js'
import { STORAGE_KEYS, writeText } from '@/services/storageService.js'

const originalBaseUrl = config.apiBaseUrl

afterEach(() => {
  config.apiBaseUrl = originalBaseUrl
})

describe('내 그룹 목록 API', () => {
  it('인증 토큰으로 GET /api/groups/me를 호출하고 그룹을 정규화한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    writeText(STORAGE_KEYS.accessToken, 'signed-token')
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        status: 200,
        message: '조회했습니다',
        data: {
          groups: [{
            groupId: 7001,
            groupType: 'COUPLE',
            name: '우리 둘',
            createdAt: '2026-09-04T00:00:00Z',
            members: [{ userId: 1, nickname: '러비', role: 'OWNER', joinedAt: '2026-09-04T00:00:00Z' }],
          }],
        },
      }),
    })

    await expect(fetchMyGroups({ fetchImpl })).resolves.toMatchObject([
      { id: '7001', type: 'COUPLE', name: '우리 둘', members: [{ role: 'OWNER' }] },
    ])
    expect(fetchImpl).toHaveBeenCalledWith(
      new URL('https://api.example.test/api/groups/me'),
      expect.objectContaining({
        method: 'GET',
        headers: { Accept: 'application/json', Authorization: 'Bearer signed-token' },
      }),
    )
  })

  it('토큰이 없으면 네트워크 요청 전에 auth_required로 실패한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn()
    await expect(fetchMyGroups({ fetchImpl })).rejects.toMatchObject({ code: 'auth_required' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('groups 배열이 없는 응답을 거부한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    writeText(STORAGE_KEYS.accessToken, 'signed-token')
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ status: 200, data: {} }),
    })
    await expect(fetchMyGroups({ fetchImpl })).rejects.toMatchObject({ code: 'invalid_response' })
  })
})

describe('그룹 생성 API', () => {
  it('POST /api/groups로 커플 그룹을 만들고 응답을 정규화한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    writeText(STORAGE_KEYS.accessToken, 'signed-token')
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({
        status: 201,
        message: '그룹을 만들었습니다',
        data: {
          groupId: 7001,
          groupType: 'COUPLE',
          name: '우리 둘',
          members: [{ userId: 1, nickname: '러비', role: 'OWNER' }],
        },
      }),
    })

    await expect(createGroup({ groupType: 'couple', name: ' 우리 둘 ' }, { fetchImpl }))
      .resolves.toMatchObject({ id: '7001', type: 'COUPLE', name: '우리 둘' })

    const [url, request] = fetchImpl.mock.calls[0]
    expect(String(url)).toBe('https://api.example.test/api/groups')
    expect(request).toMatchObject({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer signed-token' }),
    })
    expect(JSON.parse(request.body)).toEqual({ groupType: 'COUPLE', name: '우리 둘' })
  })

  it('이미 커플 그룹이 있으면 전용 오류로 변환한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    writeText(STORAGE_KEYS.accessToken, 'signed-token')
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        message: '이미 참여 중인 커플 그룹이 있습니다',
        error: { code: 'COUPLE_GROUP_ALREADY_EXISTS' },
      }),
    })

    await expect(createGroup({ groupType: 'COUPLE' }, { fetchImpl })).rejects.toMatchObject({
      code: 'couple_group_exists',
      message: '이미 참여 중인 커플 그룹이 있습니다',
    })
  })
})

describe('초대 코드 생성 API', () => {
  it('POST /api/groups/{id}/invites로 초대 코드를 발급한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    writeText(STORAGE_KEYS.accessToken, 'signed-token')
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({
        status: 201,
        message: '초대 코드를 발급했습니다',
        data: {
          inviteCodeId: 9001,
          code: 'LOVE9001',
          maxUses: 1,
          useCount: 0,
          status: 'ACTIVE',
          expiresAt: '2026-09-05T00:00:00Z',
          createdAt: '2026-09-04T00:00:00Z',
        },
      }),
    })

    await expect(createInvite(7001, {}, { fetchImpl })).resolves.toMatchObject({
      id: '9001',
      code: 'LOVE9001',
      status: 'ACTIVE',
    })
    const [url, request] = fetchImpl.mock.calls[0]
    expect(String(url)).toBe('https://api.example.test/api/groups/7001/invites')
    expect(request.method).toBe('POST')
    expect(JSON.parse(request.body)).toEqual({ maxUses: 1, expiresInHours: 24 })
  })

  it('OWNER가 아니면 접근 거부 오류로 변환한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    writeText(STORAGE_KEYS.accessToken, 'signed-token')
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({ message: '내가 소유한 그룹이 아닙니다' }),
    })

    await expect(createInvite(7001, {}, { fetchImpl })).rejects.toMatchObject({
      code: 'group_access_denied',
    })
  })
})
