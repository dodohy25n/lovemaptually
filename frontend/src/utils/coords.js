/** 좌표 유효성 검사 / 정규화. 저장 전 반드시 이 함수를 거칩니다. */

export function isValidLatitude(value) {
  const num = toNumber(value)
  return num !== null && num >= -90 && num <= 90
}

export function isValidLongitude(value) {
  const num = toNumber(value)
  return num !== null && num >= -180 && num <= 180
}

export function isValidCoordinate(latitude, longitude) {
  return isValidLatitude(latitude) && isValidLongitude(longitude)
}

/**
 * 문자열로 들어온 좌표도 Number로 변환해 반환합니다.
 * 유효하지 않으면 null을 반환하므로 호출부에서 반드시 확인해야 합니다.
 */
export function normalizeCoordinate(latitude, longitude) {
  if (!isValidCoordinate(latitude, longitude)) return null
  return { latitude: toNumber(latitude), longitude: toNumber(longitude) }
}

/** 빈 문자열·불린·배열 등은 숫자로 인정하지 않습니다. */
function toNumber(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (trimmed === '') return null
  const num = Number(trimmed)
  return Number.isFinite(num) ? num : null
}

/** 두 좌표가 (약 11m 이내로) 같은 지점인지. 중복 장소 판정에 사용합니다. */
export function isSameSpot(a, b, tolerance = 0.0001) {
  if (!a || !b) return false
  return Math.abs(a.latitude - b.latitude) < tolerance && Math.abs(a.longitude - b.longitude) < tolerance
}
