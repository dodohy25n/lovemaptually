/**
 * 장소 동일성 판정.
 *
 * 리뷰는 '가게'에 붙습니다. 그래서 같은 가게가 두 번 등록되면 커플 점수가 갈라지고,
 * 다른 가게를 하나로 합치면 남의 리뷰가 섞입니다. 그 판정을 여기 한 곳에서만 합니다.
 *
 * 신뢰 순서:
 *   1) 공급자 장소 ID (카카오 등) — 상호가 바뀌어도 유지되는 유일한 식별자
 *   2) 이름 + 좌표 근접 — 수기 입력 장소의 차선책. 오타('디어 모먼트'/'디어모먼트')와
 *      같은 건물 다른 층을 구분하지 못하므로, 공급자 ID가 있으면 항상 그쪽을 씁니다.
 */
import { isSameSpot } from './coords.js'

/** 공급자 검색을 거치지 않고 사용자가 직접 입력한 장소. */
export const PROVIDER_MANUAL = 'manual'

/** 공급자 장소 ID. 숫자로 오는 공급자(카카오)가 있어 문자열로 통일합니다. */
export function normalizeProviderPlaceId(value) {
  if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  if (typeof value !== 'string') return ''
  return value.trim()
}

/**
 * 공급자 이름.
 *
 * 장소 ID가 없으면 공급자를 신뢰할 수 없으므로 항상 manual로 되돌립니다.
 * (`providerPlaceId === '' ⟺ provider === 'manual'` 는 저장된 모든 장소가 지키는 규칙입니다.)
 */
export function normalizeProvider(value, providerPlaceId) {
  if (!providerPlaceId) return PROVIDER_MANUAL
  const name = String(value ?? '').trim().toLowerCase()
  return name && name !== PROVIDER_MANUAL ? name : PROVIDER_MANUAL
}

/**
 * 공급자 식별키(`'kakao:1234567'`). 수기 입력 장소는 null.
 * 공급자가 다르면 ID가 우연히 같아도 다른 가게이므로 공급자명을 함께 묶습니다.
 */
export function providerKey(place) {
  const id = normalizeProviderPlaceId(place?.providerPlaceId)
  if (!id) return null
  const provider = normalizeProvider(place?.provider, id)
  return provider === PROVIDER_MANUAL ? null : `${provider}:${id}`
}

/**
 * 두 장소가 같은 가게인지.
 *
 * - 양쪽 다 공급자 ID가 있으면 **ID만** 비교합니다. 같은 건물 2층과 3층은
 *   좌표가 거의 같지만 엄연히 다른 가게이므로, 좌표를 섞어 보면 안 됩니다.
 * - 한쪽만 있으면(공급자 검색으로 등록된 가게를 다른 사람이 수기로 또 등록한 경우)
 *   이름 + 좌표로 최선을 다해 잡습니다.
 */
export function isSamePlace(a, b) {
  if (!a || !b) return false

  const keyA = providerKey(a)
  const keyB = providerKey(b)
  if (keyA && keyB) return keyA === keyB

  return a.name === b.name && isSameSpot(a, b)
}
