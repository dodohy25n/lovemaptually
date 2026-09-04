import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import { login } from '@/services/authApi.js'
import { readJson, STORAGE_KEYS } from '@/services/storageService.js'

const originalBaseUrl = config.apiBaseUrl

afterEach(() => {
  config.apiBaseUrl = originalBaseUrl
})

describe('로그인 API', () => {
  it('로그인 요청 후 토큰과 사용자 정보를 저장한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        status: 200,
        message: '로그인했습니다',
        data: {
          userId: 7,
          email: 'user@example.com',
          nickname: '러비',
          accessToken: 'signed-token',
          tokenType: 'Bearer',
          expiresIn: 3600,
        },
      }),
    })

    await expect(login({ email: ' user@example.com ', password: 'password123' }, { fetchImpl }))
      .resolves.toMatchObject({ userId: 7, accessToken: 'signed-token' })

    const [url, request] = fetchImpl.mock.calls[0]
    expect(String(url)).toBe('https://api.example.test/api/auth/login')
    expect(request.method).toBe('POST')
    expect(JSON.parse(request.body)).toEqual({
      email: 'user@example.com',
      password: 'password123',
    })
    expect(window.localStorage.getItem(STORAGE_KEYS.accessToken)).toBe('signed-token')
    expect(readJson(STORAGE_KEYS.authUser)).toMatchObject({ userId: 7, nickname: '러비' })
  })

  it('401 응답을 자격 증명 오류로 변환한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ message: '이메일 또는 비밀번호가 올바르지 않습니다' }),
    })

    await expect(login({ email: 'user@example.com', password: 'wrongpass' }, { fetchImpl }))
      .rejects.toMatchObject({ code: 'invalid_credentials' })
  })

  it('토큰이 없는 성공 응답을 거부한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ status: 200, data: { userId: 7 } }),
    })

    await expect(login({ email: 'user@example.com', password: 'password123' }, { fetchImpl }))
      .rejects.toMatchObject({ code: 'invalid_response' })
  })
})
