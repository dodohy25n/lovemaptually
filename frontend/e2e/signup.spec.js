import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test('회원가입 화면은 입력 검증 후 로그인 화면으로 이동한다', async ({ page }) => {
  await openApp(page, { path: '/signup' })

  await expect(page.getByRole('heading', { name: '회원가입', level: 1 })).toBeVisible()
  await expect(page.getByTestId('signup-submit')).toBeDisabled()

  await page.getByLabel('이메일').fill('couple@example.com')
  await page.getByLabel('비밀번호', { exact: true }).fill('password12')
  await page.getByLabel('비밀번호 확인').fill('password12')
  await page.getByLabel('닉네임').fill('러비커플')
  await page.getByLabel('생년월일').fill('1999-01-01')
  await page.getByRole('button', { name: '맛집 탐방' }).click()
  await page.getByLabel('이용약관 및 개인정보 수집·이용에 동의합니다.').check()

  await expect(page.getByTestId('signup-submit')).toBeEnabled()
  await page.getByTestId('signup-submit').click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByTestId('login-form')).toBeVisible()
})
