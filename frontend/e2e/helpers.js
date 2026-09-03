import { expect } from '@playwright/test'

export const STORAGE_KEY = 'love-maptually:places:v1'

/**
 * E2E는 외부 네트워크 상태에 영향받지 않아야 합니다.
 * 지도 타일과 폰트 등 외부 요청은 전부 가로채고, 타일은 로컬에서 만든 1x1 PNG로 응답합니다.
 */
const TILE_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

export async function stubExternalRequests(page, { failTiles = false } = {}) {
  await page.route('**://*.tile.openstreetmap.org/**', async (route) => {
    if (failTiles) return route.abort('failed')
    return route.fulfill({ status: 200, contentType: 'image/png', body: TILE_PNG })
  })
  await page.route('**://*.basemaps.cartocdn.com/**', async (route) => {
    if (failTiles) return route.abort('failed')
    return route.fulfill({ status: 200, contentType: 'image/png', body: TILE_PNG })
  })
  // 폰트 등 나머지 외부 요청은 빈 응답으로 가로챕니다.
  // abort()로 끊으면 브라우저가 콘솔에 ERR_FAILED를 남겨, '콘솔 오류 없음' 검증과 충돌합니다.
  // 빈 CSS를 돌려주면 @font-face 자체가 없어져 gstatic 요청도 발생하지 않습니다.
  await page.route(/^https?:\/\/(?!127\.0\.0\.1|localhost)/, (route) =>
    route.fulfill({ status: 200, contentType: 'text/css', body: '' }),
  )
}

/** 저장소를 비운 상태로 앱을 엽니다. 기본은 seed 데이터가 채워진 상태. */
export async function openApp(page, { storage = null, failTiles = false, path = '/map' } = {}) {
  await stubExternalRequests(page, { failTiles })

  if (storage !== null) {
    await page.addInitScript(
      ([key, value]) => {
        window.localStorage.setItem(key, value)
      },
      [STORAGE_KEY, storage],
    )
  }

  await page.goto(path)
  // 지도 화면일 때만 지도가 뜰 때까지 기다립니다 (리뷰·기억 화면에는 지도가 없습니다).
  if (path === '/map') {
    await expect(page.getByTestId('map-canvas')).toBeVisible()
  } else {
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  }
}

/** 저장소에 들어 있는 장소 목록을 읽습니다. */
export async function readStoredPlaces(page) {
  const raw = await page.evaluate((key) => window.localStorage.getItem(key), STORAGE_KEY)
  return raw ? JSON.parse(raw) : null
}

/** 장소 등록 폼을 채우고 저장합니다. */
export async function createPlace(page, place) {
  await page.getByTestId('add-place').click()
  await expect(page.getByTestId('place-form')).toBeVisible()

  await page.getByTestId('field-name').fill(place.name)
  if (place.address) await page.getByTestId('field-address').fill(place.address)
  if (place.category) await page.getByTestId('field-category').selectOption(place.category)
  if (place.visitedAt) await page.getByTestId('field-visited-at').fill(place.visitedAt)
  await page.getByTestId('field-latitude').fill(String(place.latitude))
  await page.getByTestId('field-longitude').fill(String(place.longitude))
  if (place.score !== undefined) await page.getByTestId('field-score').fill(String(place.score))
  if (place.tags) await page.getByTestId('field-tags').fill(place.tags)

  await page.getByTestId('place-form-submit').click()
  await expect(page.getByTestId('place-form')).toBeHidden()
}
