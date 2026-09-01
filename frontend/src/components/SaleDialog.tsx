import { type FormEvent, useMemo, useState } from 'react'
import { ApiError, createSale } from '../lib/api'
import { formatMoney, paymentMethodLabels, unitLabels } from '../lib/format'
import { selectDefaultQuantity } from '../lib/quantityInput'
import type { JournalEntry, PaymentMethod, SaleItemInput, UnitOfMeasure } from '../types'

interface SaleDialogProps {
  onClose: () => void
  onCreated: (sale: JournalEntry) => void
}

interface DraftItem {
  key: number
  name: string
  quantity: string
  unit: UnitOfMeasure
  unitPrice: string
}

let nextKey = 1

function emptyItem(): DraftItem {
  return {
    key: nextKey++,
    name: '',
    quantity: '1',
    unit: 'PIECE',
    unitPrice: '',
  }
}

function parseDecimal(value: string): number {
  return Number(value.replace(',', '.'))
}

function isDecimal(value: string, fractionDigits: number): boolean {
  return new RegExp(`^\\d+(?:[.,]\\d{1,${fractionDigits}})?$`).test(value.trim())
}

function lineTotal(item: DraftItem): number {
  function parts(value: string) {
    const [whole, fraction = ''] = value.trim().replace(',', '.').split('.')
    return {
      digits: BigInt(`${whole}${fraction}`),
      scale: fraction.length,
    }
  }

  if (!isDecimal(item.quantity, 3) || !isDecimal(item.unitPrice, 2)) return 0
  const quantity = parts(item.quantity)
  const price = parts(item.unitPrice)
  const divisor = 10n ** BigInt(quantity.scale + price.scale)
  return Number((quantity.digits * price.digits) / divisor)
}

export function SaleDialog({ onClose, onCreated }: SaleDialogProps) {
  const [items, setItems] = useState<DraftItem[]>([emptyItem()])
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH')
  const [paymentComment, setPaymentComment] = useState('')
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const total = useMemo(
    () => items.reduce((sum, item) => sum + lineTotal(item), 0),
    [items],
  )

  function updateItem(key: number, patch: Partial<DraftItem>) {
    setItems((current) => current.map((item) => (
      item.key === key ? { ...item, ...patch } : item
    )))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')

    const payload: SaleItemInput[] = items.map((item) => ({
      name: item.name.trim(),
      quantity: parseDecimal(item.quantity),
      unit: item.unit,
      unitPrice: parseDecimal(item.unitPrice),
    }))

    const hasInvalidDecimal = items.some((item) => (
      !isDecimal(item.quantity, 3) || !isDecimal(item.unitPrice, 2)
    ))

    if (hasInvalidDecimal || payload.some((item) => (
      !item.name || item.quantity <= 0 || item.unitPrice < 0
      || !Number.isFinite(item.quantity) || !Number.isFinite(item.unitPrice)
    ))) {
      setError('Проверьте название, количество (до 3 знаков) и цену (до 2 знаков)')
      return
    }

    setSubmitting(true)
    try {
      onCreated(await createSale(
        payload,
        paymentMethod,
        paymentComment.trim() || undefined,
        comment.trim() || undefined,
      ))
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Не удалось сохранить продажу')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="sale-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sale-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="dialog-header">
          <div>
            <p className="eyebrow">Быстрая запись</p>
            <h2 id="sale-dialog-title">Продажа из наличия</h2>
            <p>Продажа будет завершена, а платёж создан на полную сумму.</p>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Закрыть">
            ×
          </button>
        </header>

        <form onSubmit={handleSubmit}>
          <div className="sale-items">
            {items.map((item, index) => (
              <fieldset className="sale-item" key={item.key}>
                <legend>Позиция {index + 1}</legend>
                <label className="field-name">
                  Название товара
                  <input
                    value={item.name}
                    onChange={(event) => updateItem(item.key, { name: event.target.value })}
                    placeholder="Например, кварцевый песок"
                    maxLength={500}
                    required
                    autoFocus={index === 0}
                  />
                </label>
                <label>
                  Количество
                  <input
                    inputMode="decimal"
                    value={item.quantity}
                    onFocus={(event) => selectDefaultQuantity(event.currentTarget)}
                    onChange={(event) => updateItem(item.key, { quantity: event.target.value })}
                    required
                  />
                </label>
                <label>
                  Единица
                  <select
                    value={item.unit}
                    onChange={(event) => updateItem(item.key, {
                      unit: event.target.value as UnitOfMeasure,
                    })}
                  >
                    {Object.entries(unitLabels).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Цена, ₽
                  <input
                    inputMode="decimal"
                    value={item.unitPrice}
                    onChange={(event) => updateItem(item.key, { unitPrice: event.target.value })}
                    placeholder="0"
                    required
                  />
                </label>
                <div className="line-total">
                  <span>Сумма</span>
                  <strong>{formatMoney(lineTotal(item))}</strong>
                </div>
                {items.length > 1 && (
                  <button
                    className="remove-item"
                    type="button"
                    onClick={() => setItems((current) => current.filter(({ key }) => key !== item.key))}
                  >
                    Удалить позицию
                  </button>
                )}
              </fieldset>
            ))}
          </div>

          <button
            className="button button-quiet add-item"
            type="button"
            onClick={() => setItems((current) => [...current, emptyItem()])}
          >
            + Добавить позицию
          </button>

          <fieldset className="sale-payment-fields">
            <legend>Оплата</legend>
            <label>
              Способ оплаты
              <select
                value={paymentMethod}
                onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}
              >
                {Object.entries(paymentMethodLabels).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            <label>
              <span className="field-label">
                Комментарий к платежу <small>необязательно</small>
              </span>
              <input
                value={paymentComment}
                onChange={(event) => setPaymentComment(event.target.value)}
                maxLength={5000}
                placeholder="Например, наличные в кассе"
              />
            </label>
          </fieldset>

          <label className="sale-comment">
            <span className="field-label">
              Комментарий к продаже <small>необязательно</small>
            </span>
            <textarea
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={5000}
              rows={3}
              placeholder="Служебная пометка к продаже"
            />
          </label>

          {error && <p className="form-error" role="alert">{error}</p>}

          <footer className="dialog-footer">
            <div>
              <span>Итого</span>
              <strong>{formatMoney(total)}</strong>
            </div>
            <button className="button button-quiet" type="button" onClick={onClose}>
              Отмена
            </button>
            <button className="button button-primary" disabled={submitting}>
              {submitting ? 'Сохраняем…' : 'Сохранить продажу'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  )
}
