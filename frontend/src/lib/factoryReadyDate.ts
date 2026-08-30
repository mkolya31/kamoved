export function currentMoscowYear(now = new Date()): number {
  return Number(new Intl.DateTimeFormat('en', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
  }).format(now))
}

export function currentMoscowDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now)
  const value = (type: Intl.DateTimeFormatPartTypes) => (
    parts.find((part) => part.type === type)?.value ?? ''
  )
  return `${value('year')}-${value('month')}-${value('day')}`
}

export function emptyFactoryReadyDate(): string {
  return `__.__.${currentMoscowYear()}`
}

export function isEmptyFactoryReadyDate(value: string): boolean {
  return /^__\.__\.\d{4}$/.test(value)
}

export function maskFactoryReadyDate(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 8)
  if (digits.length <= 2) return digits
  if (digits.length <= 4) return `${digits.slice(0, 2)}.${digits.slice(2)}`
  return `${digits.slice(0, 2)}.${digits.slice(2, 4)}.${digits.slice(4)}`
}

export function displayFactoryReadyDate(value?: string | null): string {
  if (!value) return ''
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}

export function parseFactoryReadyDate(value: string): string | undefined {
  const match = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(value)
  if (!match) return undefined
  const [, day, month, year] = match
  const date = new Date(`${year}-${month}-${day}T00:00:00Z`)
  if (date.getUTCFullYear() !== Number(year)
    || date.getUTCMonth() + 1 !== Number(month)
    || date.getUTCDate() !== Number(day)) return undefined
  return `${year}-${month}-${day}`
}

export function shortFactoryReadyDate(value: string): string {
  const [, month, day] = value.split('-')
  return `${day}.${month}`
}
