import { describe, expect, it, vi, afterEach } from 'vitest'
import { entryDateError, isBackdatedEntry, journalEntryDate, yesterdayMoscowDate } from './entryDate'
import { formatDate } from './format'
import { groupJournalEntries } from './journalPagination'
import type { JournalEntry } from '../types'

const now = new Date('2026-09-06T21:01:00Z')
afterEach(() => vi.useRealTimers())

describe('entry dates in Moscow', () => {
  it('defaults to yesterday across midnight, year and leap-day boundaries', () => {
    expect(yesterdayMoscowDate(now)).toBe('2026-09-06')
    expect(yesterdayMoscowDate(new Date('2025-12-31T21:01:00Z'))).toBe('2025-12-31')
    expect(yesterdayMoscowDate(new Date('2024-03-01T00:00:00Z'))).toBe('2024-02-29')
  })

  it.each(['', '__.__.2026', '06.09.202_', '31.02.2026', '29.02.2025', '07.09.2026', '08.09.2026', '01.01.0000'])(
    'rejects invalid or non-past date %s', (value) => expect(entryDateError(value, now)).toBeTruthy(),
  )

  it.each(['06.09.2026', '29.02.2024', '01.01.0001'])(
    'accepts past date without a recency limit: %s', (value) => expect(entryDateError(value, now)).toBeUndefined(),
  )

  it('uses the event date for groups and preserves the creation timestamp', () => {
    vi.useFakeTimers()
    vi.setSystemTime(now)
    const entry = { id: 1, entryDate: '2026-09-06', createdAt: now.toISOString() } as JournalEntry
    expect(groupJournalEntries([entry])[0][0]).toBe('Вчера')
    expect(isBackdatedEntry(entry)).toBe(true)
    expect(entry.createdAt).toBe(now.toISOString())
    expect(journalEntryDate({ createdAt: now.toISOString() })).toBe('2026-09-07')
    expect(isBackdatedEntry({ createdAt: now.toISOString() })).toBe(false)
    expect(formatDate('2026-09-06T21:00:00Z')).toBe('Сегодня')
  })
})
