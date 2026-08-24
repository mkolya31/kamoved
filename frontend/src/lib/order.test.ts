import { describe, expect, it } from 'vitest'
import type { JournalEntry, JournalEntryDetails } from '../types'
import { formatAdditionalItemsCount, summaryFromDetails } from './order'

describe('formatAdditionalItemsCount', () => {
  it.each([
    [0, null],
    [1, null],
    [2, '+ ещё 1 товар'],
    [3, '+ ещё 2 товара'],
    [6, '+ ещё 5 товаров'],
    [12, '+ ещё 11 товаров'],
    [23, '+ ещё 22 товара'],
  ])('formats a total of %s items', (itemsCount, expected) => {
    expect(formatAdditionalItemsCount(itemsCount)).toBe(expected)
  })
})

describe('summaryFromDetails', () => {
  it('updates every journal column affected by full order editing', () => {
    const current: JournalEntry = {
      id: 12,
      type: 'ORDER',
      createdAt: '2026-08-14T10:00:00+03:00',
      mainItem: null,
      itemsCount: 0,
      totalAmount: 0,
      paymentStatus: 'UNPAID',
      prepaymentAmount: null,
      paidAmount: 0,
      remainingAmount: 0,
      executionStatus: 'NEW',
      clientName: null,
      clientPhone: null,
      fulfillmentMethod: null,
      deliveryAddress: null,
      version: 0,
      matches: [],
    }
    const details: JournalEntryDetails = {
      id: 12,
      type: 'ORDER',
      createdAt: '2026-08-14T10:00:00+03:00',
      items: [{
        id: 31,
        name: 'Обновлённый товар',
        quantity: 2,
        unit: 'PACKAGE',
        unitPrice: 1500,
        lineTotal: 3000,
      }],
      totalAmount: 3000,
      paymentStatus: 'PREPAID',
      prepaymentAmount: 1000,
      paidAmount: 1000,
      remainingAmount: 2000,
      payments: [],
      executionStatus: 'READY_FACTORY',
      client: {id: 41, name: 'Максим', phone: '+7 999 111-22-33', comment: null},
      additionalContacts: [],
      fulfillmentMethod: 'DELIVERY_MARKET',
      deliveryAddress: 'Новый адрес',
      comment: 'Новый комментарий',
      createdByDisplayName: 'Камень Клинкер Про',
      updatedAt: '2026-08-14T11:00:00+03:00',
      version: 1,
    }

    expect(summaryFromDetails(current, details)).toMatchObject({
      mainItem: details.items[0],
      itemsCount: 1,
      totalAmount: 3000,
      paymentStatus: 'PREPAID',
      prepaymentAmount: 1000,
      paidAmount: 1000,
      remainingAmount: 2000,
      executionStatus: 'READY_FACTORY',
      clientName: 'Максим',
      clientPhone: '+7 999 111-22-33',
      fulfillmentMethod: 'DELIVERY_MARKET',
      deliveryAddress: 'Новый адрес',
      version: 1,
    })
  })
})
