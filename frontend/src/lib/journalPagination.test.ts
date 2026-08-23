import { describe, expect, it } from 'vitest'
import type { JournalEntry } from '../types'
import {
  appendUniqueEntries,
  groupJournalEntries,
  initialJournalPaginationState,
  isLatestJournalRequest,
  journalPaginationReducer,
} from './journalPagination'

function entry(id: number, createdAt = '2025-01-10T12:00:00+03:00'): JournalEntry {
  return {
    id,
    type: 'SALE',
    createdAt,
    mainItem: null,
    itemsCount: 0,
    totalAmount: id,
    paymentStatus: 'PAID',
    prepaymentAmount: null,
    paidAmount: id,
    remainingAmount: 0,
    executionStatus: 'COMPLETED',
    clientName: null,
    clientPhone: null,
    fulfillmentMethod: null,
    deliveryAddress: null,
    version: 0,
    matches: [],
  }
}

describe('journal pagination', () => {
  it('appends an older page and removes shifted offset duplicates by id', () => {
    const current = [entry(5), entry(4), entry(3)]
    const nextPage = [entry(3), entry(2), entry(2), entry(1)]

    expect(appendUniqueEntries(current, nextPage).map(({ id }) => id))
      .toEqual([5, 4, 3, 2, 1])
  })

  it('keeps one date group when the page boundary is inside the same day', () => {
    const groups = groupJournalEntries([
      entry(3, '2025-01-10T18:00:00+03:00'),
      entry(2, '2025-01-10T08:00:00+03:00'),
      entry(1, '2025-01-09T18:00:00+03:00'),
    ])

    expect(groups).toHaveLength(2)
    expect(groups[0][1].map(({ id }) => id)).toEqual([3, 2])
    expect(groups[1][1].map(({ id }) => id)).toEqual([1])
  })

  it('tracks loading, failure, retry and the end of the journal', () => {
    let state = journalPaginationReducer(initialJournalPaginationState(), {
      type: 'first-page-loaded',
      mode: 'all',
      page: 0,
      hasNext: true,
    })

    state = journalPaginationReducer(state, {type: 'load-more-started', mode: 'all'})
    expect(state.loadingMore).toBe(true)

    state = journalPaginationReducer(state, {type: 'load-more-failed', mode: 'all'})
    expect(state).toMatchObject({loadingMore: false, loadMoreError: true, hasNext: true})

    state = journalPaginationReducer(state, {type: 'load-more-started', mode: 'all'})
    expect(state).toMatchObject({loadingMore: true, loadMoreError: false})

    state = journalPaginationReducer(state, {
      type: 'load-more-loaded',
      mode: 'all',
      page: 1,
      hasNext: false,
    })
    expect(state).toMatchObject({page: 1, hasNext: false, loadingMore: false})
    expect(state.announcement).toContain('Более ранних записей нет')
  })

  it('resets pagination and ignores metadata from the previous mode', () => {
    const allMode = journalPaginationReducer(initialJournalPaginationState(), {
      type: 'first-page-loaded',
      mode: 'all',
      page: 0,
      hasNext: true,
    })
    const activeMode = journalPaginationReducer(allMode, {type: 'reset', mode: 'active'})
    const afterStaleResponse = journalPaginationReducer(activeMode, {
      type: 'load-more-loaded',
      mode: 'all',
      page: 1,
      hasNext: true,
    })

    expect(activeMode).toEqual(initialJournalPaginationState('active'))
    expect(afterStaleResponse).toBe(activeMode)
    expect(isLatestJournalRequest(4, 5)).toBe(false)
    expect(isLatestJournalRequest(5, 5)).toBe(true)
  })
})
