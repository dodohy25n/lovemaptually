import { describe, it, expect } from 'vitest'
import {
  isValidLatitude,
  isValidLongitude,
  isValidCoordinate,
  normalizeCoordinate,
  isSameSpot,
} from '@/utils/coords.js'

describe('좌표 유효성 검사', () => {
  it('위도는 -90 ~ 90만 허용한다', () => {
    expect(isValidLatitude(37.5)).toBe(true)
    expect(isValidLatitude(-90)).toBe(true)
    expect(isValidLatitude(90)).toBe(true)
    expect(isValidLatitude(90.1)).toBe(false)
    expect(isValidLatitude(-91)).toBe(false)
  })

  it('경도는 -180 ~ 180만 허용한다', () => {
    expect(isValidLongitude(126.9)).toBe(true)
    expect(isValidLongitude(180)).toBe(true)
    expect(isValidLongitude(181)).toBe(false)
  })

  it('숫자로 해석할 수 없는 값은 거부한다', () => {
    for (const bad of ['', '  ', 'abc', null, undefined, true, [], {}, NaN, Infinity]) {
      expect(isValidLatitude(bad)).toBe(false)
      expect(isValidLongitude(bad)).toBe(false)
    }
  })

  it('문자열 좌표는 Number로 정규화한다', () => {
    const result = normalizeCoordinate('37.5626', ' 126.9256 ')
    expect(result).toEqual({ latitude: 37.5626, longitude: 126.9256 })
    expect(typeof result.latitude).toBe('number')
    expect(typeof result.longitude).toBe('number')
  })

  it('유효하지 않으면 null을 반환한다', () => {
    expect(normalizeCoordinate('abc', 126)).toBeNull()
    expect(normalizeCoordinate(37, '')).toBeNull()
    expect(isValidCoordinate(37, 300)).toBe(false)
  })

  it('거의 같은 좌표는 같은 지점으로 본다', () => {
    const a = { latitude: 37.5626, longitude: 126.9256 }
    expect(isSameSpot(a, { latitude: 37.56262, longitude: 126.92561 })).toBe(true)
    expect(isSameSpot(a, { latitude: 37.57, longitude: 126.9256 })).toBe(false)
    expect(isSameSpot(a, null)).toBe(false)
  })
})
