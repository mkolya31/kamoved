import { describe, expect, it } from 'vitest'
import { serializeOrderFormState, type OrderFormState } from './orderFormState'

function baseState(): OrderFormState {
  return {
    items: [{ name: 'Готика Голд Кристалл 60 мм', quantity: '2', unit: 'SQUARE_METER', unitPrice: '1500' }],
    client: { name: 'Владимир', phone: '+7 999 123-45-67', comment: 'Основной покупатель' },
    additionalContacts: [{ name: 'Нурик', phone: '', comment: 'Прораб' }],
    paymentStatus: 'PREPAID',
    prepaymentAmount: '1000',
    executionStatus: 'NEW',
    fulfillmentMethod: 'DELIVERY_FACTORY',
    deliveryAddress: 'СНТ Ромашка, участок 12',
    comment: 'Позвонить за день до доставки',
  }
}

describe('serializeOrderFormState', () => {
  it('returns the same serialization for identical form states', () => {
    expect(serializeOrderFormState(baseState())).toBe(serializeOrderFormState(baseState()))
  })

  it.each([
    ['item name', (state: OrderFormState) => { state.items[0].name = 'Другой товар' }],
    ['item quantity', (state: OrderFormState) => { state.items[0].quantity = '3' }],
    ['item unit', (state: OrderFormState) => { state.items[0].unit = 'PIECE' }],
    ['item price', (state: OrderFormState) => { state.items[0].unitPrice = '999' }],
    ['added item', (state: OrderFormState) => {
      state.items.push({ name: '', quantity: '1', unit: 'PIECE', unitPrice: '' })
    }],
    ['removed item', (state: OrderFormState) => { state.items.pop() }],
    ['client name', (state: OrderFormState) => { state.client.name = '' }],
    ['client phone', (state: OrderFormState) => { state.client.phone = '' }],
    ['client comment', (state: OrderFormState) => { state.client.comment = '' }],
    ['additional contact', (state: OrderFormState) => { state.additionalContacts[0].name = 'Имя' }],
    ['payment status', (state: OrderFormState) => { state.paymentStatus = 'UNPAID' }],
    ['prepayment amount', (state: OrderFormState) => { state.prepaymentAmount = '500' }],
    ['execution status', (state: OrderFormState) => { state.executionStatus = 'COMPLETED' }],
    ['fulfillment method', (state: OrderFormState) => { state.fulfillmentMethod = '' }],
    ['delivery address', (state: OrderFormState) => { state.deliveryAddress = '' }],
    ['order comment', (state: OrderFormState) => { state.comment = '' }],
  ])('detects a change in %s', (_label, mutate) => {
    const changed = baseState()
    mutate(changed)
    expect(serializeOrderFormState(changed)).not.toBe(serializeOrderFormState(baseState()))
  })
})
