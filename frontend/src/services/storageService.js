/**
 * localStorage 접근을 감싸는 유일한 지점.
 * 화면/스토어는 절대 localStorage를 직접 호출하지 않습니다.
 *
 * - 저장 키에는 버전이 포함됩니다 (love-maptually:places:v1).
 * - JSON 파싱 오류, 사파리 프라이빗 모드의 접근 거부 등에서도 앱이 죽지 않고
 *   fallback 값을 돌려줍니다.
 */

const NAMESPACE = 'love-maptually'
const VERSION = 'v1'

export const STORAGE_KEYS = {
  places: `${NAMESPACE}:places:${VERSION}`,
  seeded: `${NAMESPACE}:seeded:${VERSION}`,
  accessToken: `${NAMESPACE}:access-token`,
  authUser: `${NAMESPACE}:auth-user:${VERSION}`,
}

/** 마지막으로 발생한 저장소 오류 (UI에서 안내 배너를 띄우는 데 사용). */
let lastError = null

export function getLastStorageError() {
  return lastError
}

function memoryFallbackStore() {
  const map = new Map()
  return {
    getItem: (key) => (map.has(key) ? map.get(key) : null),
    setItem: (key, value) => map.set(key, value),
    removeItem: (key) => map.delete(key),
  }
}

const memoryStore = memoryFallbackStore()

/** localStorage를 못 쓰는 환경(SSR, 프라이빗 모드)에서는 메모리 저장소로 대체합니다. */
function resolveStore() {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return memoryStore
    const probe = `${NAMESPACE}:probe`
    window.localStorage.setItem(probe, '1')
    window.localStorage.removeItem(probe)
    return window.localStorage
  } catch (error) {
    lastError = { type: 'unavailable', message: String(error) }
    return memoryStore
  }
}

/**
 * JSON을 읽어 반환합니다.
 * 깨진 JSON이면 fallback을 반환하고, 손상된 값은 지워 다음 실행에 영향을 주지 않게 합니다.
 */
export function readJson(key, fallback = null) {
  const store = resolveStore()
  let raw = null
  try {
    raw = store.getItem(key)
  } catch (error) {
    lastError = { type: 'read', key, message: String(error) }
    return fallback
  }
  if (raw === null || raw === undefined) return fallback

  try {
    const parsed = JSON.parse(raw)
    if (parsed === null || parsed === undefined) return fallback
    return parsed
  } catch (error) {
    lastError = { type: 'parse', key, message: String(error) }
    // 깨진 데이터는 즉시 제거해 매 실행마다 같은 오류가 반복되지 않게 합니다.
    try {
      store.removeItem(key)
    } catch {
      /* 제거 실패는 무시 — fallback으로 계속 동작합니다. */
    }
    return fallback
  }
}

export function writeJson(key, value) {
  const store = resolveStore()
  try {
    store.setItem(key, JSON.stringify(value))
    return true
  } catch (error) {
    // 용량 초과(QuotaExceededError) 등. 앱은 계속 동작하되 저장은 실패했음을 알립니다.
    lastError = { type: 'write', key, message: String(error) }
    return false
  }
}

/** 토큰처럼 JSON 직렬화 없이 저장해야 하는 문자열을 기록합니다. */
export function writeText(key, value) {
  const store = resolveStore()
  try {
    store.setItem(key, String(value))
    return true
  } catch (error) {
    lastError = { type: 'write', key, message: String(error) }
    return false
  }
}

export function readText(key, fallback = '') {
  const store = resolveStore()
  try {
    return store.getItem(key) ?? fallback
  } catch (error) {
    lastError = { type: 'read', key, message: String(error) }
    return fallback
  }
}

export function removeKey(key) {
  const store = resolveStore()
  try {
    store.removeItem(key)
    return true
  } catch (error) {
    lastError = { type: 'remove', key, message: String(error) }
    return false
  }
}

/** 테스트에서 매 케이스마다 저장소를 비우기 위해 사용합니다. */
export function clearAll() {
  lastError = null
  Object.values(STORAGE_KEYS).forEach((key) => removeKey(key))
}
