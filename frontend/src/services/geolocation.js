/**
 * 현재 위치 조회.
 *
 * 브라우저 Geolocation API를 감싸서, 실패 이유를 화면이 그대로 안내할 수 있는
 * 코드로 정리합니다. 실패해도 지도는 그대로 쓸 수 있어야 하므로 던지기만 하고
 * 상태는 호출부가 관리합니다.
 *
 * ⚠️ Geolocation은 **보안 컨텍스트(https 또는 localhost)에서만** 동작합니다.
 *    http로 배포하면 권한 창조차 뜨지 않고 조용히 막히므로 따로 안내합니다.
 */

const DEFAULT_TIMEOUT_MS = 8000

export class GeolocationError extends Error {
  constructor(message, code = 'unavailable') {
    super(message)
    this.name = 'GeolocationError'
    this.code = code
  }
}

/** 이 브라우저·페이지에서 위치 조회를 시도해 볼 수 있는지. */
export function isGeolocationAvailable() {
  return typeof navigator !== 'undefined' && Boolean(navigator.geolocation)
}

/**
 * 현재 위치를 한 번 조회합니다.
 *
 * @returns {Promise<{latitude: number, longitude: number, accuracy: number}>}
 * @throws {GeolocationError} unsupported | insecure | denied | unavailable | timeout
 */
export function getCurrentPosition({ timeout = DEFAULT_TIMEOUT_MS } = {}) {
  return new Promise((resolve, reject) => {
    if (!isGeolocationAvailable()) {
      reject(new GeolocationError('이 브라우저는 위치 조회를 지원하지 않습니다.', 'unsupported'))
      return
    }
    // https가 아니면 브라우저가 권한 창도 띄우지 않고 막습니다. 먼저 걸러 안내합니다.
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      reject(new GeolocationError('https 연결에서만 위치를 쓸 수 있습니다.', 'insecure'))
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude, accuracy } = position.coords
        resolve({ latitude, longitude, accuracy })
      },
      (error) => {
        reject(new GeolocationError(error?.message ?? '위치를 가져오지 못했습니다.', toCode(error)))
      },
      { enableHighAccuracy: true, timeout, maximumAge: 0 },
    )
  })
}

/** 브라우저의 숫자 코드를 화면에서 쓰기 쉬운 이름으로 바꿉니다. */
function toCode(error) {
  // 상수를 직접 참조하면 테스트 환경에 GeolocationPositionError가 없을 때 깨집니다.
  switch (error?.code) {
    case 1:
      return 'denied' // PERMISSION_DENIED
    case 2:
      return 'unavailable' // POSITION_UNAVAILABLE
    case 3:
      return 'timeout' // TIMEOUT
    default:
      return 'unavailable'
  }
}
