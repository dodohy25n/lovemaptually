import { test, expect } from '@playwright/test'
import { openApp, createPlace, readStoredPlaces } from './helpers.js'

const NEW_PLACE = {
  name: '망원 한강공원',
  address: '서울 마포구 마포나루길 467',
  category: '데이트 코스',
  visitedAt: '2026-03-15',
  latitude: 37.5525,
  longitude: 126.8964,
  score: 4.5,
  tags: '노을, 산책',
}

test.describe('2. 방문 장소 등록', () => {
  test('폼으로 등록하면 지도에 하트 핀이 나타난다', async ({ page }) => {
    await openApp(page)
    await createPlace(page, NEW_PLACE)

    const stored = await readStoredPlaces(page)
    const created = stored.find((place) => place.name === NEW_PLACE.name)
    expect(created).toBeTruthy()
    expect(created.latitude).toBeCloseTo(NEW_PLACE.latitude, 4)
    expect(created.longitude).toBeCloseTo(NEW_PLACE.longitude, 4)
    expect(created.heartGrade).toBe('good')

    const pin = page.getByTestId(`map-pin-${created.id}`)
    await expect(pin).toBeVisible()
    await expect(pin.getByTestId('pin-score')).toHaveText('4.5')

    await expect(page.getByTestId('place-count')).toHaveText('4')
  })

  test('지도를 클릭해 좌표를 찍을 수 있다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('add-place').click()
    await page.getByTestId('pick-on-map').click()
    await expect(page.getByTestId('place-form')).toBeHidden()

    await page.getByTestId('map-canvas').click({ position: { x: 300, y: 220 } })
    await expect(page.getByTestId('place-form')).toBeVisible()

    // 찍은 좌표가 폼에 채워진다
    await expect(page.getByTestId('field-latitude')).not.toHaveValue('')
    await expect(page.getByTestId('field-longitude')).not.toHaveValue('')
  })

  test('좌표가 유효하지 않으면 저장되지 않는다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('add-place').click()
    await page.getByTestId('field-name').fill('잘못된 좌표')
    await page.getByTestId('field-latitude').fill('999')
    await page.getByTestId('field-longitude').fill('abc')
    await page.getByTestId('place-form-submit').click()

    await expect(page.getByTestId('place-form')).toBeVisible()
    await expect(page.getByText('위도는 -90 ~ 90 사이의 숫자여야 해요.')).toBeVisible()
    await expect(page.getByText('경도는 -180 ~ 180 사이의 숫자여야 해요.')).toBeVisible()
  })

  test('같은 위치에 같은 이름은 중복 등록되지 않는다', async ({ page }) => {
    await openApp(page)
    await createPlace(page, NEW_PLACE)
    await page.getByTestId('detail-close').click()

    await page.getByTestId('add-place').click()
    await page.getByTestId('field-name').fill(NEW_PLACE.name)
    await page.getByTestId('field-latitude').fill(String(NEW_PLACE.latitude))
    await page.getByTestId('field-longitude').fill(String(NEW_PLACE.longitude))
    await page.getByTestId('place-form-submit').click()

    await expect(page.getByTestId('place-form-error')).toContainText('이미 같은 위치에 등록된 장소')
  })
})

test.describe('3. 새로고침 후 데이터 유지', () => {
  test('등록한 장소·좌표·점수·리뷰가 새로고침 후에도 남는다', async ({ page }) => {
    await openApp(page)
    await createPlace(page, NEW_PLACE)

    // 상세 패널에서 리뷰까지 작성
    await expect(page.getByTestId('place-detail')).toBeVisible()
    await page.getByTestId('review-edit-him').click()
    await page.getByTestId('review-content-him').fill('노을이 정말 좋았다.')
    await page.getByTestId('review-save-him').click()
    await expect(page.getByTestId('review-form-him')).toBeHidden()

    await page.reload()
    await expect(page.getByTestId('map-canvas')).toBeVisible()

    const stored = await readStoredPlaces(page)
    const restored = stored.find((place) => place.name === NEW_PLACE.name)

    expect(restored.latitude).toBeCloseTo(NEW_PLACE.latitude, 4)
    expect(restored.longitude).toBeCloseTo(NEW_PLACE.longitude, 4)
    expect(restored.visitedAt).toBe(NEW_PLACE.visitedAt)
    expect(restored.reviews).toHaveLength(1)
    expect(restored.reviews[0].content).toBe('노을이 정말 좋았다.')

    // 화면에도 같은 핀이 다시 보인다
    await expect(page.getByTestId(`map-pin-${restored.id}`)).toBeVisible()
    await expect(page.getByTestId('place-count')).toHaveText('4')
  })
})
