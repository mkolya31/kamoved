import type {
  ExecutionStatus,
  FulfillmentMethod,
  PaymentMethod,
  UnitOfMeasure,
} from '../types'

export interface OrderFormItemState {
  name: string
  quantity: string
  unit: UnitOfMeasure
  unitPrice: string
}

export interface OrderFormContactState {
  name: string
  phone: string
  comment: string
}

export interface OrderFormState {
  items: OrderFormItemState[]
  client: OrderFormContactState
  additionalContacts: OrderFormContactState[]
  initialPaymentOpen: boolean
  paymentAmount: string
  paymentMethod: PaymentMethod
  paymentComment: string
  executionStatus: ExecutionStatus
  fulfillmentMethod: FulfillmentMethod | ''
  deliveryAddress: string
  comment: string
  factoryReadyDate?: string
}

export function serializeOrderFormState(state: OrderFormState): string {
  return JSON.stringify(state)
}
