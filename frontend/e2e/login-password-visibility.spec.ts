import { expect, test } from '@playwright/test'

test('password visibility can be toggled without changing its value', async ({ page }) => {
  await page.route('**/api/auth/me', (route) => route.fulfill({ status: 401 }))

  await page.goto('/')

  const passwordInput = page.getByLabel('Пароль')
  const showPasswordButton = page.getByRole('button', { name: 'Показать пароль' })

  await expect(showPasswordButton).toBeVisible()
  await expect(showPasswordButton).toHaveAttribute('title', 'Показать пароль')
  await expect(passwordInput).toHaveAttribute('type', 'password')

  await passwordInput.fill('password-for-test-user')
  await showPasswordButton.click()

  const hidePasswordButton = page.getByRole('button', { name: 'Скрыть пароль' })
  await expect(hidePasswordButton).toHaveAttribute('title', 'Скрыть пароль')
  await expect(passwordInput).toHaveAttribute('type', 'text')
  await expect(passwordInput).toHaveValue('password-for-test-user')

  await hidePasswordButton.focus()
  await page.keyboard.press('Enter')

  await expect(passwordInput).toHaveAttribute('type', 'password')
  await expect(passwordInput).toHaveValue('password-for-test-user')
})
