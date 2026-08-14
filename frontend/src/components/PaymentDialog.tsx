import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { ApiError, updateOrderPayment } from '../lib/api'
import { formatMoney, paymentLabels } from '../lib/format'
import { evaluatePaymentDraft } from '../lib/payment'
import type { JournalEntry, PaymentStatus } from '../types'

interface PaymentDialogProps {
  entry: JournalEntry
  onClose: () => void
  onUpdated: (entry: JournalEntry) => void
}

const paymentStatuses: PaymentStatus[] = ['UNPAID', 'PREPAID', 'PAID']

export function PaymentDialog({ entry, onClose, onUpdated }: PaymentDialogProps) {
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>(entry.paymentStatus)
  const [paidAmount, setPaidAmount] = useState(
    entry.paymentStatus === 'PREPAID' ? String(entry.prepaymentAmount ?? '') : '',
  )
  const [fieldTouched, setFieldTouched] = useState(false)
  const [serverError, setServerError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const evaluation = useMemo(
    () => evaluatePaymentDraft(paymentStatus, paidAmount, entry.totalAmount),
    [entry.totalAmount, paidAmount, paymentStatus],
  )

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !submitting) onClose()
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [onClose, submitting])

  function selectStatus(nextStatus: PaymentStatus) {
    setPaymentStatus(nextStatus)
    setFieldTouched(false)
    setServerError('')
    if (nextStatus === 'PREPAID' && paymentStatus !== 'PREPAID') {
      setPaidAmount(entry.paymentStatus === 'PREPAID' ? String(entry.prepaymentAmount ?? '') : '')
    }
  }

  function changePaidAmount(value: string) {
    setPaidAmount(value)
    setFieldTouched(true)
    setServerError('')

    const nextEvaluation = evaluatePaymentDraft('PREPAID', value, entry.totalAmount)
    if (!nextEvaluation.error && nextEvaluation.paymentStatus === 'PAID') {
      setPaymentStatus('PAID')
      setFieldTouched(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFieldTouched(true)
    setServerError('')
    if (evaluation.error) return

    setSubmitting(true)
    try {
      const updated = await updateOrderPayment(
        entry.id,
        evaluation.paymentStatus,
        evaluation.paymentStatus === 'PREPAID' ? evaluation.paidAmount : undefined,
        entry.version,
      )
      onUpdated(updated)
    } catch (cause) {
      setServerError(cause instanceof ApiError ? cause.message : 'Не удалось сохранить оплату')
    } finally {
      setSubmitting(false)
    }
  }

  const clearsExistingPrepayment = entry.paymentStatus === 'PREPAID'
    && paymentStatus === 'UNPAID'
    && (entry.prepaymentAmount ?? 0) > 0

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
            <h2 id="payment-dialog-title">Изменить оплату</h2>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Закрыть">
            ×
          </button>
        </header>

        <form onSubmit={handleSubmit}>
          <fieldset className="payment-options">
            <legend>Статус оплаты</legend>
            {paymentStatuses.map((status, index) => (
              <label key={status} className={paymentStatus === status ? 'selected' : ''}>
                <input
                  type="radio"
                  name="payment-status"
                  value={status}
                  checked={paymentStatus === status}
                  onChange={() => selectStatus(status)}
                  autoFocus={index === 0}
                />
                <span>{status === 'PAID' ? 'Оплачено полностью' : paymentLabels[status]}</span>
              </label>
            ))}
          </fieldset>

          {paymentStatus === 'PREPAID' && (
            <label className="payment-amount-field">
              Внесено всего, ₽
              <input
                inputMode="decimal"
                value={paidAmount}
                onChange={(event) => changePaidAmount(event.target.value)}
                placeholder="0"
                aria-invalid={fieldTouched && Boolean(evaluation.error)}
                aria-describedby="payment-amount-error"
              />
              {fieldTouched && evaluation.error && (
                <small id="payment-amount-error" className="field-error" role="alert">
                  {evaluation.error}
                </small>
              )}
            </label>
          )}

          {clearsExistingPrepayment && (
            <p className="payment-warning" role="status">
              После сохранения внесённая сумма {formatMoney(entry.prepaymentAmount ?? 0)} будет очищена.
            </p>
          )}

          <dl className="payment-totals" aria-live="polite">
            <div>
              <dt>Сумма заказа</dt>
              <dd>{formatMoney(entry.totalAmount)}</dd>
            </div>
            <div>
              <dt>Внесено</dt>
              <dd>{formatMoney(evaluation.paidAmount)}</dd>
            </div>
            <div className="payment-remaining-total">
              <dt>Осталось</dt>
              <dd>{formatMoney(evaluation.remainingAmount)}</dd>
            </div>
          </dl>

          {serverError && <p className="form-error payment-form-error" role="alert">{serverError}</p>}

          <footer className="dialog-footer payment-dialog-footer">
            <button className="button button-quiet" type="button" onClick={onClose} disabled={submitting}>
              Отмена
            </button>
            <button className="button button-primary" disabled={submitting}>
              {submitting ? 'Сохраняем…' : 'Сохранить'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  )
}
