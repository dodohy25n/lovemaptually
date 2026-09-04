import { test, expect } from '@playwright/test'
import { openApp } from './helpers.js'

test.describe('6. 기억 화면', () => {
  test('두 사람의 기억이 각각 표시된다', async ({ page }) => {
    await openApp(page, { path: '/reviews/me' })
    await expect(page.getByRole('heading', { name: '도현의 기억', level: 1 })).toBeVisible()
    const dearMoment = page.getByTestId('review-card-him').filter({ hasText: '디어 모먼트' })
    await expect(dearMoment).toContainText('창가 자리에서')

    await page.getByRole('link', { name: '지우의 기억' }).click()
    await expect(page.getByRole('heading', { name: '지우의 기억', level: 1 })).toBeVisible()
    await expect(
      page.getByTestId('review-card-her').filter({ hasText: '디어 모먼트' }),
    ).toContainText('햇빛 들어오는 자리')
  })

  test('두 리뷰 카드의 크기가 일치한다', async ({ page }) => {
    await openApp(page, { path: '/reviews/me' })
    const himBox = await page.getByTestId('review-card-him').first().boundingBox()

    await page.goto('/reviews/partner')
    await expect(page.getByTestId('review-card-her').first()).toBeVisible()
    const herBox = await page.getByTestId('review-card-her').first().boundingBox()

    expect(Math.abs(himBox.width - herBox.width)).toBeLessThanOrEqual(1)
    expect(Math.abs(himBox.height - herBox.height)).toBeLessThanOrEqual(1)
  })

  test('분위기·맛·가성비·서비스 점수와 사진 6칸이 모두 있다', async ({ page }) => {
    await openApp(page, { path: '/reviews/me' })
    const card = page.getByTestId('review-card-him').first()

    for (const label of ['분위기', '맛', '가성비', '서비스']) {
      await expect(card.getByText(label, { exact: true })).toBeVisible()
    }
    await expect(card.locator('.review__photo')).toHaveCount(6)
  })

  test('장식(테이프)이 텍스트를 가리지 않는다', async ({ page }) => {
    await openApp(page, { path: '/reviews/me' })
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

    await expect(page.getByRole('heading', { name: '추억 저장소', level: 1 })).toBeVisible()
    await expect(page.getByTestId('memory-bookshelf')).toBeVisible()
    await expect(page.getByTestId('memory-bookshelf').locator('.diary')).toHaveCount(2)
    await expect(page.getByTestId('memory-diary-2026-02')).toContainText('2026.02')
    await page.getByTestId('memory-diary-2026-02').click()
    await expect(page.getByTestId('memory-detail-modal')).toBeVisible()
    await expect(page.getByLabel('추억 게시물 위치').locator('button')).toHaveCount(5)
    await expect(page.getByTestId('couple-report-card')).toContainText('우리의 맛집 리포트')
    await page.getByLabel('3번째 리포트 보기').click()
    await expect(page.getByTestId('couple-report-card')).toContainText('사진과 점수로 다시 보는 맛집')
    await expect(page.getByTestId('couple-report-card')).toContainText('디어 모먼트')
  })

  test('리포트에서 방문 기록과 AI 추천 판단을 볼 수 있다', async ({ page }) => {
    await openApp(page, { path: '/memories' })

    await page.getByTestId('memory-diary-2026-02').click()
    await page.getByLabel('5번째 리포트 보기').click()
    const report = page.getByTestId('couple-report-card')
    await expect(report).toContainText('방문 기록과 다시 갈 이유')
    await expect(report).toContainText('1회 방문')
    await expect(report).toContainText('추천')
  })
})
