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

export type PaymentMethod = 'CASH' | 'BANK_ACCOUNT' | 'CARD' | 'PERSONAL_TRANSFER'

export type FulfillmentMethod =
  | 'PICKUP_WAREHOUSE'
  | 'PICKUP_FACTORY'
  | 'DELIVERY_FACTORY'
  | 'DELIVERY_MARKET'

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
  prepaymentAmount: number | null
  paidAmount: number
  remainingAmount: number
  executionStatus: ExecutionStatus
  clientName: string | null
  clientPhone: string | null
  fulfillmentMethod: FulfillmentMethod | null
  deliveryAddress: string | null
  factoryReadyDate?: string | null
  factoryReadyAttention?: boolean
  version: number
  matches: JournalSearchMatch[]
}

export type JournalSearchField = 'ENTRY_NUMBER' | 'NAME' | 'PHONE' | 'ADDRESS' | 'ITEM'

export interface JournalSearchMatch {
  field: JournalSearchField
  value: string
  additionalCount: number
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
  paidAmount: number
  remainingAmount: number
  payments: PaymentDetails[]
  executionStatus: ExecutionStatus
  client: JournalContact | null
  additionalContacts: JournalContact[]
  fulfillmentMethod: FulfillmentMethod | null
  deliveryAddress: string | null
  comment: string | null
  factoryReadyDate?: string | null
  factoryReadyAttention?: boolean
  createdByDisplayName: string
  updatedAt: string
  version: number
}

export interface PaymentDetails {
  id: number
  amount: number
  paymentMethod: PaymentMethod | null
  comment: string | null
  receivedAt: string
  createdByDisplayName: string
  createdAt: string
  active: boolean
  voidedAt: string | null
  voidedByDisplayName: string | null
  correctionOfId: number | null
  correctionReason: string | null
}

export interface JournalPage {
  items: JournalEntry[]
  page: number
  size: number
  hasNext: boolean
  todayRevenue: number
  totalItems: number
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
  initialPayment?: PaymentInput
  executionStatus: ExecutionStatus
  fulfillmentMethod?: FulfillmentMethod
  deliveryAddress?: string
  comment?: string
  factoryReadyDate?: string
}

export interface PaymentInput {
  amount: number
  paymentMethod: PaymentMethod
  comment?: string
}

export interface UpdateOrderInput extends OrderInput {
  version: number
}
