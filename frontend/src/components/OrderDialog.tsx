import { type FormEvent, useMemo, useState } from 'react'
import { ApiError, createOrder, updateOrder } from '../lib/api'
import {
  executionLabels,
  formatMoney,
  fulfillmentLabels,
  paymentLabels,
  unitLabels,
} from '../lib/format'
import type {
  ContactInput,
  ExecutionStatus,
  FulfillmentMethod,
  JournalEntry,
  JournalEntryDetails,
  JournalContact,
  JournalItem,
  OrderInput,
  PaymentStatus,
  SaleItemInput,
  UnitOfMeasure,
} from '../types'

interface OrderDialogBaseProps {
  onClose: () => void
}

interface CreateOrderDialogProps extends OrderDialogBaseProps {
  order?: undefined
  onCreated: (order: JournalEntry) => void
  onUpdated?: never
}

interface EditOrderDialogProps extends OrderDialogBaseProps {
  order: JournalEntryDetails
  onCreated?: never
  onUpdated: (order: JournalEntryDetails) => void
}

type OrderDialogProps = CreateOrderDialogProps | EditOrderDialogProps

interface DraftItem {
  key: number
  name: string
  quantity: string
  unit: UnitOfMeasure
  unitPrice: string
}

interface DraftContact {
  key: number
  name: string
  phone: string
  comment: string
}

let nextItemKey = 1
let nextContactKey = 1

function emptyItem(): DraftItem {
  return {
    key: nextItemKey++,
    name: '',
    quantity: '1',
    unit: 'PIECE',
    unitPrice: '',
  }
}

function emptyContact(): DraftContact {
  return {
    key: nextContactKey++,
    name: '',
    phone: '',
    comment: '',
  }
}

function itemDraft(item: JournalItem): DraftItem {
  return {
    key: nextItemKey++,
    name: item.name,
    quantity: String(item.quantity),
    unit: item.unit,
    unitPrice: String(item.unitPrice),
  }
}

