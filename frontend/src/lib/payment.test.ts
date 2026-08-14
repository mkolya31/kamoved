import { describe, expect, it } from 'vitest'
import { evaluatePaymentDraft } from './payment'

describe('evaluatePaymentDraft', () => {
  it('clears the paid amount for an unpaid order', () => {
    expect(evaluatePaymentDraft('UNPAID', '400', 1000)).toEqual({
      paymentStatus: 'UNPAID',
      paidAmount: 0,
      remainingAmount: 1000,
      error: '',
    })
  })

  it('calculates paid and remaining amounts for a prepayment', () => {
    expect(evaluatePaymentDraft('PREPAID', '400,50', 1000)).toEqual({
      paymentStatus: 'PREPAID',
      paidAmount: 400.5,
      remainingAmount: 599.5,
      error: '',
    })
  })

  it('turns a full entered amount into the paid status', () => {
    expect(evaluatePaymentDraft('PREPAID', '1000', 1000)).toEqual({
      paymentStatus: 'PAID',
      paidAmount: 1000,
      remainingAmount: 0,
      error: '',
    })
  })

  it('uses the full order amount for the paid status', () => {
    expect(evaluatePaymentDraft('PAID', '', 1000)).toEqual({
      paymentStatus: 'PAID',
      paidAmount: 1000,
      remainingAmount: 0,
      error: '',
    })
  })

  it('rejects an amount greater than the order total', () => {
    const result = evaluatePaymentDraft('PREPAID', '1000.01', 1000)

    expect(result.error).toBe('Внесённая сумма не может быть больше суммы заказа')
  })

  it('requires a valid positive amount for a prepayment', () => {
    expect(evaluatePaymentDraft('PREPAID', '', 1000).error).toContain('Укажите внесённую сумму')
    expect(evaluatePaymentDraft('PREPAID', '0', 1000).error)
      .toBe('Внесённая сумма должна быть больше нуля')
  })
})
