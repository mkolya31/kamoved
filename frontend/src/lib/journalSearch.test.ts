import { describe, expect, it } from 'vitest'
import { formatSearchMatches, isJournalSearchActive } from './journalSearch'

describe('journal search helpers', () => {
  it('requires two meaningful letters or digits', () => {
    expect(isJournalSearchActive(' - ')).toBe(false)
    expect(isJournalSearchActive('я')).toBe(false)
    expect(isJournalSearchActive('з-1')).toBe(true)
  })

  it('formats matching fields and additional values', () => {
    expect(formatSearchMatches([
      {field: 'NAME', value: 'Владимир', additionalCount: 0},
      {field: 'ITEM', value: 'Готика Голд', additionalCount: 2},
    ])).toBe('имя — Владимир; товар — Готика Голд; ещё 2 товара')
  })
})
