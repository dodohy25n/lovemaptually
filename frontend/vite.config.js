import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 백엔드 CORS가 http://localhost:5173 하나만 허용합니다.
    // 포트가 밀리면 모든 API 호출이 preflight에서 막히므로 다른 포트로 넘어가지 않게 고정합니다.
    strictPort: true,
  },
})
