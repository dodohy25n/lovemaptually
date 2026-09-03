import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test('지도 핀이 해당 장소의 기억 팝업을 연다', async ({ page }) => {
  await openApp(page)
  await page.getByTestId('map-pin-place_seed_moonlight_ramen').click()
  const modal = page.getByTestId('review-carousel')
  await expect(modal).toBeVisible()
  await expect(modal).toContainText('달빛 라멘')

  await modal.getByRole('tab', { name: '그녀의 기억' }).click()
  await expect(modal).toContainText('그녀의 기억')
  await expect(modal).toContainText('면은 괜찮았는데')
})
