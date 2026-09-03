import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  categoryFromKakao,
  toPlaceDraft,
  searchPlaces,
  isSearchAvailable,
  resetSdkForTests,
} from '@/services/placeSearchApi.js'
import { config } from '@/services/config.js'
import { normalizePlace } from '@/services/placeRepository.js'

/** 카카오 keywordSearch 응답 한 건의 형태. */
const KAKAO_ITEM = {
  id: '1234567',
  place_name: '디어 모먼트',
  address_name: '서울 강남구 역삼동 123',
  road_address_name: '서울 강남구 테헤란로 123',
  category_name: '음식점 > 카페',
  category_group_code: 'CE7',
  phone: '02-000-0000',
  place_url: 'http://place.map.kakao.com/1234567',
  x: '127.0276', // 경도
  y: '37.4979', // 위도
}

/** window.kakao 를 흉내 냅니다. SDK 스크립트는 실제로 받지 않습니다. */
function stubKakaoSdk({ status = 'OK', data = [KAKAO_ITEM] } = {}) {
  const Status = { OK: 'OK', ZERO_RESULT: 'ZERO_RESULT', ERROR: 'ERROR' }
  window.kakao = {
    maps: {
      load: (callback) => callback(),
      // 로더는 지도와 검색이 공유하므로 지도 모듈(Map)도 있어야 준비된 것으로 봅니다.
      Map: class {},
      services: {
        Status,
        Places: class {
          keywordSearch(keyword, callback) {
            callback(data, status)
          }
        },
      },
    },
  }
  // window.kakao 가 이미 있으면 로더는 스크립트를 새로 받지 않고 그대로 씁니다.
}

describe('카카오 카테고리 매핑', () => {
  it('카페와 음식점은 앱의 카테고리로 바뀐다', () => {
    expect(categoryFromKakao('CE7')).toBe('카페')
    expect(categoryFromKakao('FD6')).toBe('맛집')
  })

  it('관광명소와 문화시설은 데이트 코스로 묶는다', () => {
    expect(categoryFromKakao('AT4')).toBe('데이트 코스')
    expect(categoryFromKakao('CT1')).toBe('데이트 코스')
  })

  it('모르는 코드는 기타로 둔다', () => {
    expect(categoryFromKakao('BK9')).toBe('기타')
    expect(categoryFromKakao(undefined)).toBe('기타')
  })
})

describe('검색 결과 → 장소 draft', () => {
  it('공급자 식별자와 좌표를 채운다', () => {
    expect(toPlaceDraft(KAKAO_ITEM)).toMatchObject({
      provider: 'kakao',
      providerPlaceId: '1234567',
      name: '디어 모먼트',
      category: '카페',
      latitude: 37.4979,
      longitude: 127.0276,
    })
  })

  it('카카오의 x/y를 경도/위도로 올바르게 읽는다', () => {
    // x와 y를 바꿔 읽으면 서울이 아니라 엉뚱한 곳에 찍힙니다.
    const draft = toPlaceDraft(KAKAO_ITEM)
    expect(draft.latitude).toBeCloseTo(37.4979, 4)
    expect(draft.longitude).toBeCloseTo(127.0276, 4)
  })

  it('도로명 주소가 있으면 그쪽을 쓴다', () => {
    expect(toPlaceDraft(KAKAO_ITEM).address).toBe('서울 강남구 테헤란로 123')
  })

  it('도로명 주소가 없으면 지번 주소로 대체한다', () => {
    const draft = toPlaceDraft({ ...KAKAO_ITEM, road_address_name: '' })
    expect(draft.address).toBe('서울 강남구 역삼동 123')
  })

  it('좌표나 ID가 없으면 버린다', () => {
    expect(toPlaceDraft({ ...KAKAO_ITEM, x: '', y: '' })).toBeNull()
    expect(toPlaceDraft({ ...KAKAO_ITEM, id: '' })).toBeNull()
  })

  it('그대로 저장하면 공급자 식별자가 살아남는다', () => {
    const place = normalizePlace(toPlaceDraft(KAKAO_ITEM))
    expect(place).toMatchObject({ provider: 'kakao', providerPlaceId: '1234567' })
  })
})

