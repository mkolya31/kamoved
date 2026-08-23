import type {
  JournalEntry,
  JournalEntryDetails,
  JournalPage,
  OrderInput,
  PaymentDetails,
  PaymentInput,
  PaymentMethod,
  SaleItemInput,
  ExecutionStatus,
  User,
  UpdateOrderInput,
} from '../types'

interface CsrfResponse {
  headerName: string
  token: string
}

interface ApiErrorBody {
  message?: string
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

async function parseError(response: Response): Promise<ApiError> {
  let body: ApiErrorBody | undefined
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    body = undefined
  }

  const fallback = response.status === 401
    ? 'Неверный логин или пароль'
    : 'Не удалось выполнить запрос'

  return new ApiError(body?.message ?? fallback, response.status)
}

async function csrf(): Promise<CsrfResponse> {
  const response = await fetch('/api/auth/csrf', { credentials: 'include' })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<CsrfResponse>
}

async function mutate<T>(path: string, init: RequestInit): Promise<T> {
  const token = await csrf()
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [token.headerName]: token.token,
      ...init.headers,
    },
  })

  if (!response.ok) throw await parseError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function currentUser(): Promise<User | null> {
  const response = await fetch('/api/auth/me', { credentials: 'include' })
  if (response.status === 401) return null
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<User>
}

export function login(username: string, password: string): Promise<User> {
  return mutate<User>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function logout(): Promise<void> {
  return mutate<void>('/api/auth/logout', { method: 'POST' })
}

export async function loadJournal(
  mode: 'all' | 'active',
  page = 0,
  size = 30,
): Promise<JournalPage> {
  const response = await fetch(`/api/journal?mode=${mode}&page=${page}&size=${size}`, {
    credentials: 'include',
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<JournalPage>
}

export async function searchJournal(
  query: string,
  mode: 'all' | 'active',
  page = 0,
  size = 30,
): Promise<JournalPage> {
  const parameters = new URLSearchParams({query, mode, page: String(page), size: String(size)})
  const response = await fetch(`/api/journal/search?${parameters}`, {
    credentials: 'include',
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<JournalPage>
}

export async function loadJournalEntry(id: number): Promise<JournalEntryDetails> {
  const response = await fetch(`/api/journal/${id}`, { credentials: 'include' })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<JournalEntryDetails>
}

export function createSale(
  items: SaleItemInput[],
  paymentMethod: PaymentMethod,
  paymentComment?: string,
  comment?: string,
): Promise<JournalEntry> {
  return mutate<JournalEntry>('/api/sales', {
    method: 'POST',
    body: JSON.stringify({ items, paymentMethod, paymentComment, comment }),
  })
}

export function createOrder(order: OrderInput): Promise<JournalEntry> {
  return mutate<JournalEntry>('/api/orders', {
    method: 'POST',
    body: JSON.stringify(order),
  })
}

export function updateOrder(id: number, order: UpdateOrderInput): Promise<JournalEntryDetails> {
  return mutate<JournalEntryDetails>(`/api/orders/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(order),
  })
}

export function updateOrderExecutionStatus(
  id: number,
  executionStatus: ExecutionStatus,
  version: number,
): Promise<JournalEntry> {
  return mutate<JournalEntry>(`/api/orders/${id}/execution-status`, {
    method: 'PATCH',
    body: JSON.stringify({ executionStatus, version }),
  })
}

export function addOrderPayment(
  id: number,
  payment: PaymentInput,
): Promise<JournalEntry> {
  return mutate<JournalEntry>(`/api/orders/${id}/payments`, {
    method: 'POST',
    body: JSON.stringify(payment),
  })
}

export function correctPayment(
  payment: PaymentDetails,
  correction: {
    amount?: number
    paymentMethod: PaymentMethod
    comment?: string
    reason: string
  },
): Promise<JournalEntryDetails> {
  return mutate<JournalEntryDetails>(`/api/payments/${payment.id}/corrections`, {
    method: 'POST',
    body: JSON.stringify(correction),
  })
}
