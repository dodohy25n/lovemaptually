// 발표용 화면 캡처. 프론트가 api 모드로 떠 있어야 합니다.
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const BASE = process.env.FRONT_URL || 'http://localhost:5173'
const OUT = '/Users/dohyeon/Desktop/skala-til/assignments/week-08-미니프로젝트-러브맵츄얼리/발표/screens'
fs.mkdirSync(OUT, { recursive: true })

const shots = []
async function shot(page, name, note) {
  await page.waitForTimeout(1200)
  const file = `${OUT}/${name}.png`
  await page.screenshot({ path: file, fullPage: false })
  shots.push({ name, note, file })
  console.log(`  찍음 ${name}  ${note}`)
}

const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await shot(page, '01-login', '로그인 화면')

  await page.fill('input[type="email"], input[name="email"]', 'dohyeon@lovemap.dev').catch(() => {})
  await page.fill('input[type="password"], input[name="password"]', 'demo1234!').catch(() => {})
  await page.keyboard.press('Enter')
  await page.waitForTimeout(2500)
  await shot(page, '02-map', '우리 지도. 핀이 라벨 색으로 갈립니다')

  for (const [path, name, note] of [
    ['/reviews/me', '03-reviews-me', '내 리뷰 목록'],
    ['/memories', '04-memories', '추억 저장소와 월간 리포트'],
  ]) {
    await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' }).catch(() => {})
    await shot(page, name, note)
  }
} catch (error) {
  console.log('  캡처 중 오류:', error.message)
} finally {
  fs.writeFileSync(`${OUT}/캡처목록.json`, JSON.stringify(shots, null, 2))
  console.log(`\n총 ${shots.length}장을 ${OUT} 에 저장했습니다.`)
  await browser.close()
}
