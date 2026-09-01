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

async function expectFooterToStayFixed(dialog: Locator) {
  const scrollArea = dialog.locator('.sale-dialog-scroll')
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

for (const viewport of [
  { name: 'desktop', width: 1280, height: 720 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`sale creation keeps its footer visible on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await mockEmptyJournal(page)
    await page.goto('/')

    await page.getByRole('button', { name: '+ Продажа из наличия' }).click()
    const dialog = page.getByRole('dialog', { name: 'Продажа из наличия' })
    const addItem = dialog.getByRole('button', { name: '+ Добавить позицию' })
    await addItem.click()
    await addItem.click()

    const footer = dialog.locator('.dialog-footer')
    await dialog.getByLabel('Цена, ₽').first().fill('2500')
    await expect(footer.getByText('2 500 ₽', { exact: true })).toBeVisible()
    await expectFooterToStayFixed(dialog)

    if (viewport.name === 'mobile') {
      const [totalBox, cancelBox] = await Promise.all([
        footer.getByText('Итого', { exact: true }).boundingBox(),
        footer.getByRole('button', { name: 'Отмена' }).boundingBox(),
      ])
      expect(totalBox).not.toBeNull()
      expect(cancelBox).not.toBeNull()
      expect(cancelBox!.y).toBeGreaterThan(totalBox!.y + totalBox!.height)
    }
  })
}
