import { expect, test, type Page } from '@playwright/test'

test.setTimeout(15_000)

const searchEntry = {
  id: 42,
  type: 'ORDER',
  createdAt: '2026-08-22T10:15:00+03:00',
  mainItem: {
    id: 1,
    name: 'Готика Голд',
    quantity: 10,
    unit: 'SQUARE_METER',
    unitPrice: 1000,
    lineTotal: 10000,
  },
  itemsCount: 1,
  totalAmount: 10000,
  paymentStatus: 'UNPAID',
  prepaymentAmount: null,
  remainingAmount: 10000,
  executionStatus: 'NEW',
  clientName: 'Владимир',
  clientPhone: '+7 (999) 123-45-67',
  fulfillmentMethod: 'DELIVERY',
  deliveryAddress: 'СНТ Главножуково',
  version: 0,
  matches: [{field: 'ITEM', value: 'Готика Голд', additionalCount: 0}],
}

async function mockApplication(page: Page) {
  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({username: 'test-user', displayName: 'Тестовый пользователь'}),
  }))

  await page.route('**/api/journal**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.endsWith('/search')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [], page: 0, size: 30, hasNext: false, todayRevenue: 0, totalItems: 0,
        }),
      })
      return
    }

    const query = url.searchParams.get('query') ?? ''
    const mode = url.searchParams.get('mode')
    if (query === 'владимир') {
      await new Promise((resolve) => setTimeout(resolve, 450))
    }
    const items = query === 'готика' || (query === 'никто' && mode === 'all')
      ? [searchEntry]
      : []
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items,
        page: 0,
        size: 30,
        hasNext: false,
        todayRevenue: 0,
        totalItems: items.length,
      }),
    })
  })
}

test('search keeps only the latest response and preserves query across modes', async ({ page }) => {
  await mockApplication(page)
  await page.goto('/')

  const search = page.getByRole('searchbox', {name: 'Поиск по журналу'})
  await search.fill('владимир')
  await page.waitForRequest(
    (request) => request.url().includes(
      'query=%D0%B2%D0%BB%D0%B0%D0%B4%D0%B8%D0%BC%D0%B8%D1%80',
    ),
    {timeout: 2_000},
  )
  await search.fill('готика')

  await expect(page.getByText('Найдено записей:')).toContainText('1')
  await expect(page.getByText('Готика Голд', {exact: true}).first()).toBeVisible()
  await expect(page.getByText('Совпадение:')).toBeVisible()
  await page.waitForTimeout(500)
  await expect(page.getByText('Готика Голд', {exact: true}).first()).toBeVisible()

  await page.getByRole('button', {name: 'Активные заказы'}).click()
  await search.fill('никто')
  await expect(page.getByRole('heading', {
    name: 'По запросу «никто» ничего не найдено в активных заказах',
  })).toBeVisible()

  await page.getByRole('button', {name: 'Искать во всех записях'}).click()
  await expect(search).toHaveValue('никто')
  await expect(page.getByText('Готика Голд', {exact: true}).first()).toBeVisible()
})
