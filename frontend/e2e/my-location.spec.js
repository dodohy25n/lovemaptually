import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

/** 강남역 부근. Playwright가 브라우저에 흘려 넣는 가짜 좌표입니다. */
const GANGNAM = { latitude: 37.4979, longitude: 127.0276 }

test.describe('10. 내 위치', () => {
  test('버튼을 누르면 현재 위치 표시가 지도에 나타난다', async ({ page, context }) => {
    await context.grantPermissions(['geolocation'])
    await context.setGeolocation(GANGNAM)

    await openApp(page)
    await expect(page.getByTestId('map-my-location')).toBeHidden()

    await page.getByTestId('map-locate').click()

    await expect(page.getByTestId('map-my-location')).toBeVisible()
    await expect(page.getByTestId('map-locate-error')).toBeHidden()
  })

  test('현재 위치 표시는 지도 안에 놓인다', async ({ page, context }) => {
    await context.grantPermissions(['geolocation'])
    await context.setGeolocation(GANGNAM)

    await openApp(page)
    await page.getByTestId('map-locate').click()
    await expect(page.getByTestId('map-my-location')).toBeVisible()

    // 지도를 현재 위치로 옮겼으므로 표시가 지도 영역 안에 있어야 합니다.
    const map = await page.getByTestId('map-canvas').boundingBox()
    const dot = await page.getByTestId('map-my-location').boundingBox()
    expect(dot.x).toBeGreaterThanOrEqual(map.x)
    expect(dot.y).toBeGreaterThanOrEqual(map.y)
    expect(dot.x + dot.width).toBeLessThanOrEqual(map.x + map.width)
    expect(dot.y + dot.height).toBeLessThanOrEqual(map.y + map.height)
  })

  test('위치 권한이 없으면 안내하고 지도는 그대로 쓸 수 있다', async ({ page, context }) => {
    await context.clearPermissions()
    await openApp(page)

    await page.getByTestId('map-locate').click()

    await expect(page.getByTestId('map-locate-error')).toBeVisible()
    await expect(page.getByTestId('map-my-location')).toBeHidden()

    // 권한을 거부해도 기록한 장소와 핀은 그대로 동작해야 합니다.
    await expect(page.getByTestId('map-pins').locator('> div').first()).toBeVisible()
    await page.getByTestId('map-pin-place_seed_dear_moment').click()
    await expect(page.getByTestId('place-detail')).toBeVisible()
  })

  test('버튼에 접근성 라벨이 있다', async ({ page }) => {
    await openApp(page)
    await expect(page.getByTestId('map-locate')).toHaveAttribute('aria-label', '내 위치로 이동')
  })
})
