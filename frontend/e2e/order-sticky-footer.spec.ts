import { expect, test, type Locator, type Page } from '@playwright/test'

const orderSummary = {
  id: 70,
  type: 'ORDER',
  createdAt: '2026-09-01T10:15:00+03:00',
  mainItem: {
    id: 1,
    name: 'Верона 088 плоскость',
    quantity: 10,
    unit: 'SQUARE_METER',
    unitPrice: 1000,
    lineTotal: 10000,
  },
  itemsCount: 1,
  totalAmount: 10000,
  paymentStatus: 'UNPAID',
  prepaymentAmount: null,
  paidAmount: 0,
  remainingAmount: 10000,
  executionStatus: 'NEW',
  clientName: 'Владимир',
  clientPhone: '+7 (999) 123-45-67',
  fulfillmentMethod: null,
  deliveryAddress: null,
  factoryReadyDate: null,
  factoryReadyAttention: false,
  version: 0,
  matches: [],
}

const orderDetails = {
  id: 70,
  type: 'ORDER',
  createdAt: '2026-09-01T10:15:00+03:00',
  items: [orderSummary.mainItem],
  totalAmount: 10000,
  paymentStatus: 'UNPAID',
  prepaymentAmount: null,
  paidAmount: 0,
  remainingAmount: 10000,
  payments: [],
  executionStatus: 'NEW',
  client: {
    id: 1,
    name: 'Владимир',
    phone: '+7 (999) 123-45-67',
    comment: null,
  },
  additionalContacts: [],
  fulfillmentMethod: null,
  deliveryAddress: null,
  comment: null,
  factoryReadyDate: null,
  factoryReadyAttention: false,
  createdByDisplayName: 'Тестовый пользователь',
  updatedAt: '2026-09-01T10:15:00+03:00',
  version: 0,
}

async function mockJournal(page: Page, withOrder = false) {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ username: 'test-user', displayName: 'Тестовый пользователь' }),
  }))
  await page.route('**/api/journal**', (route) => {
    const path = new URL(route.request().url()).pathname
    const body = path.endsWith('/70')
      ? orderDetails
      : {
          items: withOrder ? [orderSummary] : [],
          page: 0,
          size: 30,
          hasNext: false,
          todayRevenue: 0,
          totalItems: withOrder ? 1 : 0,
        }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })
  })
}

async function expectFooterToStayFixed(dialog: Locator) {
  const scrollArea = dialog.locator('.order-dialog-scroll')
  const footer = dialog.locator('.dialog-footer')
  await scrollArea.evaluate((node) => { node.scrollTop = 0 })
  await expect(footer).toHaveClass(/dialog-footer-elevated/)

  const before = await footer.boundingBox()
  await scrollArea.evaluate((node) => { node.scrollTop = node.scrollHeight })
  await expect(footer).not.toHaveClass(/dialog-footer-elevated/)
  const after = await footer.boundingBox()
  const dialogBox = await dialog.boundingBox()

  expect(before).not.toBeNull()
  expect(after).not.toBeNull()
  expect(dialogBox).not.toBeNull()
  expect(Math.abs(after!.y - before!.y)).toBeLessThan(1)
  expect(after!.y + after!.height).toBeLessThanOrEqual(dialogBox!.y + dialogBox!.height + 1)
}

test('order creation keeps the total and actions visible and scrolls to field errors', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await mockJournal(page)
  await page.goto('/')

  await page.getByRole('button', { name: '+ Новый заказ' }).click()
  const dialog = page.getByRole('dialog', { name: 'Новый заказ' })
  const footer = dialog.locator('.dialog-footer')

  await dialog.getByLabel('Цена, ₽').fill('1250')
  await expect(footer.getByText('1 250 ₽', { exact: true })).toBeVisible()
  await expectFooterToStayFixed(dialog)

  await footer.getByRole('button', { name: 'Создать заказ' }).click()
  const firstError = dialog.getByText('Укажите название товара', { exact: true })
  await expect(firstError).toBeVisible()
  await expect(dialog.getByText('Укажите телефон', { exact: true })).toBeAttached()

  const [errorBox, footerBox] = await Promise.all([firstError.boundingBox(), footer.boundingBox()])
  expect(errorBox).not.toBeNull()
  expect(footerBox).not.toBeNull()
  expect(errorBox!.y + errorBox!.height).toBeLessThan(footerBox!.y)
})

test('sticky order footer keeps its mobile layout', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockJournal(page)
  await page.goto('/')

  await page.getByRole('button', { name: '+ Новый заказ' }).click()
  const dialog = page.getByRole('dialog', { name: 'Новый заказ' })
  const footer = dialog.locator('.dialog-footer')
  await expectFooterToStayFixed(dialog)

  const [totalBox, cancelBox] = await Promise.all([
    footer.getByText('Итого', { exact: true }).boundingBox(),
    footer.getByRole('button', { name: 'Отмена' }).boundingBox(),
  ])
  expect(totalBox).not.toBeNull()
  expect(cancelBox).not.toBeNull()
  expect(cancelBox!.y).toBeGreaterThan(totalBox!.y + totalBox!.height)
})

test('order editing uses the same sticky footer', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await mockJournal(page, true)
  await page.goto('/')

  await page.getByRole('button', { name: 'Открыть запись З-70' }).click()
  await page.getByRole('button', { name: 'Изменить заказ' }).click()
  const dialog = page.getByRole('dialog', { name: 'Редактирование заказа' })

  await expect(dialog.getByRole('button', { name: 'Сохранить изменения' })).toBeVisible()
  await expectFooterToStayFixed(dialog)
})
