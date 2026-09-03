import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('PRD v4.0 핵심 화면', () => {
  test('우리 취향에서 추천으로 이어지고 후보 안의 결과만 표시한다', async ({ page }) => {
    await openApp(page)
    await page.getByTestId('open-taste').click()
    const taste = page.getByTestId('taste-modal')
    await expect(taste).toContainText('86%')
    await expect(taste).toContainText('취향 갈림')
    await taste.getByRole('button', { name: '우리 취향에 맞는 장소 보기' }).click()

    const recommendation = page.getByTestId('recommendation-modal')
    await expect(recommendation).toBeVisible()
    await recommendation.getByRole('button', { name: '추천받기' }).click()
    await expect(page.getByTestId('recommendation-412')).toContainText('라 비앙 로즈')
    await expect(recommendation.locator('.card')).toHaveCount(3)
  })

  test('지역이 없는 질문에는 지역을 되묻는다', async ({ page }) => {
    await openApp(page)
    await page.getByTestId('open-recommendation').click()
    const modal = page.getByTestId('recommendation-modal')
    await modal.getByLabel('추천 질문').fill('오늘 세 곳 추천해줘')
    await modal.getByRole('button', { name: '추천받기' }).click()
    await expect(modal).toContainText('어느 동네인지 알려주세요')
  })
})
