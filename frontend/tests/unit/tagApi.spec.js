import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import { fetchTags } from '@/services/tagApi.js'

describe('태그 API', () => {
  const originalUrl = config.apiBaseUrl

  afterEach(() => {
    config.apiBaseUrl = originalUrl
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('성공 응답의 data.tags를 반환한다', async () => {
    config.apiBaseUrl = 'https://mock.example.com'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 200, message: '조회했습니다', data: { tags: [{ tagId: 1, name: '조용함' }] } }),
    }))

    await expect(fetchTags()).resolves.toEqual([{ tagId: 1, name: '조용함' }])
    expect(fetch).toHaveBeenCalledWith(new URL('https://mock.example.com/api/tags'), expect.any(Object))
  })

  it('axis를 쿼리로 전달한다', async () => {
    config.apiBaseUrl = 'https://mock.example.com'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: { tags: [] } }) }))
    await fetchTags({ axis: 'TASTE' })
    expect(String(fetch.mock.calls[0][0])).toContain('axis=TASTE')
  })

  it('응답 형식이 다르면 오류를 반환한다', async () => {
    config.apiBaseUrl = 'https://mock.example.com'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: {} }) }))
    await expect(fetchTags()).rejects.toMatchObject({ code: 'TAG_FETCH_FAILED' })
  })
})
