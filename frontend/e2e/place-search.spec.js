import { test, expect } from '@playwright/test'
import { openApp, readStoredPlaces } from './helpers.js'

/**
 * 가게 검색으로 등록하면 공급자 장소 ID가 함께 저장되는지 확인합니다.
 *
 * E2E는 외부 네트워크를 타지 않으므로 카카오 SDK도 가짜로 응답합니다.
 * (helpers.js가 외부 요청을 전부 가로채므로, 이 라우트를 나중에 등록해 우선순위를 가져옵니다.)
 */
const KAKAO_RESULTS = [
  {
    id: '1234567',
    place_name: '디어 모먼트',
    address_name: '서울 강남구 역삼동 123',
    road_address_name: '서울 강남구 테헤란로 123',
    category_name: '음식점 > 카페',
    category_group_code: 'CE7',
    place_url: 'http://place.map.kakao.com/1234567',
    x: '127.0276',
    y: '37.4979',
  },
]

function fakeSdk(results) {
  return `
    window.kakao = {
      maps: {
        load: function (cb) { cb() },
        // 로더는 지도와 검색이 같은 스크립트를 쓰므로 지도 모듈도 있어야 준비된 것으로 봅니다.
        // 지도는 이 라우트가 붙기 전에 이미 대체 엔진으로 물러난 뒤라 실제로 쓰이지는 않습니다.
        Map: function () {},
        services: {
          Status: { OK: 'OK', ZERO_RESULT: 'ZERO_RESULT', ERROR: 'ERROR' },
          Places: function () {
            this.keywordSearch = function (keyword, cb) {
              var data = ${JSON.stringify(results)}
              cb(data, data.length ? 'OK' : 'ZERO_RESULT')
            }
          },
        },
      },
    }
  `
}

async function stubKakaoSdk(page, results = KAKAO_RESULTS) {
  await page.route('**dapi.kakao.com/v2/maps/sdk.js*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: fakeSdk(results),
    }),
  )
}

test.describe('9. 가게 검색으로 장소 등록', () => {
  test('검색해서 고르면 공급자 장소 ID와 함께 저장된다', async ({ page }) => {
    await openApp(page, { storage: '[]' })
    await stubKakaoSdk(page)

    await page.getByTestId('add-place').click()
    await expect(page.getByTestId('place-form')).toBeVisible()

    await page.getByTestId('field-search').fill('디어 모먼트')
    await page.getByTestId('place-search-submit').click()

    await page.getByTestId('place-search-result-1234567').click()

    // 고른 결과가 아래 입력란에 그대로 채워집니다.
    await expect(page.getByTestId('field-name')).toHaveValue('디어 모먼트')
    await expect(page.getByTestId('field-address')).toHaveValue('서울 강남구 테헤란로 123')
    await expect(page.getByTestId('field-category')).toHaveValue('카페')
    await expect(page.getByTestId('place-picked-provider')).toBeVisible()

    await page.getByTestId('place-form-submit').click()
    await expect(page.getByTestId('place-form')).toBeHidden()

    const stored = await readStoredPlaces(page)
    expect(stored).toHaveLength(1)
    expect(stored[0]).toMatchObject({
      name: '디어 모먼트',
      provider: 'kakao',
      providerPlaceId: '1234567',
    })
    expect(stored[0].latitude).toBeCloseTo(37.4979, 4)
    expect(stored[0].longitude).toBeCloseTo(127.0276, 4)
  })

  test('같은 가게를 다시 등록하면 중복으로 막는다', async ({ page }) => {
    await openApp(page, { storage: '[]' })
    await stubKakaoSdk(page)

    for (const attempt of [1, 2]) {
      await page.getByTestId('add-place').click()
      await page.getByTestId('field-search').fill('디어 모먼트')
      await page.getByTestId('place-search-submit').click()
      await page.getByTestId('place-search-result-1234567').click()
      await page.getByTestId('place-form-submit').click()

      if (attempt === 1) {
        await expect(page.getByTestId('place-form')).toBeHidden()
      } else {
        // 두 번째는 저장되지 않고 폼에 남아 있어야 합니다.
        await expect(page.getByTestId('place-form')).toBeVisible()
      }
    }

    await expect(readStoredPlaces(page)).resolves.toHaveLength(1)
  })

  test('검색을 쓸 수 없으면 안내하고 직접 입력으로 등록할 수 있다', async ({ page }) => {
    // 카카오 SDK 라우트를 따로 두지 않으면 helpers의 외부 요청 차단에 걸립니다.
    await openApp(page, { storage: '[]' })

    await page.getByTestId('add-place').click()
    await page.getByTestId('field-search').fill('디어 모먼트')
    await page.getByTestId('place-search-submit').click()

    await expect(page.getByTestId('place-search-notice')).toBeVisible()

    // 검색이 죽어도 직접 입력 경로는 그대로 살아 있어야 합니다.
    await page.getByTestId('field-name').fill('직접 입력한 가게')
    await page.getByTestId('field-latitude').fill('37.5')
    await page.getByTestId('field-longitude').fill('127.0')
    await page.getByTestId('place-form-submit').click()
    await expect(page.getByTestId('place-form')).toBeHidden()

    const stored = await readStoredPlaces(page)
    expect(stored[0]).toMatchObject({
      name: '직접 입력한 가게',
      provider: 'manual',
      providerPlaceId: '',
    })
  })

  test("'연결 끊고 직접 입력'을 누르면 manual로 돌아간다", async ({ page }) => {
    await openApp(page, { storage: '[]' })
    await stubKakaoSdk(page)

    await page.getByTestId('add-place').click()
    await page.getByTestId('field-search').fill('디어 모먼트')
    await page.getByTestId('place-search-submit').click()
    await page.getByTestId('place-search-result-1234567').click()
    await expect(page.getByTestId('place-picked-provider')).toBeVisible()

    await page.getByTestId('place-picked-clear').click()
    await expect(page.getByTestId('place-picked-provider')).toBeHidden()

    await page.getByTestId('place-form-submit').click()
    await expect(page.getByTestId('place-form')).toBeHidden()

    const stored = await readStoredPlaces(page)
    expect(stored[0]).toMatchObject({ provider: 'manual', providerPlaceId: '' })
  })

  test('지도에서 위치를 다시 찍으면 검색으로 고른 연결이 끊긴다', async ({ page }) => {
    await openApp(page, { storage: '[]' })
    await stubKakaoSdk(page)

    await page.getByTestId('add-place').click()
    await page.getByTestId('field-search').fill('디어 모먼트')
    await page.getByTestId('place-search-submit').click()
    await page.getByTestId('place-search-result-1234567').click()
    await expect(page.getByTestId('place-picked-provider')).toBeVisible()

    // 지도에서 직접 찍은 좌표는 더 이상 그 가게의 위치가 아니므로 연결이 끊겨야 합니다.
    await page.getByTestId('pick-on-map').click()
    await page.getByTestId('map-canvas').click({ position: { x: 200, y: 200 } })
    await expect(page.getByTestId('place-form')).toBeVisible()

    // 위치를 찍는 동안 입력해 둔 내용은 남아 있어야 합니다.
    await expect(page.getByTestId('field-name')).toHaveValue('디어 모먼트')
    await expect(page.getByTestId('place-picked-provider')).toBeHidden()

    await page.getByTestId('place-form-submit').click()
    await expect(page.getByTestId('place-form')).toBeHidden()

    const stored = await readStoredPlaces(page)
    expect(stored[0]).toMatchObject({ provider: 'manual', providerPlaceId: '' })
  })
})
