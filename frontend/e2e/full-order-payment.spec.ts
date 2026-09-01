import { expect, test, type Page } from '@playwright/test'

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

test('the full order amount can be copied into the initial payment', async ({ page }) => {
  await mockEmptyJournal(page)
  await page.goto('/')

  await page.getByRole('button', { name: '+ Новый заказ' }).click()
  const dialog = page.getByRole('dialog', { name: 'Новый заказ' })
  await dialog.getByRole('button', { name: '+ Добавить платёж' }).click()

  const fullAmountButton = dialog.getByRole('button', { name: /^Внести всю сумму —/ })
  await expect(fullAmountButton).toBeDisabled()

  await dialog.getByLabel('Цена, ₽').fill('125000')
  await expect(fullAmountButton).toBeEnabled()
  await expect(fullAmountButton).toHaveAccessibleName('Внести всю сумму — 125 000 ₽')

  const paymentAmount = dialog.getByLabel('Сумма платежа, ₽')
  const paymentMethod = dialog.getByLabel('Способ оплаты')
  const paymentComment = dialog.getByLabel('Комментарий к платежу необязательно')
  const [amountBox, methodBox, commentBox, buttonBox] = await Promise.all([
    paymentAmount.boundingBox(),
    paymentMethod.boundingBox(),
    paymentComment.boundingBox(),
    fullAmountButton.boundingBox(),
  ])
  expect(amountBox).not.toBeNull()
  expect(methodBox).not.toBeNull()
  expect(commentBox).not.toBeNull()
  expect(buttonBox).not.toBeNull()
  expect(amountBox!.y).toBe(methodBox!.y)
  expect(amountBox!.y).toBe(commentBox!.y)
  expect(buttonBox!.y).toBeGreaterThan(amountBox!.y + amountBox!.height)

  await paymentAmount.fill('10000')
  await fullAmountButton.click()
  await expect(paymentAmount).toHaveValue('125000')
  await expect(fullAmountButton).toBeHidden()

  await dialog.getByLabel('Цена, ₽').fill('130000')
  await expect(paymentAmount).toHaveValue('125000')
  await expect(fullAmountButton).toHaveAccessibleName('Внести всю сумму — 130 000 ₽')

  await paymentAmount.fill('130000,00')
  await expect(fullAmountButton).toBeHidden()
})
