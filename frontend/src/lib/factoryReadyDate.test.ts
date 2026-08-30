import { describe, expect, it } from 'vitest'
import {
  displayFactoryReadyDate,
  currentMoscowDate,
  maskFactoryReadyDate,
  parseFactoryReadyDate,
  shortFactoryReadyDate,
} from './factoryReadyDate'

describe('factory ready date', () => {
  it('adds dots while the user types', () => {
    expect(maskFactoryReadyDate('31082026')).toBe('31.08.2026')
  })

  it('round-trips a valid date', () => {
    expect(parseFactoryReadyDate('31.08.2026')).toBe('2026-08-31')
    expect(displayFactoryReadyDate('2026-08-31')).toBe('31.08.2026')
    expect(shortFactoryReadyDate('2026-08-31')).toBe('31.08')
  })

  it('rejects nonexistent dates', () => {
    expect(parseFactoryReadyDate('31.02.2026')).toBeUndefined()
  })

  it('uses the Moscow calendar date', () => {
    expect(currentMoscowDate(new Date('2026-08-28T21:30:00Z'))).toBe('2026-08-29')
  })
})
