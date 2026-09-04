import { defineConfig, devices } from '@playwright/test'

// 이 머신의 4173 포트는 다른 앱이 쓰고 있어 전용 포트를 씁니다.
// 필요하면 PLAYWRIGHT_PORT 환경 변수로 바꿀 수 있습니다.
const PORT = process.env.PLAYWRIGHT_PORT || 4399

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['list']],
  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    trace: 'off',
    video: 'off',
    screenshot: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // vite preview는 기본적으로 localhost(IPv6)에만 바인딩하므로 host를 명시합니다.
    command: `npm run build && npm run preview -- --host 127.0.0.1 --port ${PORT} --strictPort`,
    url: `http://127.0.0.1:${PORT}`,
    // e2e는 백엔드 없이 도는 로컬 모드를 검증합니다.
    // .env 가 api 모드를 가리키더라도 여기서 덮어써 빌드가 로컬 모드로 나오게 합니다.
    env: { VITE_DATA_MODE: 'local', VITE_API_BASE_URL: '' },
    // 다른 앱이 같은 포트를 점유하고 있어도 재사용하지 않고 항상 새로 띄웁니다.
    reuseExistingServer: false,
    timeout: 180 * 1000,
  },
})
