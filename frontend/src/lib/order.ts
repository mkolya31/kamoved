import type { FulfillmentMethod, JournalEntry, JournalEntryDetails } from '../types'
import { fulfillmentLabels } from './format'

export interface FulfillmentDisplay {
  label: string
  address: string | null
}

export function fulfillmentDisplay(
  method: FulfillmentMethod | null,
  address: string | null,
): FulfillmentDisplay | null {
  if (!method && !address) return null
  return {
    label: method ? fulfillmentLabels[method] : 'Способ получения не указан',
    address,
  }
}

export function shouldShowDeliveryAddressField(
  method: FulfillmentMethod | '',
  preservesUnspecifiedAddress: boolean,
): boolean {
  return method === 'DELIVERY_FACTORY'
    || method === 'DELIVERY_MARKET'
    || (method === '' && preservesUnspecifiedAddress)
}

export function deliveryAddressPayload(
  method: FulfillmentMethod | '',
  address: string,
  preservesUnspecifiedAddress: boolean,
): string | undefined {
  if (!shouldShowDeliveryAddressField(method, preservesUnspecifiedAddress)) return undefined
  return address.trim() || undefined
}

export function formatAdditionalItemsCount(itemsCount: number): string | null {
  const additionalCount = itemsCount - 1
  if (additionalCount < 1) return null

  const modulo100 = additionalCount % 100
  const modulo10 = additionalCount % 10
  const unit = modulo100 >= 11 && modulo100 <= 14
    ? 'товаров'
    : modulo10 === 1
      ? 'товар'
      : modulo10 >= 2 && modulo10 <= 4
        ? 'товара'
        : 'товаров'

  return `+ ещё ${additionalCount} ${unit}`
}

export function summaryFromDetails(
  current: JournalEntry,
  details: JournalEntryDetails,
): JournalEntry {
  return {
    ...current,
    createdAt: details.createdAt,
    mainItem: details.items[0] ?? null,
    itemsCount: details.items.length,
    totalAmount: details.totalAmount,
    paymentStatus: details.paymentStatus,
    prepaymentAmount: details.prepaymentAmount,
    paidAmount: details.paidAmount,
    remainingAmount: details.remainingAmount,
    executionStatus: details.executionStatus,
    clientName: details.client?.name ?? null,
    clientPhone: details.client?.phone ?? null,
    fulfillmentMethod: details.fulfillmentMethod,
    deliveryAddress: details.deliveryAddress,
    factoryReadyDate: details.factoryReadyDate,
    factoryReadyAttention: details.factoryReadyAttention,
    version: details.version,
  }
}