function contactDraft(contact?: JournalContact | null): DraftContact {
  return {
    key: nextContactKey++,
    name: contact?.name ?? '',
    phone: contact?.phone ?? '',
    comment: contact?.comment ?? '',
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

function contactPayload(contact: DraftContact): ContactInput | undefined {
  const result = {
    name: contact.name.trim(),
    phone: contact.phone.trim(),
    comment: contact.comment.trim(),
  }
  return Object.values(result).some(Boolean) ? result : undefined
}

export function OrderDialog(props: OrderDialogProps) {
  const { onClose, order } = props
  const isEditing = order !== undefined
  const [items, setItems] = useState<DraftItem[]>(() => (
    order ? order.items.map(itemDraft) : [emptyItem()]
  ))
  const [client, setClient] = useState<DraftContact>(() => contactDraft(order?.client))
  const [additionalContacts, setAdditionalContacts] = useState<DraftContact[]>(() => (
    order?.additionalContacts.map(contactDraft) ?? []
  ))
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>(order?.paymentStatus ?? 'UNPAID')
  const [prepaymentAmount, setPrepaymentAmount] = useState(
    order?.paymentStatus === 'PREPAID' ? String(order.prepaymentAmount ?? '') : '',
  )
  const [executionStatus, setExecutionStatus] = useState<ExecutionStatus>(order?.executionStatus ?? 'NEW')
  const [fulfillmentMethod, setFulfillmentMethod] = useState<FulfillmentMethod | ''>(
    order?.fulfillmentMethod ?? '',
  )
  const [deliveryAddress, setDeliveryAddress] = useState(order?.deliveryAddress ?? '')
  const [comment, setComment] = useState(order?.comment ?? '')
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

  function updateAdditionalContact(key: number, patch: Partial<DraftContact>) {
    setAdditionalContacts((current) => current.map((contact) => (
      contact.key === key ? { ...contact, ...patch } : contact
    )))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')

    const payloadItems: SaleItemInput[] = items.map((item) => ({
      name: item.name.trim(),
      quantity: parseDecimal(item.quantity),
      unit: item.unit,
      unitPrice: parseDecimal(item.unitPrice),
    }))
    const invalidItem = items.some((item) => (
      !isDecimal(item.quantity, 3) || !isDecimal(item.unitPrice, 2)
    )) || payloadItems.some((item) => (
      !item.name || item.quantity <= 0 || item.unitPrice < 0
      || !Number.isFinite(item.quantity) || !Number.isFinite(item.unitPrice)
    ))

    if (invalidItem) {
      setError('Проверьте название, количество (до 3 знаков) и цену (до 2 знаков)')
      return
    }

    let parsedPrepayment: number | undefined
    if (paymentStatus === 'PREPAID') {
      if (!isDecimal(prepaymentAmount, 2)) {
        setError('Укажите сумму предоплаты')
        return
      }
      parsedPrepayment = parseDecimal(prepaymentAmount)
      if (parsedPrepayment <= 0 || parsedPrepayment >= total) {
        setError('Предоплата должна быть больше нуля и меньше суммы заказа')
        return
      }
    }

    if (fulfillmentMethod === 'DELIVERY' && !deliveryAddress.trim()) {
      setError('Для доставки укажите адрес')
      return
    }

    const payload: OrderInput = {
      items: payloadItems,
      client: contactPayload(client),
      additionalContacts: additionalContacts
        .map(contactPayload)
        .filter((contact): contact is ContactInput => contact !== undefined),
      paymentStatus,
      prepaymentAmount: parsedPrepayment,
      executionStatus,
      fulfillmentMethod: fulfillmentMethod || undefined,
      deliveryAddress: fulfillmentMethod === 'DELIVERY' ? deliveryAddress.trim() : undefined,
      comment: comment.trim() || undefined,
    }

    setSubmitting(true)
    try {
      if (props.order) {
        props.onUpdated(await updateOrder(props.order.id, {
          ...payload,
          version: props.order.version,
        }))
      } else {
        props.onCreated(await createOrder(payload))
      }
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Не удалось сохранить заказ')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="sale-dialog order-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="order-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="dialog-header">
          <div>
            <p className="eyebrow">{order ? `Заказ З-${order.id}` : 'Заказ с сопровождением'}</p>
            <h2 id="order-dialog-title">{isEditing ? 'Редактирование заказа' : 'Новый заказ'}</h2>
            <p>
              {isEditing
                ? 'Изменения сохранятся во всех данных заказа.'
                : 'Контакты и способ получения можно оставить пустыми.'}
            </p>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Закрыть">
            ×
          </button>
        </header>

        <form onSubmit={handleSubmit}>
          <section className="order-form-section">
            <header>
              <h3>Позиции заказа</h3>
              <span>Обязательно</span>
            </header>
            <div className="sale-items order-items">
              {items.map((item, index) => (
                <fieldset className="sale-item" key={item.key}>
                  <legend>Позиция {index + 1}</legend>
                  <label className="field-name">
                    Название товара
                    <input
                      value={item.name}
                      onChange={(event) => updateItem(item.key, { name: event.target.value })}
                      placeholder="Например, Готика Голд Кристалл 60 мм"
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
          </section>

          <section className="order-form-section">
            <header>
              <h3>Клиент</h3>
              <span>Необязательно</span>
            </header>
            <div className="contact-fields">
              <label>
                Имя
                <input
                  value={client.name}
                  onChange={(event) => setClient({ ...client, name: event.target.value })}
                  maxLength={255}
                  placeholder="Например, Владимир"
                />
              </label>
              <label>
                Телефон
                <input
                  value={client.phone}
                  onChange={(event) => setClient({ ...client, phone: event.target.value })}
                  maxLength={100}
                  inputMode="tel"
                  placeholder="+7 999 123-45-67"
                />
              </label>
              <label className="contact-comment">
                Комментарий к контакту
                <input
                  value={client.comment}
                  onChange={(event) => setClient({ ...client, comment: event.target.value })}
                  maxLength={2000}
                  placeholder="Основной покупатель"
                />
              </label>
            </div>

            {additionalContacts.map((contact, index) => (
              <fieldset className="additional-contact" key={contact.key}>
                <legend>Дополнительный контакт {index + 1}</legend>
                <div className="contact-fields">
                  <label>
                    Имя
                    <input
                      value={contact.name}
                      onChange={(event) => updateAdditionalContact(contact.key, { name: event.target.value })}
                      maxLength={255}
                      placeholder="Например, Нурик"
                    />
                  </label>
                  <label>
                    Телефон
                    <input
                      value={contact.phone}
                      onChange={(event) => updateAdditionalContact(contact.key, { phone: event.target.value })}
                      maxLength={100}
                      inputMode="tel"
                    />
                  </label>
                  <label className="contact-comment">
                    Комментарий
                    <input
                      value={contact.comment}
                      onChange={(event) => updateAdditionalContact(contact.key, { comment: event.target.value })}
                      maxLength={2000}
                      placeholder="Прораб, звонить по доставке"
                    />
                  </label>
                </div>
                <button
                  className="remove-item"
                  type="button"
                  onClick={() => setAdditionalContacts((current) => (
                    current.filter(({ key }) => key !== contact.key)
                  ))}
                >
                  Удалить контакт
                </button>
              </fieldset>
            ))}

            <button
              className="button button-quiet add-item"
              type="button"
              onClick={() => setAdditionalContacts((current) => [...current, emptyContact()])}
            >
              + Дополнительный контакт
            </button>
          </section>

          <section className="order-form-section">
            <header>
              <h3>Состояние заказа</h3>
            </header>
            <div className="order-settings-groups">
              <div className="order-settings-group">
                <label className="order-settings-primary-field">
                  Статус оплаты
                  <select
                    value={paymentStatus}
                    onChange={(event) => {
                      const next = event.target.value as PaymentStatus
                      setPaymentStatus(next)
                      if (next !== 'PREPAID') setPrepaymentAmount('')
                    }}
                  >
                    {Object.entries(paymentLabels).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
                {paymentStatus === 'PREPAID' && (
                  <div className="order-settings-dependent-fields">
                    <label>
                      Сумма предоплаты, ₽
                      <input
                        inputMode="decimal"
                        value={prepaymentAmount}
                        onChange={(event) => setPrepaymentAmount(event.target.value)}
                        placeholder="0"
                        required
                      />
                    </label>
                  </div>
                )}
              </div>

              <div className="order-settings-group">
                <label className="order-settings-primary-field">
                  Статус исполнения
                  <select
                    value={executionStatus}
                    onChange={(event) => setExecutionStatus(event.target.value as ExecutionStatus)}
                  >
                    {Object.entries(executionLabels).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
              </div>

              <div className="order-settings-group">
                <label className="order-settings-primary-field">
                  Способ получения
                  <select
                    value={fulfillmentMethod}
                    onChange={(event) => {
                      const next = event.target.value as FulfillmentMethod | ''
                      setFulfillmentMethod(next)
                      if (next !== 'DELIVERY') setDeliveryAddress('')
                    }}
                  >
                    <option value="">Не указан</option>
                    {Object.entries(fulfillmentLabels).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
                {fulfillmentMethod === 'DELIVERY' && (
                  <div className="order-settings-dependent-fields">
                    <label>
                      Адрес доставки
                      <input
                        value={deliveryAddress}
                        onChange={(event) => setDeliveryAddress(event.target.value)}
                        maxLength={2000}
                        placeholder="Населённый пункт, улица, участок или ориентир"
                        required
                      />
                    </label>
                  </div>
                )}
              </div>

              <label className="order-settings-comment">
                Комментарий к заказу
                <textarea
                  value={comment}
                  onChange={(event) => setComment(event.target.value)}
                  maxLength={5000}
                  rows={3}
                  placeholder="Любые важные договорённости или пометки"
                />
              </label>
            </div>
          </section>

          {error && <p className="form-error order-form-error" role="alert">{error}</p>}

          <footer className="dialog-footer">
            <div>
              <span>Итого</span>
              <strong>{formatMoney(total)}</strong>
            </div>
            <button className="button button-quiet" type="button" onClick={onClose}>
              Отмена
            </button>
            <button className="button button-primary" disabled={submitting}>
              {submitting ? 'Сохраняем…' : isEditing ? 'Сохранить изменения' : 'Создать заказ'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  )
}
