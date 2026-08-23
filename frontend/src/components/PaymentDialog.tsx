import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { addOrderPayment, ApiError } from '../lib/api'
import { formatMoney, paymentMethodLabels } from '../lib/format'
import type { JournalEntry, PaymentMethod } from '../types'

interface PaymentDialogProps {
  entry: JournalEntry
  onClose: () => void
  onUpdated: (entry: JournalEntry) => void
}

const paymentMethods = Object.keys(paymentMethodLabels) as PaymentMethod[]

function parseAmount(value: string): number | null {
  const normalized = value.trim().replace(',', '.')
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return null
  return Number(normalized)
}

export function PaymentDialog({ entry, onClose, onUpdated }: PaymentDialogProps) {
  const [amount, setAmount] = useState('')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH')
  const [comment, setComment] = useState('')
  const [fieldTouched, setFieldTouched] = useState(false)
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const parsedAmount = useMemo(() => parseAmount(amount), [amount])
  const amountError = !fieldTouched
    ? ''
    : parsedAmount === null || parsedAmount <= 0
      ? 'Сумма платежа должна быть больше нуля'
      : parsedAmount > entry.remainingAmount
        ? 'Сумма платежа не может превышать остаток заказа'
        : ''
  const newPaidAmount = entry.paidAmount + (parsedAmount ?? 0)
  const newRemainingAmount = Math.max(0, entry.totalAmount - newPaidAmount)

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !submitting) onClose()
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [onClose, submitting])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFieldTouched(true)
    setServerError('')
    if (parsedAmount === null || parsedAmount <= 0 || parsedAmount > entry.remainingAmount) return

    setSubmitting(true)
    try {
      onUpdated(await addOrderPayment(entry.id, {
        amount: parsedAmount,
        paymentMethod,
        comment: comment.trim() || undefined,
      }))
    } catch (cause) {
      setServerError(cause instanceof ApiError ? cause.message : 'Не удалось добавить платёж')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="payment-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="payment-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="dialog-header payment-dialog-header">
          <div>
            <p className="eyebrow">Оплата заказа З-{entry.id}</p>
            <h2 id="payment-dialog-title">Добавить платёж</h2>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Закрыть">×</button>
        </header>

        <form onSubmit={handleSubmit}>
          <dl className="payment-totals" aria-live="polite">
            <div><dt>Сумма заказа</dt><dd>{formatMoney(entry.totalAmount)}</dd></div>
            <div><dt>Внесено</dt><dd>{formatMoney(entry.paidAmount)}</dd></div>
            <div className="payment-remaining-total"><dt>Осталось</dt><dd>{formatMoney(entry.remainingAmount)}</dd></div>
          </dl>

          <label className="payment-amount-field">
            Внести сейчас, ₽
            <input
              inputMode="decimal"
              value={amount}
              onChange={(event) => { setAmount(event.target.value); setServerError('') }}
              onBlur={() => setFieldTouched(true)}
              placeholder="0"
              autoFocus
              aria-invalid={Boolean(amountError)}
              aria-describedby="payment-amount-error"
            />
            {amountError && <small id="payment-amount-error" className="field-error">{amountError}</small>}
          </label>

          <button
            className="button button-quiet payment-full-balance"
            type="button"
            onClick={() => { setAmount(String(entry.remainingAmount)); setFieldTouched(true); setServerError('') }}
          >
            Внести весь остаток — {formatMoney(entry.remainingAmount)}
          </button>

          <label className="payment-amount-field">
            Способ оплаты
            <select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}>
              {paymentMethods.map((method) => (
                <option key={method} value={method}>{paymentMethodLabels[method]}</option>
              ))}
            </select>
          </label>

          <label className="payment-amount-field">
            <span className="field-label">
              Комментарий <small>необязательно</small>
            </span>
            <textarea
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={5000}
              rows={3}
              placeholder="Например, перевод на карту сотрудника"
            />
          </label>

          {parsedAmount !== null && parsedAmount > 0 && !amountError && (
            <p className="payment-preview">
              После платежа внесено {formatMoney(newPaidAmount)}, осталось {formatMoney(newRemainingAmount)}.
            </p>
          )}
          {serverError && <p className="form-error payment-form-error" role="alert">{serverError}</p>}

          <footer className="dialog-footer payment-dialog-footer">
            <button className="button button-quiet" type="button" onClick={onClose} disabled={submitting}>Отмена</button>
            <button className="button button-primary" disabled={submitting}>
              {submitting ? 'Добавляем…' : 'Добавить платёж'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  )
}
