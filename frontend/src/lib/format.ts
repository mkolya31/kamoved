import { currentMoscowDate } from './factoryReadyDate'
import { yesterdayMoscowDate } from './entryDate'
import type {
  ExecutionStatus,
  FulfillmentMethod,
  PaymentMethod,
  PaymentStatus,
  UnitOfMeasure,
} from '../types'

const moneyFormatter = new Intl.NumberFormat('ru-RU', {
  maximumFractionDigits: 2,
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

export const paymentMethodLabels: Record<PaymentMethod, string> = {
  CASH: 'Наличными',
  BANK_ACCOUNT: 'По реквизитам',
  CARD: 'Картой',
  PERSONAL_TRANSFER: 'Переводом',
}

export const fulfillmentLabels: Record<FulfillmentMethod, string> = {
  PICKUP_WAREHOUSE: 'Самовывоз со склада',
  PICKUP_FACTORY: 'Самовывоз с завода',
  DELIVERY_FACTORY: 'Доставка от завода',
  DELIVERY_MARKET: 'Доставка от рынка',
}

export function formatMoney(value: number): string {
  return `${moneyFormatter.format(value)} ₽`
}

export function formatQuantity(value: number, unit: UnitOfMeasure): string {
  return `${quantityFormatter.format(value)} ${unitLabels[unit]}`
}

export function formatTime(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Europe/Moscow',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatDate(value: string): string {
  const key = /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : currentMoscowDate(new Date(value))
  const today = currentMoscowDate()
  if (key === today) return 'Сегодня'
  if (key === yesterdayMoscowDate()) return 'Вчера'
  const date = new Date(`${key}T00:00:00Z`)
  const formattedDate = new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'UTC',
    day: 'numeric',
    month: 'long',
    year: key.slice(0, 4) === today.slice(0, 4) ? undefined : 'numeric',
  }).format(date)
  const weekday = new Intl.DateTimeFormat('ru-RU', { timeZone: 'UTC', weekday: 'long' }).format(date)
  return `${formattedDate} · ${weekday}`
}
