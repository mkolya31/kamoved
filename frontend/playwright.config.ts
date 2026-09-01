import { defineConfig } from '@playwright/test';
import { fileURLToPath } from 'node:url';

const baseURL = 'http://127.0.0.1:5173';
const viteCliPath = fileURLToPath(new URL('./node_modules/vite/bin/vite.js', import.meta.url));
const viteCommand = `"${process.execPath}" "${viteCliPath}" --host 127.0.0.1`;

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
    command: viteCommand,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
