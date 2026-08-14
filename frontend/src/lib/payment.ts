import type { PaymentStatus } from '../types'

export interface PaymentDraftEvaluation {
  paymentStatus: PaymentStatus
  paidAmount: number
  remainingAmount: number
  error: string
}

function roundMoney(value: number): number {
  return Math.round(value * 100) / 100
}

export function evaluatePaymentDraft(
  selectedStatus: PaymentStatus,
  paidAmountInput: string,
  totalAmount: number,
): PaymentDraftEvaluation {
  if (selectedStatus === 'UNPAID') {
    return {
      paymentStatus: 'UNPAID',
      paidAmount: 0,
      remainingAmount: totalAmount,
      error: '',
    }
  }

  if (selectedStatus === 'PAID') {
    return {
      paymentStatus: 'PAID',
      paidAmount: totalAmount,
      remainingAmount: 0,
      error: '',
    }
  }

  const normalized = paidAmountInput.trim().replace(',', '.')
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) {
    return {
      paymentStatus: 'PREPAID',
      paidAmount: 0,
      remainingAmount: totalAmount,
      error: 'Укажите внесённую сумму — не более двух знаков после запятой',
    }
  }

  const paidAmount = Number(normalized)
  if (paidAmount <= 0) {
    return {
      paymentStatus: 'PREPAID',
      paidAmount,
      remainingAmount: totalAmount,
      error: 'Внесённая сумма должна быть больше нуля',
    }
  }
  if (paidAmount > totalAmount) {
    return {
      paymentStatus: 'PREPAID',
      paidAmount,
      remainingAmount: 0,
      error: 'Внесённая сумма не может быть больше суммы заказа',
    }
  }
  if (paidAmount === totalAmount) {
    return {
      paymentStatus: 'PAID',
      paidAmount: totalAmount,
      remainingAmount: 0,
      error: '',
    }
  }

  return {
    paymentStatus: 'PREPAID',
    paidAmount,
    remainingAmount: roundMoney(totalAmount - paidAmount),
    error: '',
  }
}
