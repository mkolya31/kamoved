import type { JournalEntry, JournalEntryDetails } from '../types'

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
    version: details.version,
  }
}
