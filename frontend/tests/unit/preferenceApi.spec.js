import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import { fetchGroupPreferences } from '@/services/preferenceApi.js'

const originalBaseUrl = config.apiBaseUrl
afterEach(() => { config.apiBaseUrl = originalBaseUrl })

describe('그룹 취향 조회 API', () => {
  it('GET 응답을 화면용 취향 목록으로 정규화한다', async () => {
    config.apiBaseUrl = 'https://demo.mock.pstmn.io'
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ data: {
      groupId: 7001,
      preferences: [{ tagId: 3, tagName: '맵기', axis: 'BIPOLAR', label: 'SPLIT', side: null, sideLabel: null, judgedMemberCount: 2, members: [{ userId: 1, nickname: '도현', side: 'LOW', sideLabel: '순함' }] }],
    } }) })
    await expect(fetchGroupPreferences(7001, { fetchImpl })).resolves.toMatchObject({
      groupId: '7001', preferences: [{ tagId: '3', tagName: '맵기', label: 'SPLIT' }],
    })
    const [url, request] = fetchImpl.mock.calls[0]
    expect(String(url)).toBe('https://demo.mock.pstmn.io/api/groups/7001/preferences')
    expect(request.headers.Authorization).toBe('Bearer mock-token')
  })

  it('preferences 배열이 없으면 거부한다', async () => {
    config.apiBaseUrl = 'https://example.test'
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ data: {} }) })
    await expect(fetchGroupPreferences(1, { fetchImpl })).rejects.toMatchObject({ code: 'invalid_response' })
  })
})
