import type { JournalEntry } from '../types'
import { formatDate } from './format'

export type JournalMode = 'all' | 'active'

export interface JournalPaginationState {
  mode: JournalMode
  page: number
  hasNext: boolean
  loadingMore: boolean
  loadMoreError: boolean
  announcement: string
}

export type JournalPaginationAction =
  | { type: 'reset', mode: JournalMode }
  | { type: 'first-page-loaded', mode: JournalMode, page: number, hasNext: boolean }
  | { type: 'load-more-started', mode: JournalMode }
  | { type: 'load-more-loaded', mode: JournalMode, page: number, hasNext: boolean }
  | { type: 'load-more-failed', mode: JournalMode }

export function initialJournalPaginationState(
  mode: JournalMode = 'all',
): JournalPaginationState {
  return {
    mode,
    page: 0,
    hasNext: false,
    loadingMore: false,
    loadMoreError: false,
    announcement: '',
  }
}

export function journalPaginationReducer(
  state: JournalPaginationState,
  action: JournalPaginationAction,
): JournalPaginationState {
  if (action.type === 'reset') {
    return initialJournalPaginationState(action.mode)
  }

  if (action.mode !== state.mode) {
    return state
  }

  switch (action.type) {
    case 'first-page-loaded':
      return {
        ...state,
        page: action.page,
        hasNext: action.hasNext,
      }
    case 'load-more-started':
      if (state.loadingMore || !state.hasNext) return state
      return {
        ...state,
        loadingMore: true,
        loadMoreError: false,
        announcement: '',
      }
    case 'load-more-loaded':
      return {
        ...state,
        page: action.page,
        hasNext: action.hasNext,
        loadingMore: false,
        loadMoreError: false,
        announcement: action.hasNext
          ? 'Более ранние записи загружены'
          : 'Загружены последние записи. Более ранних записей нет',
      }
    case 'load-more-failed':
      return {
        ...state,
        loadingMore: false,
        loadMoreError: true,
        announcement: '',
      }
  }
}

export function appendUniqueEntries(
  current: JournalEntry[],
  nextPage: JournalEntry[],
): JournalEntry[] {
  const knownIds = new Set(current.map(({ id }) => id))
  const result = [...current]

  nextPage.forEach((entry) => {
    if (knownIds.has(entry.id)) return
    knownIds.add(entry.id)
    result.push(entry)
  })

  return result
}

export function groupJournalEntries(
  entries: JournalEntry[],
): [string, JournalEntry[]][] {
  const grouped = new Map<string, JournalEntry[]>()

  entries.forEach((entry) => {
    const key = formatDate(entry.createdAt)
    grouped.set(key, [...(grouped.get(key) ?? []), entry])
  })

  return [...grouped.entries()]
}

export function isLatestJournalRequest(
  requestGeneration: number,
  currentGeneration: number,
): boolean {
  return requestGeneration === currentGeneration
}
