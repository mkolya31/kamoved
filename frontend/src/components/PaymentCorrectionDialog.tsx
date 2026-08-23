import { type FormEvent, useState } from 'react'
import { ApiError, correctPayment } from '../lib/api'
import { paymentMethodLabels } from '../lib/format'
import type { EntryType, JournalEntryDetails, PaymentDetails, PaymentMethod } from '../types'

interface PaymentCorrectionDialogProps {
  entryType: EntryType
  payment: PaymentDetails
  onClose: () => void
  onUpdated: (entry: JournalEntryDetails) => void
}

const paymentMethods = Object.keys(paymentMethodLabels) as PaymentMethod[]

export function PaymentCorrectionDialog({
  entryType,
  payment,
  onClose,
  onUpdated,
}: PaymentCorrectionDialogProps) {
  const [amount, setAmount] = useState(String(payment.amount))
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(payment.paymentMethod ?? 'CASH')
  const [comment, setComment] = useState(payment.comment ?? '')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    const parsedAmount = Number(amount.trim().replace(',', '.'))
    if (entryType === 'ORDER' && (!/^\d+(?:[.,]\d{1,2})?$/.test(amount.trim()) || parsedAmount <= 0)) {
      setError('Сумма платежа должна быть больше нуля, не более двух знаков после запятой')
      return
    }
    if (!reason.trim()) {
      setError('Укажите причину исправления')
      return
    }

    setSubmitting(true)
    try {
      onUpdated(await correctPayment(payment, {
        amount: entryType === 'ORDER' ? parsedAmount : undefined,
        paymentMethod,
        comment: comment.trim() || undefined,
        reason: reason.trim(),
      }))
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Не удалось исправить платёж')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section className="payment-dialog" role="dialog" aria-modal="true" aria-labelledby="correction-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="dialog-header payment-dialog-header">
          <div><p className="eyebrow">Платёж от {new Date(payment.receivedAt).toLocaleString('ru-RU')}</p><h2 id="correction-title">Исправить платёж</h2></div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Закрыть">×</button>
        </header>
        <form onSubmit={handleSubmit}>
          {entryType === 'ORDER' && (
            <label className="payment-amount-field payment-correction-first-field">Сумма, ₽<input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} /></label>
          )}
          <label className={`payment-amount-field${entryType === 'SALE' ? ' payment-correction-first-field' : ''}`}>Способ оплаты<select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}>{paymentMethods.map((method) => <option key={method} value={method}>{paymentMethodLabels[method]}</option>)}</select></label>
          <label className="payment-amount-field">Комментарий <span>необязательно</span><textarea value={comment} onChange={(event) => setComment(event.target.value)} maxLength={5000} rows={3} /></label>
          <label className="payment-amount-field">Причина исправления<textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={2000} rows={3} required autoFocus={entryType === 'SALE'} /></label>
          {error && <p className="form-error payment-form-error" role="alert">{error}</p>}
          <footer className="dialog-footer payment-dialog-footer"><button className="button button-quiet" type="button" onClick={onClose} disabled={submitting}>Отмена</button><button className="button button-primary" disabled={submitting}>{submitting ? 'Сохраняем…' : 'Сохранить исправление'}</button></footer>
        </form>
      </section>
    </div>
  )
}
