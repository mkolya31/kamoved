import { expect, test, type Locator } from '@playwright/test'

async function enterDate(input: Locator, value: string) {
  await input.click()
  await input.press('ControlOrMeta+A')
  await input.pressSequentially(value, { delay: 25 })
}

for (const type of ['SALE', 'ORDER'] as const) {
  test(`${type} validates, resets and saves its event date`, async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-09-06T21:30:00Z'))
    if (type === 'ORDER') await page.setViewportSize({ width: 390, height: 844 })
    const isOrder = type === 'ORDER'
    const title = isOrder ? 'Новый заказ' : 'Продажа из наличия'
    const toggleText = isOrder ? 'Заказ оформлен не сегодня' : 'Продажа совершена не сегодня'
    const dateLabel = isOrder ? 'Дата заказа' : 'Дата продажи'
    const buttonText = isOrder ? 'Создать заказ' : 'Сохранить продажу'
    const requests: Record<string, unknown>[] = []
    const entries: Record<string, unknown>[] = []
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: { username: 'test-user', displayName: 'Тестовый пользователь' } }))
    await page.route('**/api/auth/csrf', (route) => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test' } }))
    await page.route('**/api/journal?*', (route) => route.fulfill({ json: {
      items: [...entries].reverse(), page: 0, size: 30, hasNext: false,
      todayRevenue: requests.filter((request) => !request.backdated).length * (isOrder ? 1000 : 5000), totalItems: entries.length,
    } }))
    await page.route(isOrder ? '**/api/orders' : '**/api/sales', (route) => {
      const body = route.request().postDataJSON()
      requests.push(body)
      const entry = {
        id: entries.length + 1, type, createdAt: '2026-09-06T21:30:00Z',
        entryDate: body.backdated ? body.entryDate : '2026-09-07',
        mainItem: { id: 1, name: 'Дата маркер', quantity: 1, unit: 'PIECE', unitPrice: 5000, lineTotal: 5000 },
        itemsCount: 1, totalAmount: 5000, paidAmount: isOrder ? 1000 : 5000,
        remainingAmount: isOrder ? 4000 : 0, paymentStatus: isOrder ? 'PREPAID' : 'PAID',
        prepaymentAmount: isOrder ? 1000 : null, executionStatus: isOrder ? 'NEW' : 'COMPLETED',
        clientName: null, clientPhone: null, fulfillmentMethod: null, deliveryAddress: null, version: 0, matches: [],
      }
      entries.push(entry)
      return route.fulfill({ status: 201, json: entry })
    })
    await page.goto('/')

    async function openForm() {
      await page.getByRole('button', { name: `+ ${title}` }).click()
      const dialog = page.getByRole('dialog', { name: title, exact: true })
      await dialog.getByLabel('Название товара', { exact: true }).fill('Дата маркер')
      await dialog.getByLabel('Цена, ₽').fill('5000')
      if (isOrder) {
        await dialog.getByLabel('Телефон', { exact: true }).first().fill('9991234567')
        await dialog.getByRole('button', { name: '+ Добавить платёж' }).click()
        await dialog.getByLabel('Сумма платежа, ₽').fill('1000')
      }
      return dialog
    }

    let dialog = await openForm()
    let toggle = dialog.getByRole('checkbox', { name: toggleText })
    let date = dialog.getByRole('textbox', { name: dateLabel, exact: true })
    await expect(toggle).not.toBeChecked()
    await expect(date).toHaveCount(0)
    await toggle.check()
    await expect(date).toHaveValue('06.09.2026')
    const dateBox = await date.boundingBox()
    const footerBox = await dialog.locator('.dialog-footer').boundingBox()
    expect(dateBox!.y + dateBox!.height).toBeLessThanOrEqual(footerBox!.y)

    for (const invalid of ['31.02.2026', '07.09.2026', '08.09.2026', '06.09.202', '']) {
      await date.click()
      await date.press('ControlOrMeta+A')
      await date.press('Backspace')
      if (invalid) await enterDate(date, invalid.replaceAll('.', ''))
      await dialog.getByRole('button', { name: buttonText, exact: true }).click()
      await expect(dialog.locator('#entry-date-error')).toBeVisible()
      expect(requests).toHaveLength(0)
    }
    // Pasting uses the same mask component as the factory readiness date.
    await date.evaluate((node) => {
      const clipboardData = new DataTransfer()
      clipboardData.setData('text/plain', '05.09.2026')
      node.dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true }))
    })
    await expect(date).toHaveValue('05.09.2026')
    await toggle.uncheck()
    await expect(date).toHaveCount(0)
    await toggle.check()
    await expect(date).toHaveValue('06.09.2026')
    await dialog.getByRole('button', { name: buttonText, exact: true }).click()
    await expect(dialog).toHaveCount(0)
    expect(requests[0]).toMatchObject({ backdated: true, entryDate: '2026-09-06' })
    await expect(page.locator('.journal-group').filter({ hasText: 'Вчера' })).toBeVisible()
    await expect(page.locator('.entry-number small').first()).toBeEmpty()
    await expect(page.locator('.revenue-today')).toContainText('0 ₽')

    dialog = await openForm()
    toggle = dialog.getByRole('checkbox', { name: toggleText })
    date = dialog.getByRole('textbox', { name: dateLabel, exact: true })
    await expect(toggle).not.toBeChecked()
    await expect(date).toHaveCount(0)
    await toggle.check()
    await enterDate(date, '01012020')
    await toggle.uncheck()
    await dialog.getByRole('button', { name: buttonText, exact: true }).click()
    await expect(dialog).toHaveCount(0)
    expect(requests[1].backdated).toBe(false)
    expect(requests[1]).not.toHaveProperty('entryDate')
    await expect(page.locator('.revenue-today')).toContainText(isOrder ? '1 000 ₽' : '5 000 ₽')
  })
}
