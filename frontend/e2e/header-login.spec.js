import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test('우측 상단 프로필 아이콘을 누르면 로그인 화면으로 이동한다', async ({ page }) => {
  await openApp(page)

  await page.getByRole('link', { name: '로그인' }).click()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Love Maptually', level: 1 })).toBeVisible()
  await expect(page.getByTestId('login-form')).toBeVisible()
})
