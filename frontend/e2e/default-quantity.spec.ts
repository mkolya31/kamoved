import { expect, test, type Locator, type Page } from '@playwright/test'

async function mockEmptyJournal(page: Page) {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ username: 'test-user', displayName: 'Тестовый пользователь' }),
  }))
  await page.route('**/api/journal**', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      items: [],
      page: 0,
      size: 30,
      hasNext: false,
      todayRevenue: 0,
      totalItems: 0,
    }),
  }))
}

async function expectTypingToReplaceDefault(input: Locator, value: string) {
  await input.focus()
  await expect(input).toHaveJSProperty('selectionStart', 0)
  await expect(input).toHaveJSProperty('selectionEnd', 1)
  await input.pressSequentially(value)
  await expect(input).toHaveValue(value)
}

test('default quantity is replaced in new sale and order items', async ({ page }) => {
  await mockEmptyJournal(page)
  await page.goto('/')

  await page.getByRole('button', { name: '+ Продажа из наличия' }).click()
  const saleDialog = page.getByRole('dialog', { name: 'Продажа из наличия' })
  await expectTypingToReplaceDefault(saleDialog.getByLabel('Количество').first(), '23')
  await saleDialog.getByRole('button', { name: '+ Добавить позицию' }).click()
  await expectTypingToReplaceDefault(saleDialog.getByLabel('Количество').nth(1), '45')
  await saleDialog.getByRole('button', { name: 'Закрыть' }).click()

  await page.getByRole('button', { name: '+ Новый заказ' }).click()
  const orderDialog = page.getByRole('dialog', { name: 'Новый заказ' })
  await expectTypingToReplaceDefault(orderDialog.getByLabel('Количество').first(), '23')
  await orderDialog.getByRole('button', { name: '+ Добавить позицию' }).click()
  await expectTypingToReplaceDefault(orderDialog.getByLabel('Количество').nth(1), '45')
})
