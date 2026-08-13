import type {
  ExecutionStatus,
  FulfillmentMethod,
  PaymentStatus,
  UnitOfMeasure,
} from '../types'

const moneyFormatter = new Intl.NumberFormat('ru-RU', {
  maximumFractionDigits: 0,
})

const quantityFormatter = new Intl.NumberFormat('ru-RU', {
  maximumFractionDigits: 3,
})

export const unitLabels: Record<UnitOfMeasure, string> = {
  PIECE: 'шт.',
  SQUARE_METER: 'м²',
  LINEAR_METER: 'пог. м',
  PACKAGE: 'уп.',
}

export const executionLabels: Record<ExecutionStatus, string> = {
  NEW: 'Новый',
  ORDERED_FACTORY: 'Заказан на заводе',
  IN_PRODUCTION: 'В производстве',
  READY_FACTORY: 'Готов на заводе',
  IN_TRANSIT_TO_WAREHOUSE: 'В пути на склад',
  AT_WAREHOUSE: 'На нашем складе',
  OUT_FOR_DELIVERY: 'В доставке клиенту',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
}

export const paymentLabels: Record<PaymentStatus, string> = {
  UNPAID: 'Не оплачено',
  PREPAID: 'Предоплата',
  PAID: 'Оплачено',
}

export const fulfillmentLabels: Record<FulfillmentMethod, string> = {
  PICKUP_WAREHOUSE: 'Самовывоз со склада',
  PICKUP_FACTORY: 'Самовывоз с завода',
  DELIVERY: 'Доставка клиенту',
}

export function formatMoney(value: number): string {
  return `${moneyFormatter.format(value)} ₽`
}

export function formatQuantity(value: number, unit: UnitOfMeasure): string {
  return `${quantityFormatter.format(value)} ${unitLabels[unit]}`
}

export function formatTime(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatDate(value: string): string {
  const date = new Date(value)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)

  const key = date.toDateString()
  if (key === today.toDateString()) return 'Сегодня'
  if (key === yesterday.toDateString()) return 'Вчера'

  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: date.getFullYear() === today.getFullYear() ? undefined : 'numeric',
  }).format(date)
}
