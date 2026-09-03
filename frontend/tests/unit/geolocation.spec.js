import { describe, it, expect, afterEach, vi } from 'vitest'
import { getCurrentPosition, isGeolocationAvailable } from '@/services/geolocation.js'

/** navigator.geolocation 을 흉내 냅니다. */
function stubGeolocation(impl) {
  Object.defineProperty(navigator, 'geolocation', {
    value: { getCurrentPosition: impl },
    configurable: true,
  })
}

function stubSecureContext(value) {
  Object.defineProperty(window, 'isSecureContext', { value, configurable: true })
}

describe('현재 위치 조회', () => {
  afterEach(() => {
    delete navigator.geolocation
    stubSecureContext(true)
    vi.restoreAllMocks()
  })

  it('좌표를 위도·경도로 정리해 돌려준다', async () => {
    stubSecureContext(true)
    stubGeolocation((success) =>
      success({ coords: { latitude: 37.4979, longitude: 127.0276, accuracy: 12 } }),
    )
    await expect(getCurrentPosition()).resolves.toEqual({
      latitude: 37.4979,
      longitude: 127.0276,
      accuracy: 12,
    })
  })

  it('지원하지 않는 브라우저는 unsupported로 알린다', async () => {
    delete navigator.geolocation
    expect(isGeolocationAvailable()).toBe(false)
    await expect(getCurrentPosition()).rejects.toMatchObject({ code: 'unsupported' })
  })

  it('https가 아니면 권한 창을 띄우기 전에 insecure로 알린다', async () => {
    stubSecureContext(false)
    const spy = vi.fn()
    stubGeolocation(spy)
    await expect(getCurrentPosition()).rejects.toMatchObject({ code: 'insecure' })
    // 브라우저가 어차피 막으므로 호출조차 하지 않습니다.
    expect(spy).not.toHaveBeenCalled()
  })

  it('브라우저 오류 코드를 이름으로 바꾼다', async () => {
    stubSecureContext(true)
    for (const [code, expected] of [
      [1, 'denied'],
      [2, 'unavailable'],
      [3, 'timeout'],
      [undefined, 'unavailable'],
    ]) {
      stubGeolocation((success, failure) => failure({ code, message: 'x' }))
      await expect(getCurrentPosition()).rejects.toMatchObject({ code: expected })
    }
  })
})
