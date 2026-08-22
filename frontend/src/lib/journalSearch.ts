import type { JournalSearchField, JournalSearchMatch } from '../types'

const fieldLabels: Record<JournalSearchField, string> = {
  ENTRY_NUMBER: 'номер записи',
  NAME: 'имя',
  PHONE: 'телефон',
  ADDRESS: 'адрес',
  ITEM: 'товар',
}

export function isJournalSearchActive(query: string): boolean {
  return (query.match(/[\p{L}\p{N}]/gu)?.length ?? 0) >= 2
}

function additionalUnit(field: JournalSearchField, count: number): string {
  const forms = field === 'ITEM'
    ? ['товар', 'товара', 'товаров']
    : ['контакт', 'контакта', 'контактов']
  const modulo100 = count % 100
  const modulo10 = count % 10
  if (modulo100 >= 11 && modulo100 <= 14) return forms[2]
  if (modulo10 === 1) return forms[0]
  if (modulo10 >= 2 && modulo10 <= 4) return forms[1]
  return forms[2]
}

export function formatSearchMatches(matches: JournalSearchMatch[]): string {
  return matches.map((match) => {
    const additional = match.additionalCount > 0
      ? `; ещё ${match.additionalCount} ${additionalUnit(match.field, match.additionalCount)}`
      : ''
    return `${fieldLabels[match.field]} — ${match.value}${additional}`
  }).join('; ')
}
