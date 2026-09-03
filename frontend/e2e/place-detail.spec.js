import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('5. 장소 상세', () => {
  test('핀을 누르면 해당 장소의 기억 팝업이 열린다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('map-pin-place_seed_dear_moment').click()

    const modal = page.getByTestId('review-carousel')
    await expect(modal).toBeVisible()
    await expect(modal).toContainText('디어 모먼트')
    await expect(modal).toContainText('창가 자리에서')

    await modal.getByRole('tab', { name: '그녀의 기억' }).click()
    await expect(modal).toContainText('햇빛 들어오는 자리')
    await expect(modal).not.toContainText('달빛 라멘')
  })

  test('최근 방문 목록에서도 해당 장소의 기억 팝업을 열 수 있다', async ({ page }) => {
    await openApp(page)

    await page
      .getByRole('region', { name: '최근 방문 장소' })
      .getByRole('button', { name: /로맨틱 가든/ })
      .click()
    await expect(page.getByTestId('review-carousel')).toContainText('로맨틱 가든')
  })

  test('Escape 키로 기억 팝업을 닫을 수 있다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('map-pin-place_seed_dear_moment').click()
    await expect(page.getByTestId('review-carousel')).toBeVisible()

    await page.keyboard.press('Escape')
    await expect(page.getByTestId('review-carousel')).toBeHidden()
  })

  test('닫기 버튼으로도 닫힌다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('map-pin-place_seed_moonlight_ramen').click()
    await page.getByRole('button', { name: '기억 팝업 닫기' }).click()
    await expect(page.getByTestId('review-carousel')).toBeHidden()
  })
})
