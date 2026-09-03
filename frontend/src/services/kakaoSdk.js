/**
 * 카카오 지도 SDK 로더.
 *
 * 지도(MapCanvas)와 가게 검색(placeSearchApi)이 같은 스크립트를 씁니다.
 * 여기 한 곳에서만 불러와 스크립트 태그가 두 번 붙지 않게 합니다.
 *
 * ⚠️ 여기서 쓰는 키는 카카오 **JavaScript 키**입니다(`VITE_KAKAO_JS_KEY`).
 *    브라우저에 노출되므로 카카오 콘솔에서 반드시 도메인 제한을 걸어야 합니다.
 *    등록되지 않은 도메인에서 부르면 SDK 대신 401 JSON이 오고, 크롬이 그것을
 *    ERR_BLOCKED_BY_ORB 로 막아 script.onerror 로 떨어집니다.
 *
 * 실패해도 앱은 죽지 않습니다. 지도는 타일 없는 대체 화면으로, 검색은 직접 입력으로
 * 각각 물러납니다.
 */
import { config } from './config.js'

const SCRIPT_ID = 'kakao-maps-sdk'
const LOAD_TIMEOUT_MS = 8000

export class KakaoSdkError extends Error {
  constructor(message, code = 'sdk_unavailable') {
    super(message)
    this.name = 'KakaoSdkError'
    this.code = code
  }
}

/** 카카오 기능(지도·검색)을 쓸 수 있는 상태인지. 키가 없으면 전부 대체 경로로 갑니다. */
export function isKakaoConfigured() {
  return Boolean(config.kakaoJsKey)
}

let sdkPromise = null

/**
 * `kakao.maps` 네임스페이스를 돌려줍니다. 여러 번 불러도 스크립트는 한 번만 받습니다.
 *
 * @throws {KakaoSdkError} no_key | sdk_unavailable
 */
export function loadKakaoMaps() {
  if (sdkPromise) return sdkPromise

  sdkPromise = new Promise((resolve, reject) => {
    if (!isKakaoConfigured()) {
      reject(new KakaoSdkError('카카오 JavaScript 키가 설정되지 않았습니다.', 'no_key'))
      return
    }
    if (typeof document === 'undefined') {
      reject(new KakaoSdkError('브라우저 환경이 아닙니다.'))
      return
    }

    // 응답이 오지 않아도 사용자를 붙잡아두지 않도록 항상 시간 제한을 둡니다.
    const timer = setTimeout(() => fail(new KakaoSdkError('카카오 지도 SDK 응답이 없습니다.')), LOAD_TIMEOUT_MS)

    function done(maps) {
      clearTimeout(timer)
      resolve(maps)
    }
    function fail(error) {
      clearTimeout(timer)
      // 다음 시도에서 처음부터 다시 받을 수 있도록 캐시와 스크립트 태그를 모두 비웁니다.
      // 태그를 남겨두면 재시도가 '이미 있음'으로 오판해 영영 다시 요청하지 않습니다.
      sdkPromise = null
      document.getElementById(SCRIPT_ID)?.remove()
      reject(error)
    }

    function whenReady() {
      const maps = window.kakao?.maps
      if (!maps?.load) {
        fail(new KakaoSdkError('카카오 지도 SDK를 불러오지 못했습니다.'))
        return
      }
      // autoload=false 이므로 실제 모듈은 load() 이후에 준비됩니다.
      maps.load(() => {
        if (!window.kakao?.maps?.Map) {
          fail(new KakaoSdkError('카카오 지도 모듈을 사용할 수 없습니다.'))
          return
        }
        done(window.kakao.maps)
      })
    }

    // 이미 받아 둔 SDK가 있으면 그대로 씁니다.
    if (window.kakao?.maps?.load) {
      whenReady()
      return
    }
    // 여기 도달했다는 건 이전 시도가 실패했다는 뜻이므로(성공했다면 sdkPromise가 반환됨)
    // 남아 있는 태그는 실패한 잔재입니다. 지우고 처음부터 다시 받습니다.
    document.getElementById(SCRIPT_ID)?.remove()

    const script = document.createElement('script')
    script.id = SCRIPT_ID
    script.async = true
    script.src =
      'https://dapi.kakao.com/v2/maps/sdk.js' +
      `?appkey=${encodeURIComponent(config.kakaoJsKey)}&libraries=services&autoload=false`
    script.onload = whenReady
    script.onerror = () => fail(new KakaoSdkError('카카오 지도 SDK를 불러오지 못했습니다.'))
    document.head.appendChild(script)
  })

  return sdkPromise
}

/** 테스트에서 로딩 캐시를 초기화합니다. */
export function resetKakaoSdkForTests() {
  sdkPromise = null
  document?.getElementById(SCRIPT_ID)?.remove()
}
