import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  LocalPlaceRepository,
  ApiPlaceRepository,
  PlaceRepository,
  PlaceRepositoryError,
  normalizePlace,
} from '@/services/placeRepository.js'
import { STORAGE_KEYS } from '@/services/storageService.js'

const DRAFT = {
  name: '연남동 오후 세시',
  address: '서울 마포구 연남로 27',
  category: '카페',
  latitude: '37.5626',
  longitude: '126.9256',
  visitedAt: '2026-02-14',
}

function repo() {
  return new LocalPlaceRepository({ seedFactory: () => [] })
}

describe('저장소 저장 및 복원', () => {
  let repository

  beforeEach(() => {
    repository = repo()
  })

  it('저장한 장소를 다시 읽을 수 있다', async () => {
    const created = await repository.create(DRAFT)
    const list = await repository.list()

    expect(list).toHaveLength(1)
    expect(list[0].id).toBe(created.id)
    expect(list[0].name).toBe('연남동 오후 세시')
  })

  it('새로운 인스턴스에서도 같은 데이터가 복원된다 (새로고침 상황)', async () => {
    const created = await repository.create(DRAFT)

    const restored = await repo().get(created.id)
    expect(restored).not.toBeNull()
    expect(restored.latitude).toBe(37.5626)
    expect(restored.longitude).toBe(126.9256)
    expect(restored.visitedAt).toBe('2026-02-14')
  })

  it('좌표는 Number로 저장된다', async () => {
    const created = await repository.create(DRAFT)
    expect(typeof created.latitude).toBe('number')
    expect(typeof created.longitude).toBe('number')
  })

  it('수정하면 값이 바뀌고 createdAt은 유지된다', async () => {
    const created = await repository.create(DRAFT)
    const updated = await repository.update(created.id, { name: '오후 네시', coupleScore: 2.5 })

    expect(updated.name).toBe('오후 네시')
    expect(updated.coupleScore).toBe(2.5)
    expect(updated.heartGrade).toBe('normal')
    expect(updated.createdAt).toBe(created.createdAt)
  })

  it('삭제하면 목록에서 사라진다', async () => {
    const created = await repository.create(DRAFT)
    await expect(repository.remove(created.id)).resolves.toBe(true)
    await expect(repository.list()).resolves.toHaveLength(0)
    await expect(repository.remove('없는-id')).resolves.toBe(false)
  })

  it('없는 장소를 수정하면 에러', async () => {
    await expect(repository.update('없는-id', { name: 'x' })).rejects.toThrow(PlaceRepositoryError)
  })
})

