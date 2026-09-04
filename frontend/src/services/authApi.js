import { config } from './config.js'
import { STORAGE_KEYS, writeJson, writeText } from './storageService.js'

export class AuthApiError extends Error {
  constructor(message, code = 'auth_error') {
    super(message)
    this.name = 'AuthApiError'
    this.code = code
  }
}

/** POST /api/auth/login 후 토큰과 최소 사용자 정보를 저장합니다. */
export async function login({ email, password }, { fetchImpl = globalThis.fetch } = {}) {
  if (!config.apiBaseUrl) {
    throw new AuthApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  }
  if (typeof fetchImpl !== 'function') {
    throw new AuthApiError('이 환경에서는 로그인 요청을 보낼 수 없습니다.', 'fetch_unavailable')
  }

  const url = new URL('/api/auth/login', config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: String(email).trim(), password: String(password) }),
    })
  } catch {
    throw new AuthApiError('로그인 서버에 연결하지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new AuthApiError(
      payload?.message || '이메일 또는 비밀번호를 확인해주세요.',
      response.status === 401 ? 'invalid_credentials' : 'http_error',
    )
  }

  const auth = payload?.data
  if (!auth?.accessToken || !auth?.userId) {
    throw new AuthApiError('로그인 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  if (!writeText(STORAGE_KEYS.accessToken, auth.accessToken)) {
    throw new AuthApiError('로그인 정보를 저장하지 못했습니다.', 'storage_error')
  }
  writeJson(STORAGE_KEYS.authUser, {
    userId: auth.userId,
    email: auth.email,
    nickname: auth.nickname,
    tokenType: auth.tokenType,
    expiresIn: auth.expiresIn,
  })

  return auth
}
