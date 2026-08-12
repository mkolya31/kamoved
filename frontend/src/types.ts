export type EntryType = 'SALE' | 'ORDER'

export type ExecutionStatus =
  | 'NEW'
  | 'ORDERED_FACTORY'
  | 'IN_PRODUCTION'
  | 'READY_FACTORY'
  | 'IN_TRANSIT_TO_WAREHOUSE'
  | 'AT_WAREHOUSE'
  | 'OUT_FOR_DELIVERY'
  | 'COMPLETED'
  | 'CANCELLED'

export type PaymentStatus = 'UNPAID' | 'PREPAID' | 'PAID'

export type UnitOfMeasure =
  | 'PIECE'
  | 'SQUARE_METER'
  | 'LINEAR_METER'
  | 'PACKAGE'

export interface User {
  username: string
  displayName: string
}

export interface JournalItem {
  id: number
  name: string
  quantity: number
  unit: UnitOfMeasure
  unitPrice: number
  lineTotal: number
}

export interface JournalEntry {
  id: number
  type: EntryType
  createdAt: string
  mainItem: JournalItem | null
  itemsCount: number
  totalAmount: number
  paymentStatus: PaymentStatus
  executionStatus: ExecutionStatus
}

export interface JournalEntryDetails {
  id: number
  type: EntryType
  createdAt: string
  items: JournalItem[]
  totalAmount: number
  paymentStatus: PaymentStatus
  executionStatus: ExecutionStatus
}

export interface JournalPage {
  items: JournalEntry[]
  page: number
  size: number
  hasNext: boolean
}

export interface SaleItemInput {
  name: string
  quantity: number
  unit: UnitOfMeasure
  unitPrice: number
}
