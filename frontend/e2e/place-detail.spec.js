import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('5. 장소 상세', () => {
  test('핀을 누르면 상세 패널이 열리고 정보가 정확히 표시된다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('map-pin-place_seed_dear_moment').click()

    const panel = page.getByTestId('place-detail')
    await expect(panel).toBeVisible()
    await expect(panel.getByRole('heading', { name: '디어 모먼트' })).toBeVisible()
    await expect(panel.getByTestId('detail-address')).toHaveText('서울 강남구 테헤란로 123')
    await expect(panel.getByTestId('detail-visited-at')).toHaveText('2026-02-14')
    await expect(panel.getByText('카페', { exact: true })).toBeVisible()

    // 커플 통합 점수 (텍스트)
    await expect(panel.getByText('커플 통합 점수')).toBeVisible()
    await expect(panel.getByTestId('score-text').first()).toHaveText('4.5')

    // 두 사람의 리뷰
    await expect(panel.getByTestId('detail-review-him')).toContainText('창가 자리에서')
    await expect(panel.getByTestId('detail-review-her')).toContainText('햇빛 들어오는 자리')
  })

  test('최근 방문 목록에서도 상세를 열 수 있다', async ({ page }) => {
    await openApp(page)

    await page
      .getByRole('region', { name: '최근 방문 장소' })
      .getByRole('button', { name: /로맨틱 가든/ })
      .click()
    await expect(page.getByTestId('place-detail')).toContainText('로맨틱 가든')
  })

  test('Escape 키로 상세 패널을 닫을 수 있다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('map-pin-place_seed_dear_moment').click()
    await expect(page.getByTestId('place-detail')).toBeVisible()

    await page.keyboard.press('Escape')
    await expect(page.getByTestId('place-detail')).toBeHidden()
  })

  test('닫기 버튼으로도 닫힌다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('map-pin-place_seed_moonlight_ramen').click()
    await page.getByTestId('detail-close').click()
    await expect(page.getByTestId('place-detail')).toBeHidden()
  })
})
