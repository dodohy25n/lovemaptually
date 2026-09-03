import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('1. 앱 최초 접속', () => {
  test('메인 화면과 지도, 커플 요약 카드, 샘플 핀이 표시된다', async ({ page }) => {
    await openApp(page)

    // 메인은 헤더 없이 지도가 화면 전체를 채웁니다.
    await expect(page.getByRole('link', { name: 'Love Maptually 홈으로' })).toHaveCount(0)
    const mapBox = await page.getByTestId('map-canvas').boundingBox()
    expect(mapBox.width).toBeGreaterThan(page.viewportSize().width * 0.95)
    expect(mapBox.height).toBeGreaterThan(page.viewportSize().height * 0.95)

    // 커플 요약 카드
    const summary = page.getByRole('region', { name: '우리의 러브맵' })
    await expect(summary).toBeVisible()
    await expect(page.getByTestId('place-count')).toHaveText('3')

    // 스케치북형 플로팅 바로가기
    await expect(page.getByRole('navigation', { name: '러브맵 바로가기' })).toBeVisible()
    await expect(page.getByRole('link', { name: '추억 저장소' })).toBeVisible()
    await expect(page.getByTestId('open-taste')).toBeVisible()
    await expect(page.getByTestId('add-place')).toBeVisible()
    await expect(page.getByRole('link', { name: '로그아웃' })).toBeVisible()
    await expect(page.getByTestId('open-recommendation')).toBeVisible()
    await expect(page.getByText('좋아요! (4.0 ~ 5.0)')).toHaveCount(0)

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
    await expect(page.getByTestId('review-carousel')).toBeVisible()

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

  test('지도 검색으로 기록된 장소를 찾아 기억 팝업을 연다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('map-search-input').fill('디어')
    await expect(page.getByText('디어 모먼트', { exact: true }).last()).toBeVisible()
    await page.getByTestId('map-search-submit').click()

    await expect(page.getByTestId('review-carousel')).toContainText('디어 모먼트')
  })
})
