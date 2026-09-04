import { STORAGE_KEYS, readJson, readText, writeJson, removeKey } from './storageService.js'
import { createSeedPlaces } from './seedPlaces.js'
import { normalizeCoordinate } from '@/utils/coords.js'
import {
  isSamePlace,
  normalizeProvider,
  normalizeProviderPlaceId,
} from '@/utils/placeIdentity.js'
import { coupleScoreFromReviews, toHeartGrade, clampScore, round1 } from '@/utils/heartGrade.js'
import { createId } from '@/utils/id.js'

/**
 * 저장소 계층.
 *
 *   PlaceRepository (인터페이스)
 *    ├─ LocalPlaceRepository  ← 현재 사용 (localStorage)
 *    └─ ApiPlaceRepository    ← 백엔드 완성 후 교체 지점 (지금은 placeholder)
 *
 * Vue 컴포넌트와 Pinia 스토어는 이 인터페이스만 알고 있으며,
 * 뒤가 localStorage인지 HTTP인지 알지 못합니다.
 */

/**
 * 시연용 사진입니다. 사진 업로드가 아직 없어서 장소마다 고정된 표본 사진을 붙입니다.
 * placeId 로 고르므로 같은 장소는 늘 같은 사진이 나옵니다.
 */
const SAMPLE_PHOTOS = [
  '/samples/cake.jpg', '/samples/ade.jpg', '/samples/window.jpg',
  '/samples/salad.jpg', '/samples/pudding.jpg', '/samples/pasta.jpg',
]

function samplePhotos(placeId, count = 3) {
  const seed = Number(placeId) || 0
  return Array.from({ length: count }, (_, index) =>
    SAMPLE_PHOTOS[(seed * 2 + index) % SAMPLE_PHOTOS.length])
}

export class PlaceRepositoryError extends Error {
  constructor(message, code = 'repository_error') {
    super(message)
    this.name = 'PlaceRepositoryError'
    this.code = code
  }
}

/** 어떤 구현이든 지켜야 하는 계약. 구현하지 않으면 명시적으로 실패합니다. */
export class PlaceRepository {
  async list() {
    throw new PlaceRepositoryError('list() not implemented', 'not_implemented')
  }
  async get() {
    throw new PlaceRepositoryError('get() not implemented', 'not_implemented')
  }
  async create() {
    throw new PlaceRepositoryError('create() not implemented', 'not_implemented')
  }
  async update() {
    throw new PlaceRepositoryError('update() not implemented', 'not_implemented')
  }
  async remove() {
    throw new PlaceRepositoryError('remove() not implemented', 'not_implemented')
  }
  async reset() {
    throw new PlaceRepositoryError('reset() not implemented', 'not_implemented')
  }
}

/** 리뷰 한 건을 저장 가능한 형태로 정규화합니다. */
function normalizeReview(review) {
  return {
    userId: String(review?.userId ?? ''),
    userName: String(review?.userName ?? ''),
    content: String(review?.content ?? ''),
    atmosphere: clampScore(review?.atmosphere),
    taste: clampScore(review?.taste),
    value: clampScore(review?.value),
    service: clampScore(review?.service),
    revisitIntent: Boolean(review?.revisitIntent),
    images: Array.isArray(review?.images) ? review.images.filter((src) => typeof src === 'string') : [],
    tagStatus: ['PENDING', 'COMPLETED', 'FAILED'].includes(review?.tagStatus)
      ? review.tagStatus
      : 'COMPLETED',
    extractedTags: Array.isArray(review?.extractedTags)
      ? review.extractedTags.slice(0, 5).map((item) => ({
          tag: String(item?.tag ?? ''),
          fact: item?.fact == null ? null : String(item.fact),
          want: item?.want == null ? null : String(item.want),
          evidence: item?.evidence == null ? null : String(item.evidence),
        })).filter((item) => item.tag)
      : [],
  }
}

/**
 * 저장 전 장소 정규화 + 검증.
 *
 * coupleScore와 heartGrade는 저장 시점에 리뷰로부터 항상 다시 계산합니다.
 * 화면에서 넘어온 값과 실제 리뷰 데이터가 어긋나는 것을 막기 위해서입니다.
 * (리뷰가 아직 없는 장소만 draft.coupleScore를 그대로 씁니다.)
 */
