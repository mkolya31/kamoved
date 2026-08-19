import { defineConfig } from '@playwright/test';

const baseURL = 'http://127.0.0.1:5173';

export default defineConfig({
  testDir: './e2e',

  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,

  // Короткий вывод особенно полезен при работе через Codex:
  // меньше тестовых логов возвращается в контекст агента.
  reporter: 'line',

  use: {
    baseURL,
    browserName: 'chromium',

    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'off',
  },

  webServer: {
    command: 'pnpm dev',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
