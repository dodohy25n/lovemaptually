import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('7. 챗봇', () => {
  test('너구리 버튼을 누르면 패널이 열리고 mock 응답이 표시된다', async ({ page }) => {
    await openApp(page)

    await expect(page.getByTestId('chatbot-panel')).toBeHidden()
    await page.getByTestId('chatbot-button').click()

    const panel = page.getByTestId('chatbot-panel')
    await expect(panel).toBeVisible()
    // 처음 열면 러비가 인사한다
    await expect(panel.getByTestId('chatbot-log')).toContainText('나는 러비야')

    await page.getByTestId('chatbot-input').fill('점수는 어떻게 계산돼?')
    await page.getByTestId('chatbot-send').click()

    const log = panel.getByTestId('chatbot-log')
    await expect(log.locator('[data-role="user"]')).toHaveText('점수는 어떻게 계산돼?')
    await expect(log.locator('[data-role="bot"]').last()).toContainText('평균')
  })

  test('닫기 버튼과 Escape로 닫을 수 있다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('chatbot-button').click()
    await page.getByTestId('chatbot-close').click()
    await expect(page.getByTestId('chatbot-panel')).toBeHidden()

    await page.getByTestId('chatbot-button').click()
    await expect(page.getByTestId('chatbot-panel')).toBeVisible()
    await page.getByTestId('chatbot-input').press('Escape')
    await expect(page.getByTestId('chatbot-panel')).toBeHidden()
  })

  test('키보드만으로 열고 대화하고 닫을 수 있다', async ({ page }) => {
    await openApp(page)

    const button = page.getByTestId('chatbot-button')
    await button.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByTestId('chatbot-panel')).toBeVisible()

    // 열리면 입력창으로 포커스가 옮겨간다
    await expect(page.getByTestId('chatbot-input')).toBeFocused()

    await page.keyboard.type('어디 갈까?')
    await page.keyboard.press('Enter')
    await expect(
      page.getByTestId('chatbot-log').locator('[data-role="bot"]').last(),
    ).toContainText('주말')
  })

  test('모르는 질문에는 폴백 답을 준다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('chatbot-button').click()
    await page.getByTestId('chatbot-input').fill('오늘 환율 알려줘')
    await page.getByTestId('chatbot-send').click()

    await expect(
      page.getByTestId('chatbot-log').locator('[data-role="bot"]').last(),
    ).toContainText('배우는 중')
  })
})
