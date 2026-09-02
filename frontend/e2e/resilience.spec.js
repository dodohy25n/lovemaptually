import { test, expect } from '@playwright/test'
import { openApp, STORAGE_KEY } from './helpers.js'

test.describe('8. 오류 및 빈 상태', () => {
  test('localStorage에 깨진 JSON이 있어도 앱이 실행된다', async ({ page }) => {
    const pageErrors = []
    page.on('pageerror', (error) => pageErrors.push(error.message))

    await openApp(page, { storage: '{이건 JSON이 아님' })

    // 앱이 죽지 않고 seed 데이터로 복구된다
    await expect(page.getByTestId('map-canvas')).toBeVisible()
    await expect(page.getByTestId('place-count')).toHaveText('3')
    await expect(page.getByTestId('storage-warning')).toBeVisible()
    expect(pageErrors).toEqual([])
  })

  test('배열 안에 깨진 레코드가 있어도 나머지는 보여준다', async ({ page }) => {
    const broken = JSON.stringify([
      {
        id: 'ok_1',
        name: '살아남은 장소',
        category: '카페',
        latitude: 37.5,
        longitude: 127.0,
        visitedAt: '2026-01-01',
        coupleScore: 4.2,
        reviews: [],
        images: [],
        tags: [],
      },
      { id: 'broken', name: '', latitude: 'not-a-number' },
    ])

    await openApp(page, { storage: broken })

    await expect(page.getByTestId('place-count')).toHaveText('1')
    await expect(page.getByTestId('map-pin-ok_1')).toBeVisible()
  })

  test('지도 타일 요청이 실패해도 fallback UI와 핀이 동작한다', async ({ page }) => {
    await openApp(page, { failTiles: true })

    await expect(page.getByTestId('map-fallback')).toBeVisible()
    // 타일이 없어도 핀과 상세는 그대로 쓸 수 있다
    await expect(page.getByTestId('map-pin-place_seed_dear_moment')).toBeVisible()
    await page.getByTestId('map-pin-place_seed_dear_moment').click()
    await expect(page.getByTestId('place-detail')).toContainText('디어 모먼트')
  })

  test('방문 기록이 없으면 Empty State가 표시된다', async ({ page }) => {
    await openApp(page, { storage: '[]' })

    await expect(page.getByTestId('place-count')).toHaveText('0')
    await expect(page.getByTestId('empty-state').first()).toBeVisible()
    await expect(page.getByText('아직 우리의 러브맵이 비어 있어요')).toBeVisible()

    // Empty State의 버튼으로 바로 등록 폼을 열 수 있다
    await page.getByRole('button', { name: '첫 장소 기록하기' }).click()
    await expect(page.getByTestId('place-form')).toBeVisible()
  })

  test('카테고리 필터 결과가 없으면 안내가 나온다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('category-데이트 코스').click()
    await expect(page.getByTestId('no-filter-result')).toBeVisible()
  })

  test('빈 상태에서 리뷰·기억 화면도 정상 동작한다', async ({ page }) => {
    await openApp(page, { storage: '[]' })

    await page.goto('/reviews/him')
    await expect(page.getByTestId('empty-state')).toBeVisible()

    await page.goto('/memories')
    await expect(page.getByTestId('empty-state')).toBeVisible()
  })
})

test.describe('접근성', () => {
  test('아이콘 버튼에 aria-label이 있다', async ({ page }) => {
    await openApp(page)

    await expect(page.getByRole('button', { name: '장소 검색' })).toBeVisible()
    await expect(page.getByRole('button', { name: '알림 보기' })).toBeVisible()
    await expect(page.getByRole('button', { name: '내 프로필' })).toBeVisible()
    await expect(page.getByRole('button', { name: '러비 챗봇 열기' })).toBeVisible()
  })

  test('지도 핀은 장소명과 등급, 점수를 함께 읽어준다', async ({ page }) => {
    await openApp(page)

    await expect(
      page.getByRole('button', { name: '디어 모먼트, 좋아요, 점수 4.5점' }),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: '달빛 라멘, 아쉬워요, 점수 1.8점' }),
    ).toBeVisible()
  })

  test('Escape로 등록 모달을 닫을 수 있다', async ({ page }) => {
    await openApp(page)

    await page.getByTestId('add-place').click()
    await expect(page.getByTestId('place-form')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('place-form')).toBeHidden()
  })

  test('모든 이미지에 alt가 있다', async ({ page }) => {
    await openApp(page)

    const missing = await page.evaluate(() =>
      [...document.querySelectorAll('img')]
        .filter((img) => !img.hasAttribute('alt'))
        .map((img) => img.getAttribute('src')),
    )
    expect(missing).toEqual([])
  })
})
