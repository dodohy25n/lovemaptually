import { config } from './config.js'

export class TagApiError extends Error {
  constructor(message, code = 'TAG_FETCH_FAILED') {
    super(message)
    this.name = 'TagApiError'
    this.code = code
  }
}

export async function fetchTags({ axis, signal } = {}) {
  if (!config.apiBaseUrl) throw new TagApiError('태그 API 주소가 설정되지 않았습니다.', 'API_URL_MISSING')
  const url = new URL('/api/tags', config.apiBaseUrl)
  if (axis) url.searchParams.set('axis', axis)

  let response
  try {
    response = await fetch(url, { headers: { Accept: 'application/json' }, signal })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new TagApiError('태그 정보를 불러오지 못했습니다.')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok || !body || !Array.isArray(body.data?.tags)) {
    throw new TagApiError(body?.message || '태그 응답 형식이 올바르지 않습니다.')
  }
  return body.data.tags
}
