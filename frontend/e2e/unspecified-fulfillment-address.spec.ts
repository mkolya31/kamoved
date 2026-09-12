import { expect, test } from '@playwright/test'

test('an imported address remains visible and survives editing until pickup is selected', async ({ page }) => {
  const address = 'СНТ Вымышленное, участок 12'
  const item = {
    id: 1,
    name: 'Вымышленная плитка',
    quantity: 10,
    unit: 'SQUARE_METER',
    unitPrice: 1000,
    lineTotal: 10000,
  }
  const summary = {
    id: 50,
    type: 'ORDER',
    createdAt: '2026-08-24T10:15:00+03:00',
    entryDate: '2025-05-10',
    mainItem: item,
    itemsCount: 1,
    totalAmount: 10000,
    paymentStatus: 'UNPAID',
    prepaymentAmount: null,
    paidAmount: 0,
    remainingAmount: 10000,
    executionStatus: 'NEW',
    clientName: 'Вымышленный клиент',
    clientPhone: '+7 (000) 000-00-00',
    fulfillmentMethod: null,
    deliveryAddress: address,
    version: 0,
    matches: [],
  }
  let details = {
    ...summary,
    items: [item],
    payments: [],
    client: { id: 1, name: 'Вымышленный клиент', phone: '+7 (000) 000-00-00', comment: null },
    additionalContacts: [],
    comment: null as string | null,
    factoryReadyDate: null,
    factoryReadyAttention: false,
    createdByDisplayName: 'Тестовый пользователь',
    updatedAt: '2026-08-24T10:15:00+03:00',
  }
  const updates: Record<string, unknown>[] = []

  await page.route('**/api/auth/me', route => route.fulfill({ json: {
    username: 'test-user', displayName: 'Тестовый пользователь',
  } }))
  await page.route('**/api/auth/csrf', route => route.fulfill({ json: {
    headerName: 'X-CSRF-TOKEN', token: 'test',
  } }))
  await page.route('**/api/journal**', route => {
    const pathname = new URL(route.request().url()).pathname
    if (pathname === '/api/journal/50') return route.fulfill({ json: details })
    return route.fulfill({ json: {
      items: [summary], page: 0, size: 30, hasNext: false, todayRevenue: 0, totalItems: 1,
    } })
  })
  await page.route('**/api/orders/50', async route => {
    const body = route.request().postDataJSON() as Record<string, unknown>
    updates.push(body)
    details = {
      ...details,
      comment: body.comment as string | undefined ?? null,
      fulfillmentMethod: body.fulfillmentMethod as typeof details.fulfillmentMethod ?? null,
      deliveryAddress: body.deliveryAddress as string | undefined ?? null,
      version: details.version + 1,
    }
    await route.fulfill({ json: details })
  })

  await page.goto('/')
  await expect(page.getByText(`Способ получения не указан · ${address}`, { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Открыть запись З-50' }).click()
  await expect(page.getByText('Способ получения не указан', { exact: true })).toBeVisible()
  await expect(page.getByText(address, { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Изменить заказ' }).click()
  let dialog = page.getByRole('dialog', { name: 'Редактирование заказа' })
  await expect(dialog.getByLabel('Способ получения')).toHaveValue('')
  await expect(dialog.getByLabel('Адрес', { exact: true })).toHaveValue(address)
  await dialog.getByLabel('Комментарий к заказу', { exact: true }).fill('Проверено без изменения доставки')
  await dialog.getByRole('button', { name: 'Сохранить изменения' }).click()
  await expect(dialog).toBeHidden()
  expect(updates[0]).not.toHaveProperty('fulfillmentMethod')
  expect(updates[0].deliveryAddress).toBe(address)

  await page.getByRole('button', { name: 'Изменить заказ' }).click()
  dialog = page.getByRole('dialog', { name: 'Редактирование заказа' })
  await dialog.getByLabel('Способ получения').selectOption('PICKUP_WAREHOUSE')
  await expect(dialog.getByLabel('Адрес', { exact: true })).toBeHidden()
  await dialog.getByLabel('Способ получения').selectOption('DELIVERY_FACTORY')
  await expect(dialog.getByLabel('Адрес доставки')).toHaveValue('')
})

test('a new order still has no address until delivery is selected', async ({ page }) => {
  await page.route('**/api/auth/me', route => route.fulfill({ json: {
    username: 'test-user', displayName: 'Тестовый пользователь',
  } }))
  await page.route('**/api/journal**', route => route.fulfill({ json: {
    items: [], page: 0, size: 30, hasNext: false, todayRevenue: 0, totalItems: 0,
  } }))
  await page.goto('/')
  await page.getByRole('button', { name: '+ Новый заказ' }).click()
  const dialog = page.getByRole('dialog', { name: 'Новый заказ' })
  await expect(dialog.getByLabel('Способ получения')).toHaveValue('')
  await expect(dialog.getByLabel('Адрес', { exact: true })).toHaveCount(0)
  await dialog.getByLabel('Способ получения').selectOption('DELIVERY_MARKET')
  await expect(dialog.getByLabel('Адрес доставки')).toBeVisible()
})
