import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('6. 리뷰 화면', () => {
  test('그의 리뷰와 그녀의 리뷰가 각각 표시된다', async ({ page }) => {
    await openApp(page, { path: '/reviews/him' })
    await expect(page.getByRole('heading', { name: '그의 리뷰', level: 1 })).toBeVisible()
    const dearMoment = page.getByTestId('review-card-him').filter({ hasText: '디어 모먼트' })
    await expect(dearMoment).toContainText('창가 자리에서')

    await page.getByRole('link', { name: '그녀의 리뷰' }).click()
    await expect(page.getByRole('heading', { name: '그녀의 리뷰', level: 1 })).toBeVisible()
    await expect(
      page.getByTestId('review-card-her').filter({ hasText: '디어 모먼트' }),
    ).toContainText('햇빛 들어오는 자리')
  })

  test('두 리뷰 카드의 크기가 일치한다', async ({ page }) => {
    await openApp(page, { path: '/reviews/him' })
    const himBox = await page.getByTestId('review-card-him').first().boundingBox()

    await page.goto('/reviews/her')
    await expect(page.getByTestId('review-card-her').first()).toBeVisible()
    const herBox = await page.getByTestId('review-card-her').first().boundingBox()

    expect(Math.abs(himBox.width - herBox.width)).toBeLessThanOrEqual(1)
    expect(Math.abs(himBox.height - herBox.height)).toBeLessThanOrEqual(1)
  })

  test('분위기·맛·가성비·서비스 점수와 사진 6칸이 모두 있다', async ({ page }) => {
    await openApp(page, { path: '/reviews/him' })
    const card = page.getByTestId('review-card-him').first()

    for (const label of ['분위기', '맛', '가성비', '서비스']) {
      await expect(card.getByText(label, { exact: true })).toBeVisible()
    }
    await expect(card.locator('.review__photo')).toHaveCount(6)
  })

  test('장식(테이프)이 텍스트를 가리지 않는다', async ({ page }) => {
    await openApp(page, { path: '/reviews/him' })
    const card = page.getByTestId('review-card-him').first()

    // 장식은 클릭을 가로채지 않아야 합니다.
    const pointerEvents = await card.locator('.lm-tape').evaluate(
      (el) => getComputedStyle(el).pointerEvents,
    )
    expect(pointerEvents).toBe('none')

    // 본문 텍스트가 실제로 화면에서 잡히는지(가려지지 않았는지) 확인합니다.
    const body = card.locator('.review__body')
    await expect(body).toBeVisible()
    const box = await body.boundingBox()
    const topElement = await page.evaluate(
      ([x, y]) => document.elementFromPoint(x, y)?.className ?? '',
      [box.x + box.width / 2, box.y + 10],
    )
    expect(String(topElement)).not.toContain('lm-tape')
  })

  test('우리의 기억 화면이 정상 표시된다', async ({ page }) => {
    await openApp(page, { path: '/memories' })

    await expect(page.getByRole('heading', { name: '우리의 기억', level: 1 })).toBeVisible()
    await expect(page.getByTestId('memory-card-place_seed_dear_moment')).toBeVisible()
    await expect(page.getByTestId('memories-average')).toHaveText('3.2')

    const card = page.getByTestId('memory-card-place_seed_dear_moment')
    await expect(card).toContainText('디어 모먼트')
    await expect(card).toContainText('재방문 의사')
    await expect(card).toContainText('둘 다 또 가고 싶어요')
  })

  test('기억 카드에서 지도로 이동할 수 있다', async ({ page }) => {
    await openApp(page, { path: '/memories' })

    await page
      .getByTestId('memory-card-place_seed_dear_moment')
      .getByRole('button', { name: '지도에서 보기' })
      .click()

    await expect(page.getByTestId('map-canvas')).toBeVisible()
    await expect(page).toHaveURL(/\/$/)
  })
})
