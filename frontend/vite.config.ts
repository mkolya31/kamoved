import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    // Юнит-тесты живут в src, e2e-спеки Playwright (*.spec.ts) — в e2e.
    include: ['src/**/*.test.{ts,tsx}'],
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

