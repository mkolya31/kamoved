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

export type FulfillmentMethod =
  | 'PICKUP_WAREHOUSE'
  | 'PICKUP_FACTORY'
  | 'DELIVERY'

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
  clientName: string | null
  clientPhone: string | null
  fulfillmentMethod: FulfillmentMethod | null
  deliveryAddress: string | null
}

export interface JournalContact {
  id: number
  name: string | null
  phone: string | null
  comment: string | null
}

export interface JournalEntryDetails {
  id: number
  type: EntryType
  createdAt: string
  items: JournalItem[]
  totalAmount: number
  paymentStatus: PaymentStatus
  prepaymentAmount: number | null
  remainingAmount: number
  executionStatus: ExecutionStatus
  client: JournalContact | null
  additionalContacts: JournalContact[]
  fulfillmentMethod: FulfillmentMethod | null
  deliveryAddress: string | null
  comment: string | null
  createdByDisplayName: string
  updatedAt: string
  version: number
}

export interface JournalPage {
  items: JournalEntry[]
  page: number
  size: number
  hasNext: boolean
  todayRevenue: number
}

export interface SaleItemInput {
  name: string
  quantity: number
  unit: UnitOfMeasure
  unitPrice: number
}

export interface ContactInput {
  name?: string
  phone?: string
  comment?: string
}

export interface OrderInput {
  items: SaleItemInput[]
  client?: ContactInput
  additionalContacts: ContactInput[]
  paymentStatus: PaymentStatus
  prepaymentAmount?: number
  executionStatus: ExecutionStatus
  fulfillmentMethod?: FulfillmentMethod
  deliveryAddress?: string
  comment?: string
}
