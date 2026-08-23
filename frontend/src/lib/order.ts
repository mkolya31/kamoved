import type { JournalEntry, JournalEntryDetails } from '../types'

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