describe('저장 전 검증', () => {
  it('장소명이 비어 있으면 저장하지 않는다', async () => {
    await expect(repo().create({ ...DRAFT, name: '  ' })).rejects.toMatchObject({
      code: 'invalid_name',
    })
  })

  it('좌표가 유효하지 않으면 저장하지 않는다', async () => {
    await expect(repo().create({ ...DRAFT, latitude: 'abc' })).rejects.toMatchObject({
      code: 'invalid_coordinate',
    })
    await expect(repo().create({ ...DRAFT, longitude: 200 })).rejects.toMatchObject({
      code: 'invalid_coordinate',
    })
  })

  it('같은 이름 + 같은 위치는 중복 등록을 막는다', async () => {
    const repository = repo()
    await repository.create(DRAFT)
    await expect(repository.create(DRAFT)).rejects.toMatchObject({ code: 'duplicate_place' })
  })

  it('이름이 같아도 위치가 다르면 등록된다', async () => {
    const repository = repo()
    await repository.create(DRAFT)
    await repository.create({ ...DRAFT, latitude: 37.6, longitude: 127.1 })
    await expect(repository.list()).resolves.toHaveLength(2)
  })

  it('공급자 필드가 없던 기존 저장 데이터도 그대로 살아난다', () => {
    // 이 필드가 생기기 전에 저장된 레코드. 마이그레이션 없이 manual로 읽혀야 합니다.
    const legacy = { ...DRAFT, id: 'place_old', createdAt: '2026-01-01T00:00:00.000Z' }
    expect(normalizePlace(legacy, { id: legacy.id, createdAt: legacy.createdAt })).toMatchObject({
      id: 'place_old',
      provider: 'manual',
      providerPlaceId: '',
      createdAt: '2026-01-01T00:00:00.000Z',
    })
  })

  it('공급자 장소 ID가 같으면 이름과 좌표가 달라도 같은 가게로 본다', async () => {
    const repository = repo()
    await repository.create({ ...DRAFT, provider: 'kakao', providerPlaceId: '1234567' })

    // 한 사람은 '디어 모먼트', 다른 사람은 '디어모먼트'로 검색해도 같은 가게를 고른 상황
    await expect(
      repository.create({
        ...DRAFT,
        name: '디어모먼트',
        latitude: 37.6,
        longitude: 127.1,
        provider: 'kakao',
        providerPlaceId: '1234567',
      }),
    ).rejects.toMatchObject({ code: 'duplicate_place' })
  })

  it('공급자 장소 ID가 다르면 같은 좌표여도 각각 등록된다 (같은 건물 다른 층)', async () => {
    const repository = repo()
    await repository.create({ ...DRAFT, name: '2층 카페', provider: 'kakao', providerPlaceId: '111' })
    await repository.create({ ...DRAFT, name: '3층 술집', provider: 'kakao', providerPlaceId: '222' })
    await expect(repository.list()).resolves.toHaveLength(2)
  })

  it('공급자가 다르면 장소 ID가 같아도 다른 가게로 본다', async () => {
    const repository = repo()
    await repository.create({ ...DRAFT, provider: 'kakao', providerPlaceId: '1' })
    await repository.create({ ...DRAFT, provider: 'google', providerPlaceId: '1' })
    await expect(repository.list()).resolves.toHaveLength(2)
  })

  it('공급자 장소 ID가 없으면 provider는 manual로 되돌아간다', () => {
    expect(normalizePlace({ ...DRAFT, provider: 'kakao' })).toMatchObject({
      provider: 'manual',
      providerPlaceId: '',
    })
  })

  it('숫자로 들어온 공급자 장소 ID도 문자열로 저장된다', () => {
    expect(normalizePlace({ ...DRAFT, provider: 'kakao', providerPlaceId: 1234567 })).toMatchObject({
      provider: 'kakao',
      providerPlaceId: '1234567',
    })
  })

  it('공급자로 등록된 가게를 수기로 또 등록하면 이름 + 좌표로 잡는다', async () => {
    const repository = repo()
    await repository.create({ ...DRAFT, provider: 'kakao', providerPlaceId: '1234567' })
    await expect(repository.create(DRAFT)).rejects.toMatchObject({ code: 'duplicate_place' })
  })

  it('수정 결과가 다른 장소와 같은 가게가 되면 막는다', async () => {
    const repository = repo()
    await repository.create({ ...DRAFT, provider: 'kakao', providerPlaceId: '111' })
    const second = await repository.create({
      ...DRAFT,
      name: '다른 가게',
      latitude: 37.6,
      longitude: 127.1,
      provider: 'kakao',
      providerPlaceId: '222',
    })

    await expect(repository.update(second.id, { providerPlaceId: '111' })).rejects.toMatchObject({
      code: 'duplicate_place',
    })
  })

  it('커플 점수와 하트 등급은 리뷰로부터 다시 계산된다', () => {
    const place = normalizePlace({
      ...DRAFT,
      coupleScore: 0.1, // 화면에서 잘못 들어온 값
      reviews: [
        { userId: 'him', atmosphere: 5, taste: 5, value: 5, service: 5 },
        { userId: 'her', atmosphere: 4, taste: 4, value: 4, service: 4 },
      ],
    })

    expect(place.coupleScore).toBe(4.5)
    expect(place.heartGrade).toBe('good')
  })
})

