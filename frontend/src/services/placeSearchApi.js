/**
 * 카카오 장소 검색.
 *
 * 리뷰는 '가게'에 붙으므로, 사용자가 이름을 타이핑하는 대신 여기서 고른 결과의
 * 공급자 장소 ID(`providerPlaceId`)를 저장해야 같은 가게가 두 갈래로 갈라지지 않습니다.
 * 동일성 판정 규칙은 `utils/placeIdentity.js` 참고.
 *
 * ⚠️ 여기서 쓰는 키는 카카오 **JavaScript 키**입니다(`VITE_KAKAO_JS_KEY`).
 *    브라우저에 노출되므로 카카오 콘솔에서 반드시 도메인 제한을 걸어야 합니다.
 *    서버 권한을 가진 REST API 키는 절대 이 경로로 다루지 마세요.
 *
 * 키가 없거나 SDK를 못 불러와도 앱은 죽지 않습니다. 검색만 조용히 비활성화되고
 * 사용자는 기존처럼 직접 입력할 수 있습니다 (그 경우 provider 는 'manual').
 */
import { loadKakaoMaps, isKakaoConfigured, resetKakaoSdkForTests } from './kakaoSdk.js'
import { normalizeCoordinate } from '@/utils/coords.js'

export class PlaceSearchError extends Error {
  constructor(message, code = 'search_failed') {
    super(message)
    this.name = 'PlaceSearchError'
    this.code = code
  }
}

/** 검색 UI를 띄울 수 있는 상태인지. 키가 없으면 직접 입력만 제공합니다. */
export function isSearchAvailable() {
  return isKakaoConfigured()
}

/**
 * 카카오 카테고리 그룹 코드 → 이 앱의 카테고리.
 * 매핑에 없는 코드는 '기타'로 두고 사용자가 직접 고치게 합니다.
 */
const CATEGORY_BY_GROUP_CODE = {
  CE7: '카페',
  FD6: '맛집',
  AT4: '데이트 코스', // 관광명소
  CT1: '데이트 코스', // 문화시설
}

export function categoryFromKakao(groupCode) {
  return CATEGORY_BY_GROUP_CODE[groupCode] ?? '기타'
}

/**
 * 카카오 검색 결과 한 건을 이 앱의 장소 draft 형태로 변환합니다.
 * 좌표를 못 읽으면 지도에 찍을 수 없으므로 null을 반환하고 호출부에서 걸러냅니다.
 */
export function toPlaceDraft(item) {
  // 카카오는 x가 경도(longitude), y가 위도(latitude)입니다. 순서를 바꾸면 엉뚱한 곳에 찍힙니다.
  const coordinate = normalizeCoordinate(item?.y, item?.x)
  const providerPlaceId = String(item?.id ?? '').trim()
  if (!coordinate || !providerPlaceId) return null

  return {
    provider: 'kakao',
    providerPlaceId,
    name: String(item?.place_name ?? '').trim(),
    // 도로명 주소가 있으면 그쪽이 더 정확합니다.
    address: String(item?.road_address_name || item?.address_name || '').trim(),
    category: categoryFromKakao(item?.category_group_code),
    latitude: coordinate.latitude,
    longitude: coordinate.longitude,
    // 아래 둘은 검색 결과 목록에 보여주기만 하고 저장하지는 않습니다.
    categoryName: String(item?.category_name ?? '').trim(),
    placeUrl: String(item?.place_url ?? '').trim(),
  }
}

/**
 * 검색에 쓰는 `kakao.maps.services` 네임스페이스.
 * SDK 로딩 자체는 kakaoSdk.js가 맡습니다(지도와 같은 스크립트를 공유합니다).
 */
async function loadSearchServices() {
  const maps = await loadKakaoMaps()
  if (!maps.services?.Places) {
    throw new PlaceSearchError('장소 검색 라이브러리를 사용할 수 없습니다.', 'sdk_unavailable')
  }
  return maps.services
}

/**
 * 키워드로 가게를 검색합니다.
 *
 * @returns {Promise<Array>} toPlaceDraft() 형태의 결과 목록 (없으면 빈 배열)
 * @throws {PlaceSearchError} no_key | sdk_unavailable | search_failed
 */
export async function searchPlaces(keyword, { size = 10 } = {}) {
  const query = String(keyword ?? '').trim()
  if (!query) return []

  const services = await loadSearchServices()

  return new Promise((resolve, reject) => {
    const places = new services.Places()
    places.keywordSearch(
      query,
      (data, status) => {
        if (status === services.Status.ZERO_RESULT) {
          resolve([])
          return
        }
        if (status !== services.Status.OK) {
          reject(new PlaceSearchError('장소 검색에 실패했습니다.', 'search_failed'))
          return
        }
        // 좌표가 깨진 결과는 지도에 찍을 수 없으므로 조용히 버립니다.
        resolve(data.map(toPlaceDraft).filter(Boolean))
      },
      { size },
    )
  })
}

/** 테스트에서 SDK 로딩 캐시를 초기화합니다. */
export function resetSdkForTests() {
  resetKakaoSdkForTests()
}
