import { defaultEntryDate } from '../lib/entryDate'
import { FactoryReadyDateInput } from './FactoryReadyDateInput'

interface EntryDateFieldProps {
  type: 'SALE' | 'ORDER'
  backdated: boolean
  date: string
  onToggle: (checked: boolean) => void
  onDateChange: (date: string) => void
  error?: string
}

export function EntryDateField({ type, backdated, date, onToggle, onDateChange, error }: EntryDateFieldProps) {
  const label = type === 'SALE' ? 'Дата продажи' : 'Дата заказа'
  return (
    <div className="entry-date-fields">
      <label className="entry-date-toggle">
        <input type="checkbox" checked={backdated} onChange={(event) => {
          onToggle(event.target.checked)
          onDateChange(event.target.checked ? defaultEntryDate() : '')
        }} />
        {type === 'SALE' ? 'Продажа совершена не сегодня' : 'Заказ оформлен не сегодня'}
      </label>
      {backdated && (
        <label className="entry-date-input">
          {label}
          <FactoryReadyDateInput value={date} onChange={onDateChange} ariaLabel={label}
            ariaInvalid={Boolean(error)} ariaRequired ariaDescribedBy={error ? 'entry-date-error' : undefined} autoFocus />
          {error && <span className="field-error" id="entry-date-error" role="alert">{error}</span>}
        </label>
      )}
    </div>
  )
}