describe('손상된 데이터 복구', () => {
  it('깨진 JSON이 있어도 seed로 시작한다', async () => {
    window.localStorage.setItem(STORAGE_KEYS.places, '{이건 JSON이 아님')

    const repository = new LocalPlaceRepository({
      seedFactory: () => [{ ...DRAFT, id: 'seed_1' }],
    })

    const list = await repository.list()
    expect(list).toHaveLength(1)
    expect(list[0].id).toBe('seed_1')
  })

  it('배열 안에 깨진 레코드가 있어도 나머지는 살린다', async () => {
    window.localStorage.setItem(
      STORAGE_KEYS.places,
      JSON.stringify([
        { ...DRAFT, id: 'ok_1' },
        { id: 'broken', name: '', latitude: 'x' },
      ]),
    )

    const list = await new LocalPlaceRepository().list()
    expect(list).toHaveLength(1)
    expect(list[0].id).toBe('ok_1')
  })

  it('reset({seed:false}) 하면 빈 목록이 된다', async () => {
    const repository = repo()
    await repository.create(DRAFT)
    await repository.reset({ seed: false })
    await expect(repository.list()).resolves.toHaveLength(0)
  })
})

describe('Repository 인터페이스', () => {
  it('기본 클래스는 구현하지 않으면 실패한다', async () => {
    const base = new PlaceRepository()
    await expect(base.list()).rejects.toMatchObject({ code: 'not_implemented' })
  })

  it('아직 연동하지 않은 API 메서드는 준비되지 않았음을 명시적으로 알린다', async () => {
    const api = new ApiPlaceRepository({ baseUrl: 'https://example.test' })
    await expect(api.get('place-1')).rejects.toMatchObject({ code: 'backend_not_ready' })
  })
})

describe('장소 목록 API', () => {
  it('GET /api/places 응답을 화면의 장소 목록으로 변환한다', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        status: 200,
        message: '조회했습니다',
        data: {
          content: [
            {
              placeId: 412,
              provider: 'KAKAO',
              providerPlaceId: 'DEMO-412',
              name: '○○찻집',
              address: '서울 종로구 인사동길',
              category: '카페',
              latitude: 37.5741,
              longitude: 126.9853,
            },
            { placeId: 999, name: '', latitude: null, longitude: null },
          ],
          page: 0,
          size: 10,
          totalElements: 2,
        },
      }),
    })
    const api = new ApiPlaceRepository({
      baseUrl: 'https://demo.mock.pstmn.io',
      fetchImpl,
    })

    await expect(api.list()).resolves.toMatchObject([
      {
        id: '412',
        provider: 'kakao',
        providerPlaceId: 'DEMO-412',
        name: '○○찻집',
        tags: [],
        reviews: [],
      },
    ])
    const [url, request] = fetchImpl.mock.calls[0]
    expect(String(url)).toBe('https://demo.mock.pstmn.io/api/places')
    expect(request).toMatchObject({
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: 'Bearer mock-token' },
    })
  })

  it('content가 없는 성공 응답을 거부한다', async () => {
    const api = new ApiPlaceRepository({
      baseUrl: 'https://example.test',
      fetchImpl: vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ status: 200, data: {} }),
      }),
    })

    await expect(api.list()).rejects.toMatchObject({ code: 'invalid_response' })
  })

  it('HTTP 오류의 API 메시지를 전달한다', async () => {
    const api = new ApiPlaceRepository({
      baseUrl: 'https://example.test',
      fetchImpl: vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({ message: '잠시 후 다시 시도해주세요' }),
      }),
    })

    await expect(api.list()).rejects.toMatchObject({
      code: 'http_error',
      message: '잠시 후 다시 시도해주세요',
    })
  })
})
