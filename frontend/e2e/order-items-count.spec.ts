import { expect, test } from '@playwright/test'

test('collapsed order shows how many additional items it contains', async ({ page }) => {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({username: 'test-user', displayName: 'Тестовый пользователь'}),
  }))
  await page.route('**/api/journal**', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      items: [{
        id: 50,
        type: 'ORDER',
        createdAt: '2026-08-24T10:15:00+03:00',
        mainItem: {
          id: 1,
          name: 'Верона 088 плоскость',
          quantity: 10,
          unit: 'SQUARE_METER',
          unitPrice: 1000,
          lineTotal: 10000,
        },
        itemsCount: 3,
        totalAmount: 15000,
        paymentStatus: 'UNPAID',
        prepaymentAmount: null,
        paidAmount: 0,
        remainingAmount: 15000,
        executionStatus: 'NEW',
        clientName: 'Владимир',
        clientPhone: null,
        fulfillmentMethod: null,
        deliveryAddress: null,
        version: 0,
        matches: [],
      }],
      page: 0,
      size: 30,
      hasNext: false,
      todayRevenue: 0,
      totalItems: 1,
    }),
  }))

  await page.goto('/')

  await expect(page.getByText(
    'Верона 088 плоскость + ещё 2 товара',
    {exact: true},
  )).toBeVisible()
})