export function normalizePlace(draft, { id, createdAt } = {}) {
  const name = String(draft?.name ?? '').trim()
  if (!name) {
    throw new PlaceRepositoryError('장소명은 필수입니다.', 'invalid_name')
  }

  const coordinate = normalizeCoordinate(draft?.latitude, draft?.longitude)
  if (!coordinate) {
    throw new PlaceRepositoryError('위도·경도가 올바르지 않습니다.', 'invalid_coordinate')
  }

  // 리뷰가 붙을 대상을 정하는 값이므로 좌표와 같은 급으로 정규화합니다.
  const providerPlaceId = normalizeProviderPlaceId(draft?.providerPlaceId)
  const provider = normalizeProvider(draft?.provider, providerPlaceId)

  const reviews = Array.isArray(draft?.reviews) ? draft.reviews.map(normalizeReview) : []
  const coupleScore =
    reviews.length > 0 ? coupleScoreFromReviews(reviews) : round1(clampScore(draft?.coupleScore))
  const now = new Date().toISOString()

  return {
    id: id ?? draft?.id ?? createId('place'),
    name,
    provider,
    providerPlaceId,
    address: String(draft?.address ?? '').trim(),
    category: String(draft?.category ?? '기타').trim() || '기타',
    latitude: coordinate.latitude,
    longitude: coordinate.longitude,
    visitedAt: String(draft?.visitedAt ?? '').trim(),
    coupleScore,
    heartGrade: toHeartGrade(coupleScore),
    images: Array.isArray(draft?.images) ? draft.images.filter((src) => typeof src === 'string') : [],
    tags: Array.isArray(draft?.tags) ? draft.tags.map(String).filter(Boolean) : [],
    reviews,
    memo: String(draft?.memo ?? ''),
    createdAt: createdAt ?? draft?.createdAt ?? now,
    updatedAt: now,
  }
}

/** 저장소에서 읽은 배열 중 형태가 깨진 항목은 조용히 버립니다. */
function reviveStoredPlaces(raw) {
  if (!Array.isArray(raw)) return null
  const revived = []
  for (const item of raw) {
    try {
      revived.push(normalizePlace(item, { id: item?.id, createdAt: item?.createdAt }))
    } catch {
      // 레코드 한 건이 손상됐다고 전체 목록을 잃지 않도록 건너뜁니다.
    }
  }
  return revived
}

export class LocalPlaceRepository extends PlaceRepository {
  #key
  #seedFactory

  constructor({ key = STORAGE_KEYS.places, seedFactory = createSeedPlaces } = {}) {
    super()
    this.#key = key
    this.#seedFactory = seedFactory
  }

