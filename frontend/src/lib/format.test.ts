import { afterEach, describe, expect, it, vi } from 'vitest'
import { formatDate } from './format'

describe('formatDate', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps relative labels without a weekday', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-19T12:00:00'))

    expect(formatDate('2026-08-19T08:00:00')).toBe('Сегодня')
    expect(formatDate('2026-08-18T08:00:00')).toBe('Вчера')
  })

  it('adds a full weekday after a date from the current year', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-19T12:00:00'))

    expect(formatDate('2026-08-17T08:00:00')).toBe('17 августа · понедельник')
  })

  it('keeps the year and adds a weekday for a date from another year', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-19T12:00:00'))

    expect(formatDate('2025-08-18T08:00:00')).toBe('18 августа 2025 г. · понедельник')
  })
})
