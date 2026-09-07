import { currentMoscowDate, displayFactoryReadyDate, parseFactoryReadyDate } from './factoryReadyDate'

export function yesterdayMoscowDate(now = new Date()): string {
  const date = new Date(`${currentMoscowDate(now)}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() - 1)
  return date.toISOString().slice(0, 10)
}

export function defaultEntryDate(): string {
  return displayFactoryReadyDate(yesterdayMoscowDate())
}

export function entryDateError(value: string, now = new Date()): string | undefined {
  const date = parseFactoryReadyDate(value)
  if (!date || date.startsWith('0000-')) return 'Укажите существующую дату в формате ДД.ММ.ГГГГ'
  if (date >= currentMoscowDate(now)) return 'Дата должна быть раньше сегодняшней по Москве'
  return undefined
}

export function journalEntryDate(entry: { entryDate?: string, createdAt: string }): string {
  return entry.entryDate ?? currentMoscowDate(new Date(entry.createdAt))
}

export function isBackdatedEntry(entry: { entryDate?: string, createdAt: string }): boolean {
  return journalEntryDate(entry) !== currentMoscowDate(new Date(entry.createdAt))
}