describe('장소 검색', () => {
  const originalKey = config.kakaoJsKey
  const originalApiBaseUrl = config.apiBaseUrl

  beforeEach(() => {
    config.kakaoJsKey = 'x'.repeat(32)
    config.apiBaseUrl = ''
    resetSdkForTests()
  })

  afterEach(() => {
    config.kakaoJsKey = originalKey
    config.apiBaseUrl = originalApiBaseUrl
    delete window.kakao
    resetSdkForTests()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('키가 없으면 검색을 제공하지 않는다', () => {
    config.kakaoJsKey = ''
    expect(isSearchAvailable()).toBe(false)
  })

  it('빈 키워드는 네트워크를 타지 않는다', async () => {
    await expect(searchPlaces('   ')).resolves.toEqual([])
  })

  it('결과를 draft 목록으로 돌려준다', async () => {
    stubKakaoSdk()
    const results = await searchPlaces('디어 모먼트')
    expect(results).toHaveLength(1)
    expect(results[0]).toMatchObject({ provider: 'kakao', providerPlaceId: '1234567' })
  })

  it('결과가 없으면 빈 배열이다', async () => {
    stubKakaoSdk({ status: 'ZERO_RESULT', data: [] })
    await expect(searchPlaces('없는가게')).resolves.toEqual([])
  })

  it('검색이 실패하면 search_failed로 알린다', async () => {
    stubKakaoSdk({ status: 'ERROR', data: [] })
    await expect(searchPlaces('디어 모먼트')).rejects.toMatchObject({ code: 'search_failed' })
  })

  it('키가 없으면 no_key로 알린다', async () => {
    config.kakaoJsKey = ''
    await expect(searchPlaces('디어 모먼트')).rejects.toMatchObject({ code: 'no_key' })
  })

  it('SDK를 불러오지 못하면 sdk_unavailable로 알린다', async () => {
    // 스크립트가 차단되는 상황(도메인 미등록·오프라인·ORB)을 흉내 냅니다.
    vi.spyOn(document.head, 'appendChild').mockImplementation((node) => {
      if (node.tagName === 'SCRIPT') queueMicrotask(() => node.onerror?.(new Event('error')))
      return node
    })
    await expect(searchPlaces('디어 모먼트')).rejects.toMatchObject({ code: 'sdk_unavailable' })
  })

  it('한 번 실패해도 다음 시도에서 다시 받아온다', async () => {
    // 실패한 <script>가 남아 있으면 재시도가 '이미 있음'으로 오판해 영영 안 됩니다.
    const spy = vi.spyOn(document.head, 'appendChild').mockImplementation((node) => {
      if (node.tagName === 'SCRIPT') queueMicrotask(() => node.onerror?.(new Event('error')))
      return node
    })
    await expect(searchPlaces('디어 모먼트')).rejects.toMatchObject({ code: 'sdk_unavailable' })

    spy.mockRestore()
    stubKakaoSdk()
    await expect(searchPlaces('디어 모먼트')).resolves.toHaveLength(1)
  })

  it('좌표가 깨진 결과는 조용히 걸러낸다', async () => {
    stubKakaoSdk({ data: [KAKAO_ITEM, { ...KAKAO_ITEM, id: '999', x: 'abc', y: 'abc' }] })
    await expect(searchPlaces('디어 모먼트')).resolves.toHaveLength(1)
  })

  it('API 주소가 있으면 백엔드 장소 검색을 우선 사용한다', async () => {
    config.apiBaseUrl = 'https://example.test'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          content: [{
            placeId: 412,
            provider: 'KAKAO',
            providerPlaceId: 'DEMO-412',
            name: '○○찻집',
            region: '인사동',
            address: '서울 종로구 인사동길',
            category: '카페',
            priceBand: 2,
            latitude: 37.5741,
            longitude: 126.9853,
          }],
        },
      }),
    }))

    await expect(searchPlaces('찻집', { size: 5 })).resolves.toEqual([
      expect.objectContaining({ id: 412, provider: 'kakao', name: '○○찻집', latitude: 37.5741 }),
    ])
    const [url, request] = fetch.mock.calls[0]
    expect(String(url)).toContain('/api/places?query=%EC%B0%BB%EC%A7%91&page=0&size=5')
    expect(request.headers.Authorization).toBeUndefined()
  })

  it('Postman Mock 검색에는 Mock 인증 헤더를 보낸다', async () => {
    config.apiBaseUrl = 'https://demo.mock.pstmn.io'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: { content: [] } }) }))
    await searchPlaces('카페')
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer mock-token')
  })

  it('API 응답 형식이 잘못되면 search_failed 오류를 반환한다', async () => {
    config.apiBaseUrl = 'https://example.test'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: {} }) }))
    await expect(searchPlaces('카페')).rejects.toMatchObject({ code: 'search_failed' })
  })
})
