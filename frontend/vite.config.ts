import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const localFaviconPlugin = {
  name: 'local-favicon',
  apply: 'serve' as const,
  transformIndexHtml(html: string) {
    const withoutProductionFavicons = html.replace(/\s*<link rel="icon"[^>]*\/>/g, '')

    return withoutProductionFavicons.replace(
      '</head>',
      '    <link rel="icon" type="image/svg+xml" href="/brand/favicon-local.svg" />\n  </head>',
    )
  },
}

export default defineConfig({
  plugins: [react(), localFaviconPlugin],
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

