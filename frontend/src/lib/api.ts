import type {
  JournalEntry,
  JournalEntryDetails,
  JournalPage,
  OrderInput,
  PaymentStatus,
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

export async function loadJournal(mode: 'all' | 'active'): Promise<JournalPage> {
  const response = await fetch(`/api/journal?mode=${mode}&page=0&size=30`, {
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

export function createSale(items: SaleItemInput[], comment?: string): Promise<JournalEntry> {
  return mutate<JournalEntry>('/api/sales', {
    method: 'POST',
    body: JSON.stringify({ items, comment }),
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

export function updateOrderPayment(
  id: number,
  paymentStatus: PaymentStatus,
  paidAmount: number | undefined,
  version: number,
): Promise<JournalEntry> {
  return mutate<JournalEntry>(`/api/orders/${id}/payment`, {
    method: 'PATCH',
    body: JSON.stringify({ paymentStatus, paidAmount, version }),
  })
}
