import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('1. 앱 최초 접속', () => {
  test('메인 화면과 지도, 커플 요약 카드, 샘플 핀이 표시된다', async ({ page }) => {
    await openApp(page)

    // 헤더
    await expect(page.getByRole('link', { name: 'Love Maptually 홈으로' })).toBeVisible()

    // 커플 요약 카드
    const summary = page.getByRole('region', { name: '우리의 러브맵' })
    await expect(summary).toBeVisible()
    await expect(page.getByTestId('place-count')).toHaveText('3')

    // 하트 등급 안내
    await expect(page.getByRole('region', { name: '점수에 따른 하트 등급' })).toBeVisible()
    await expect(page.getByText('좋아요! (4.0 ~ 5.0)')).toBeVisible()
    await expect(page.getByText('보통이에요 (2.0 ~ 3.9)')).toBeVisible()
    await expect(page.getByText('아쉬워요 (0 ~ 1.9)')).toBeVisible()

    // 최근 방문 장소
    await expect(page.getByRole('region', { name: '최근 방문 장소' })).toBeVisible()

    // 지도와 샘플 핀
    await expect(page.getByTestId('map-canvas')).toBeVisible()
    await expect(page.getByTestId('map-pin-place_seed_dear_moment')).toBeVisible()
    await expect(page.getByTestId('map-pin-place_seed_romantic_garden')).toBeVisible()
    await expect(page.getByTestId('map-pin-place_seed_moonlight_ramen')).toBeVisible()

    // 챗봇 버튼
    await expect(page.getByTestId('chatbot-button')).toBeVisible()
  })

  test('콘솔 오류와 Vue 경고가 없다', async ({ page }) => {
    const problems = []
    page.on('console', (message) => {
      if (message.type() === 'error' || message.type() === 'warning') {
        problems.push(`[${message.type()}] ${message.text()}`)
      }
    })
    page.on('pageerror', (error) => problems.push(`[pageerror] ${error.message}`))

    await openApp(page)
    await page.getByTestId('map-pin-place_seed_dear_moment').click()
    await expect(page.getByTestId('place-detail')).toBeVisible()

    expect(problems).toEqual([])
  })

  test('카테고리 필터로 핀을 걸러낸다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('category-카페').click()
    await expect(page.getByTestId('map-pin-place_seed_dear_moment')).toBeVisible()
    await expect(page.getByTestId('map-pin-place_seed_romantic_garden')).toHaveCount(0)

    await page.getByTestId('category-all').click()
    await expect(page.getByTestId('map-pin-place_seed_romantic_garden')).toBeVisible()
  })
})
