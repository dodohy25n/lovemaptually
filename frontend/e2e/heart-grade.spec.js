import { test, expect } from '@playwright/test'
import { openApp, createPlace, readStoredPlaces } from './helpers.js'

test.describe('4. 점수별 하트 변경', () => {
  const CASES = [
    { score: 4.8, grade: 'good', asset: 'heart-good', label: '좋아요' },
    { score: 3.2, grade: 'normal', asset: 'heart-normal', label: '보통이에요' },
    { score: 1.5, grade: 'bad', asset: 'heart-bad', label: '아쉬워요' },
  ]

  for (const testCase of CASES) {
    test(`${testCase.score}점으로 등록하면 ${testCase.label} 하트가 표시된다`, async ({ page }) => {
      await openApp(page)

      await createPlace(page, {
        name: `${testCase.label} 테스트 장소`,
        category: '카페',
        latitude: 37.5 + testCase.score / 100,
        longitude: 127.0,
        score: testCase.score,
      })

      const stored = await readStoredPlaces(page)
      const created = stored.find((place) => place.name === `${testCase.label} 테스트 장소`)
      expect(created.coupleScore).toBeCloseTo(testCase.score, 5)
      expect(created.heartGrade).toBe(testCase.grade)

      const pin = page.getByTestId(`map-pin-${created.id}`)
      await expect(pin).toHaveAttribute('data-grade', testCase.grade)
      await expect(pin.locator('img')).toHaveAttribute('src', `/assets/${testCase.asset}.svg`)

      // 숫자 점수는 이미지가 아니라 텍스트 노드로 렌더링된다
      const scoreChip = pin.getByTestId('pin-score')
      await expect(scoreChip).toHaveText(testCase.score.toFixed(1))
      expect(await scoreChip.evaluate((el) => el.tagName.toLowerCase())).toBe('span')
      expect(await scoreChip.locator('img').count()).toBe(0)
    })
  }

  test('점수를 수정하면 하트 등급과 핀이 즉시 바뀐다', async ({ page }) => {
    await openApp(page)

    await createPlace(page, {
      name: '점수 수정 테스트',
      category: '카페',
      latitude: 37.53,
      longitude: 127.02,
      score: 4.8,
    })

    const stored = await readStoredPlaces(page)
    const created = stored.find((place) => place.name === '점수 수정 테스트')
    const pin = page.getByTestId(`map-pin-${created.id}`)
    await expect(pin).toHaveAttribute('data-grade', 'good')

    // 상세 패널 → 장소 정보 수정 → 점수 1.5로 변경
    await page.getByTestId('detail-edit-place').click()
    await page.getByTestId('field-score').fill('1.5')
    await page.getByTestId('place-form-submit').click()
    await expect(page.getByTestId('place-form')).toBeHidden()

    await expect(pin).toHaveAttribute('data-grade', 'bad')
    await expect(pin.getByTestId('pin-score')).toHaveText('1.5')
    await expect(pin.locator('img')).toHaveAttribute('src', '/assets/heart-bad.svg')
  })

  test('리뷰의 세부 점수가 커플 통합 점수를 다시 계산한다', async ({ page }) => {
    await openApp(page)

    // seed의 카페(4.5, good)에 아쉬운 리뷰를 덮어써서 등급이 내려가는지 확인
    await page.getByTestId('map-pin-place_seed_dear_moment').click()
    await page.getByTestId('review-edit-him').click()

    for (const field of ['atmosphere', 'taste', 'value', 'service']) {
      await page.locator(`#review-${field}-him`).fill('0.5')
    }
    await page.getByTestId('review-save-him').click()
    await expect(page.getByTestId('review-form-him')).toBeHidden()

    const stored = await readStoredPlaces(page)
    const cafe = stored.find((place) => place.id === 'place_seed_dear_moment')
    // 그의 리뷰 0.5 + 그녀의 리뷰 4.5 → 평균 2.5 (보통이에요)
    expect(cafe.coupleScore).toBeCloseTo(2.5, 1)
    expect(cafe.heartGrade).toBe('normal')

    await expect(page.getByTestId('map-pin-place_seed_dear_moment')).toHaveAttribute(
      'data-grade',
      'normal',
    )
  })
})
