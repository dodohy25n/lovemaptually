import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test('로그인 화면은 입력 검증과 회원가입 이동을 제공한다', async ({ page }) => {
  await openApp(page, { path: '/login' })

  await expect(page.getByRole('heading', { name: 'Love Maptually', level: 1 })).toBeVisible()
  await expect(page.getByTestId('login-submit')).toBeDisabled()

  await page.getByLabel('이메일').fill('couple@example.com')
  await page.getByLabel('비밀번호', { exact: true }).fill('password12')
  await expect(page.getByTestId('login-submit')).toBeEnabled()

  await page.getByRole('link', { name: /회원가입하기/ }).click()
  await expect(page).toHaveURL(/\/signup$/)
  await expect(page.getByRole('heading', { name: '회원가입', level: 1 })).toBeVisible()
})
