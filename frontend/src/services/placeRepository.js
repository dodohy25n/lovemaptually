import { STORAGE_KEYS, readJson, writeJson, removeKey } from './storageService.js'
import { createSeedPlaces } from './seedPlaces.js'
import { normalizeCoordinate, isSameSpot } from '@/utils/coords.js'
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

  const reviews = Array.isArray(draft?.reviews) ? draft.reviews.map(normalizeReview) : []
  const coupleScore =
    reviews.length > 0 ? coupleScoreFromReviews(reviews) : round1(clampScore(draft?.coupleScore))
  const now = new Date().toISOString()

  return {
    id: id ?? draft?.id ?? createId('place'),
    name,
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

    // 같은 이름 + 거의 같은 좌표(약 11m 이내)면 중복 등록으로 봅니다.
    const duplicate = places.find(
      (existing) => existing.name === place.name && isSameSpot(existing, place),
    )
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
  constructor({ baseUrl = '' } = {}) {
    super()
    this.baseUrl = baseUrl
  }

  #notReady(method) {
    throw new PlaceRepositoryError(
      `ApiPlaceRepository.${method}() 는 백엔드 구현 후 연결됩니다. ` +
        `(예정 엔드포인트: ${this.baseUrl || '/api'}/places)`,
      'backend_not_ready',
    )
  }

  async list() { return this.#notReady('list') }        // GET    /places
  async get() { return this.#notReady('get') }          // GET    /places/:id
  async create() { return this.#notReady('create') }    // POST   /places
  async update() { return this.#notReady('update') }    // PATCH  /places/:id
  async remove() { return this.#notReady('remove') }    // DELETE /places/:id
  async reset() { return this.#notReady('reset') }
}