  /** 저장된 값이 없거나 배열이 아니면(깨진 JSON 포함) seed로 시작합니다. */
  #load() {
    const revived = reviveStoredPlaces(readJson(this.#key, null))
    if (revived === null) {
      const seeded = this.#seedFactory().map((place) =>
        normalizePlace(place, { id: place.id, createdAt: place.createdAt }),
      )
      writeJson(this.#key, seeded)
      return seeded
    }
    return revived
  }

  #save(places) {
    writeJson(this.#key, places)
    return places
  }

  async list() {
    return this.#load()
  }

  async get(id) {
    return this.#load().find((place) => place.id === id) ?? null
  }

  async create(draft) {
    const places = this.#load()
    const place = normalizePlace(draft)

    // 공급자 장소 ID가 있으면 그것으로, 없으면 이름 + 좌표로 판정합니다 (placeIdentity.js).
    const duplicate = places.find((existing) => isSamePlace(existing, place))
    if (duplicate) {
      throw new PlaceRepositoryError('이미 같은 위치에 등록된 장소입니다.', 'duplicate_place')
    }

    this.#save([...places, place])
    return place
  }

  async update(id, patch) {
    const places = this.#load()
    const index = places.findIndex((place) => place.id === id)
    if (index === -1) {
      throw new PlaceRepositoryError('수정할 장소를 찾을 수 없습니다.', 'not_found')
    }
    const merged = normalizePlace(
      { ...places[index], ...patch },
      { id, createdAt: places[index].createdAt },
    )

    // 수정 결과가 다른 장소와 같은 가게가 되면 리뷰가 두 갈래로 남으므로 막습니다.
    const collides = places.some(
      (existing) => existing.id !== id && isSamePlace(existing, merged),
    )
    if (collides) {
      throw new PlaceRepositoryError('이미 같은 위치에 등록된 장소입니다.', 'duplicate_place')
    }

    const next = [...places]
    next[index] = merged
    this.#save(next)
    return merged
  }

  async remove(id) {
    const places = this.#load()
    const next = places.filter((place) => place.id !== id)
    if (next.length === places.length) return false
    this.#save(next)
    return true
  }

  /**
   * 저장소를 비웁니다.
   * seed:true  → 다음 조회 때 seed가 다시 생성됩니다.
   * seed:false → 빈 목록으로 고정합니다 (Empty State 확인용).
   */
  async reset({ seed = true } = {}) {
    removeKey(this.#key)
    if (!seed) return this.#save([])
    return this.#load()
  }
}

/**
 * 백엔드 완성 후 사용할 구현.
 *
 * 지금은 의도적으로 미구현입니다. VITE_DATA_MODE=api 로 바꾸고 아래 메서드에
 * fetch를 채워 넣으면, 화면과 스토어 코드는 한 줄도 고치지 않고 그대로 동작합니다.
 * 반환 형식은 normalizePlace()가 만드는 객체와 동일해야 합니다.
 */
export class ApiPlaceRepository extends PlaceRepository {
  constructor({ baseUrl = '', fetchImpl = null, groupId = null } = {}) {
    super()
    this.baseUrl = baseUrl
    // globalThis.fetch 를 그대로 담아 두면 this 가 Repository 로 바뀌어 브라우저가 호출을 거부합니다.
    // 그래서 감싼 함수를 기본값으로 둡니다.
    this.fetchImpl = fetchImpl ?? ((...args) => globalThis.fetch(...args))
    // 지도 핀은 그룹에 종속됩니다. 로그인 후 그룹을 알아내면 이 값을 채웁니다.
    this.groupId = groupId
  }

  setGroupId(groupId) {
    this.groupId = groupId
  }

  /**
   * GET /api/groups/{groupId}/places — 그룹이 기록한 장소 마커.
   * /api/places 는 키워드 검색 전용이라 지도 핀은 이 경로로 읽습니다.
   */
  async listByGroup() {
    if (!this.baseUrl) {
      throw new PlaceRepositoryError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
    }
    if (typeof this.fetchImpl !== 'function') {
      throw new PlaceRepositoryError('이 환경에서는 장소 목록 요청을 보낼 수 없습니다.', 'fetch_unavailable')
    }

    const token = readText(STORAGE_KEYS.accessToken)
    const url = new URL(`/api/groups/${this.groupId}/places`, this.baseUrl)
    const headers = { Accept: 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`

    let response
    try {
      response = await this.fetchImpl(url, { method: 'GET', headers })
    } catch {
      throw new PlaceRepositoryError('장소 목록을 불러오지 못했습니다.', 'network_error')
    }

    const payload = await response.json().catch(() => null)
    if (!response.ok || !Array.isArray(payload?.data?.markers)) {
      throw new PlaceRepositoryError(
        payload?.message || `장소 목록 응답이 올바르지 않습니다. (${response.status})`,
        response.ok ? 'invalid_response' : 'http_error',
      )
    }

    return payload.data.markers.flatMap((marker) => {
      // 마커에는 개별 점수가 없어 '좋아한 사람 수 / 리뷰한 사람 수' 로 하트 등급을 냅니다.
      const reviewed = Number(marker.reviewedCount ?? 0)
      const liked = Number(marker.likedCount ?? 0)
      const coupleScore = reviewed > 0 ? round1((liked / reviewed) * 5) : 0
      try {
        return [normalizePlace({
          id: String(marker.placeId),
          name: marker.name,
          address: marker.address,
          category: marker.category,
          latitude: marker.latitude,
          longitude: marker.longitude,
          coupleScore,
          images: samplePhotos(marker.placeId),
          tags: [],
          reviews: [],
        }, { id: String(marker.placeId) })]
      } catch {
        return []
      }
    })
  }

  #notReady(method) {
    throw new PlaceRepositoryError(
      `ApiPlaceRepository.${method}() 는 백엔드 구현 후 연결됩니다. ` +
        `(예정 엔드포인트: ${this.baseUrl || '/api'}/places)`,
      'backend_not_ready',
    )
  }

  async list() {
    // 그룹을 알면 지도 핀(그룹 마커)을 읽고, 모르면 기존 검색 경로를 그대로 씁니다.
    if (this.groupId != null) return this.listByGroup()

    if (!this.baseUrl) {
      throw new PlaceRepositoryError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
    }
    if (typeof this.fetchImpl !== 'function') {
      throw new PlaceRepositoryError('이 환경에서는 장소 목록 요청을 보낼 수 없습니다.', 'fetch_unavailable')
    }

    const url = new URL('/api/places', this.baseUrl)
    const headers = { Accept: 'application/json' }
    if (url.hostname.endsWith('.mock.pstmn.io')) headers.Authorization = 'Bearer mock-token'

    let response
    try {
      response = await this.fetchImpl(url, { method: 'GET', headers })
    } catch (error) {
      throw new PlaceRepositoryError(
        error?.name === 'AbortError'
          ? '장소 목록 조회가 취소되었습니다.'
          : '장소 목록을 불러오지 못했습니다.',
        error?.name === 'AbortError' ? 'request_aborted' : 'network_error',
      )
    }

    const payload = await response.json().catch(() => null)
    if (!response.ok || !Array.isArray(payload?.data?.content)) {
      throw new PlaceRepositoryError(
        payload?.message || `장소 목록 응답이 올바르지 않습니다. (${response.status})`,
        response.ok ? 'invalid_response' : 'http_error',
      )
    }

    return payload.data.content.flatMap((place) => {
      try {
        return [normalizePlace({
          id: String(place.placeId),
          provider: place.provider,
          providerPlaceId: place.providerPlaceId,
          name: place.name,
          address: place.address,
          category: place.category,
          latitude: place.latitude,
          longitude: place.longitude,
          images: samplePhotos(place.placeId),
          tags: [],
          reviews: [],
        }, { id: String(place.placeId) })]
      } catch {
        return []
      }
    })
  }                                                     // GET    /api/places

  async get(id) {
    if (!this.baseUrl) {
      throw new PlaceRepositoryError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
    }
    if (typeof this.fetchImpl !== 'function') {
      throw new PlaceRepositoryError('이 환경에서는 장소 상세 요청을 보낼 수 없습니다.', 'fetch_unavailable')
    }

    const url = new URL(`/api/places/${encodeURIComponent(String(id))}`, this.baseUrl)
    const headers = { Accept: 'application/json' }
    if (url.hostname.endsWith('.mock.pstmn.io')) headers.Authorization = 'Bearer mock-token'

    let response
    try {
      response = await this.fetchImpl(url, { method: 'GET', headers })
    } catch (error) {
      throw new PlaceRepositoryError(
        error?.name === 'AbortError'
          ? '장소 상세 조회가 취소되었습니다.'
          : '장소 상세 정보를 불러오지 못했습니다.',
        error?.name === 'AbortError' ? 'request_aborted' : 'network_error',
      )
    }

    if (!response.ok) {
      throw new PlaceRepositoryError(
        response.status === 404
          ? '장소를 찾을 수 없습니다.'
          : `장소 상세 정보를 불러오지 못했습니다. (${response.status})`,
        response.status === 404 ? 'not_found' : 'http_error',
      )
    }

    const payload = await response.json()
    const place = payload?.data
    if (!place || place.placeId == null) {
      throw new PlaceRepositoryError('장소 상세 응답 형식이 올바르지 않습니다.', 'invalid_response')
    }

    return normalizePlace({
      id: String(place.placeId),
      provider: place.provider,
      providerPlaceId: place.providerPlaceId,
      name: place.name,
      address: place.address,
      category: place.category,
      latitude: place.latitude,
      longitude: place.longitude,
      tags: Array.isArray(place.tags)
        ? place.tags.map((item) => typeof item === 'string' ? item : item?.tag).filter(Boolean)
        : [],
      images: samplePhotos(place.placeId),
      reviews: [],
    }, { id: String(place.placeId) })
  }                                               // GET    /api/places/:id
  async create() { return this.#notReady('create') }    // POST   /places
  async update() { return this.#notReady('update') }    // PATCH  /places/:id
  async remove() { return this.#notReady('remove') }    // DELETE /places/:id
  async reset() { return this.#notReady('reset') }
}
