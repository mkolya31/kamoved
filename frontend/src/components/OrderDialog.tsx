import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from 'react'
import { ApiError, createOrder, updateOrder } from '../lib/api'
import {
  executionLabels,
  formatMoney,
  fulfillmentLabels,
  paymentMethodLabels,
  unitLabels,
} from '../lib/format'
import { serializeOrderFormState, type OrderFormState } from '../lib/orderFormState'
import { formatPhone } from '../lib/phone'
import { selectDefaultQuantity } from '../lib/quantityInput'
import {
  currentMoscowDate,
  displayFactoryReadyDate,
  emptyFactoryReadyDate,
  isEmptyFactoryReadyDate,
  parseFactoryReadyDate,
} from '../lib/factoryReadyDate'
import { FactoryReadyDateInput } from './FactoryReadyDateInput'
import type {
  ContactInput,
  ExecutionStatus,
  FulfillmentMethod,
  JournalEntry,
  JournalEntryDetails,
  JournalContact,
  JournalItem,
  OrderInput,
  PaymentMethod,
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

function isDeliveryMethod(method: FulfillmentMethod | ''): boolean {
  return method === 'DELIVERY_FACTORY' || method === 'DELIVERY_MARKET'
}

interface DraftContact {
  key: number
  name: string
  phone: string
  comment: string
}

type ValidationErrors = Record<string, string>

function itemFieldKey(itemKey: number, field: 'name' | 'quantity' | 'unitPrice'): string {
  return `item-${itemKey}-${field}`
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
  const [initialPaymentOpen, setInitialPaymentOpen] = useState(false)
  const [paymentAmount, setPaymentAmount] = useState('')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH')
  const [paymentComment, setPaymentComment] = useState('')
  const [executionStatus, setExecutionStatus] = useState<ExecutionStatus>(order?.executionStatus ?? 'NEW')
  const [fulfillmentMethod, setFulfillmentMethod] = useState<FulfillmentMethod | ''>(
    order?.fulfillmentMethod ?? '',
  )
  const [deliveryAddress, setDeliveryAddress] = useState(order?.deliveryAddress ?? '')
  const [comment, setComment] = useState(order?.comment ?? '')
  const [factoryReadyDate, setFactoryReadyDate] = useState(
    displayFactoryReadyDate(order?.factoryReadyDate ?? null) || emptyFactoryReadyDate(),
  )
  const [error, setError] = useState('')
  const [validationVisible, setValidationVisible] = useState(false)
  const [validationScrollRequest, setValidationScrollRequest] = useState({
    field: '',
    sequence: 0,
  })
  const [footerElevated, setFooterElevated] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [confirmingClose, setConfirmingClose] = useState(false)
  const dialogRef = useRef<HTMLElement | null>(null)
  const scrollAreaRef = useRef<HTMLDivElement | null>(null)
  const validationFieldRefs = useRef<Record<string, HTMLElement | null>>({})
  const continueButtonRef = useRef<HTMLButtonElement>(null)
  const discardButtonRef = useRef<HTMLButtonElement>(null)
  const initialSnapshotRef = useRef<string | null>(null)
  const savedScrollTopRef = useRef(0)
  const resumedFromConfirmRef = useRef(false)

  const total = useMemo(
    () => items.reduce((sum, item) => sum + lineTotal(item), 0),
    [items],
  )
  const hasFullInitialPayment = isDecimal(paymentAmount, 2)
    && parseDecimal(paymentAmount) === total

  function buildValidationErrors(): ValidationErrors {
    const errors: ValidationErrors = {}

    items.forEach((item) => {
      if (!item.name.trim()) {
        errors[itemFieldKey(item.key, 'name')] = 'Укажите название товара'
      }

      const quantityKey = itemFieldKey(item.key, 'quantity')
      if (!isDecimal(item.quantity, 3)) {
        errors[quantityKey] = 'Укажите количество — до 3 знаков после запятой'
      } else if (parseDecimal(item.quantity) <= 0) {
        errors[quantityKey] = 'Количество должно быть больше нуля'
      }

      const priceKey = itemFieldKey(item.key, 'unitPrice')
      if (!isDecimal(item.unitPrice, 2)) {
        errors[priceKey] = 'Укажите цену — до 2 знаков после запятой'
      }
    })

    if (!client.phone.trim()) {
      errors['client-phone'] = 'Укажите телефон'
    }

    if (!isEditing && initialPaymentOpen) {
      if (!isDecimal(paymentAmount, 2)) {
        errors['payment-amount'] = 'Укажите сумму платежа'
      } else {
        const parsedPaymentAmount = parseDecimal(paymentAmount)
        if (parsedPaymentAmount <= 0 || parsedPaymentAmount > total) {
          errors['payment-amount'] = 'Платёж должен быть больше нуля и не превышать сумму заказа'
        }
      }
    }

    const parsedFactoryReadyDate = !isEmptyFactoryReadyDate(factoryReadyDate)
      ? parseFactoryReadyDate(factoryReadyDate)
      : undefined
    if (!isEmptyFactoryReadyDate(factoryReadyDate) && !parsedFactoryReadyDate) {
      errors['factory-ready-date'] = 'Укажите существующую дату в формате ДД.ММ.ГГГГ'
    } else if (parsedFactoryReadyDate && parsedFactoryReadyDate < currentMoscowDate()) {
      errors['factory-ready-date'] = 'Дата готовности на заводе не может быть в прошлом'
    }

    if (isDeliveryMethod(fulfillmentMethod) && !deliveryAddress.trim()) {
      errors['delivery-address'] = 'Для доставки укажите адрес'
    }

    return errors
  }

  const validationErrors = validationVisible ? buildValidationErrors() : {}

  function validationError(field: string) {
    const message = validationErrors[field]
    if (!message) return null
    return (
      <span className="field-error" id={`order-error-${field}`} role="alert">
        {message}
      </span>
    )
  }

  function registerValidationField(field: string, node: HTMLElement | null) {
    validationFieldRefs.current[field] = node
  }

  function updateFooterElevation() {
    const node = scrollAreaRef.current
    if (!node) return
    setFooterElevated(node.scrollHeight - node.scrollTop - node.clientHeight > 1)
  }

  const formState: OrderFormState = {
    items: items.map(({ name, quantity, unit, unitPrice }) => ({
      name,
      quantity,
      unit,
      unitPrice,
    })),
    client: { name: client.name, phone: client.phone, comment: client.comment },
    additionalContacts: additionalContacts.map(({ name, phone, comment }) => ({
      name,
      phone,
      comment,
    })),
    initialPaymentOpen,
    paymentAmount,
    paymentMethod,
    paymentComment,
    executionStatus,
    fulfillmentMethod,
    deliveryAddress,
    comment,
    factoryReadyDate,
  }
  const snapshot = serializeOrderFormState(formState)
  if (initialSnapshotRef.current === null) {
    initialSnapshotRef.current = snapshot
  }
  const isDirty = snapshot !== initialSnapshotRef.current

  useEffect(() => {
    if (!isDirty) return
    function handleBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isDirty])

  useEffect(() => {
    window.addEventListener('resize', updateFooterElevation)
    return () => window.removeEventListener('resize', updateFooterElevation)
  }, [])

  useLayoutEffect(() => {
    updateFooterElevation()
  })

  useLayoutEffect(() => {
    if (!validationScrollRequest.field) return
    validationFieldRefs.current[validationScrollRequest.field]?.scrollIntoView({
      block: 'center',
    })
  }, [validationScrollRequest])

  useLayoutEffect(() => {
    if (confirmingClose) {
      // Откладываем фокусировку до следующего кадра: браузер применяет
      // дефолтное действие mousedown (перенос фокуса) после обработчика,
      // и синхронный focus() был бы перезаписан.
      const frame = requestAnimationFrame(() => continueButtonRef.current?.focus())
      return () => cancelAnimationFrame(frame)
    }
    if (resumedFromConfirmRef.current) {
      resumedFromConfirmRef.current = false
      const node = scrollAreaRef.current
      if (node) {
        node.scrollTop = savedScrollTopRef.current
        dialogRef.current?.focus({ preventScroll: true })
      }
    }
  }, [confirmingClose])

  function requestClose() {
    if (submitting || confirmingClose) return
    if (!isDirty) {
      onClose()
      return
    }
    savedScrollTopRef.current = scrollAreaRef.current?.scrollTop ?? 0
    resumedFromConfirmRef.current = true
    setConfirmingClose(true)
  }

  function resumeEditing() {
    setConfirmingClose(false)
  }

  function handleConfirmKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      resumeEditing()
      return
    }
    if (event.key === 'Tab') {
      event.preventDefault()
      const next = document.activeElement === continueButtonRef.current
        ? discardButtonRef.current
        : continueButtonRef.current
      next?.focus()
    }
  }

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

    const nextValidationErrors = buildValidationErrors()
    setValidationVisible(true)
    const firstInvalidField = Object.keys(nextValidationErrors)[0]
    if (firstInvalidField) {
      setValidationScrollRequest((current) => ({
        field: firstInvalidField,
        sequence: current.sequence + 1,
      }))
      return
    }

    const payloadItems: SaleItemInput[] = items.map((item) => ({
      name: item.name.trim(),
      quantity: parseDecimal(item.quantity),
      unit: item.unit,
      unitPrice: parseDecimal(item.unitPrice),
    }))

    let initialPayment: OrderInput['initialPayment']
    if (!isEditing && initialPaymentOpen) {
      const parsedPaymentAmount = parseDecimal(paymentAmount)
      initialPayment = {
        amount: parsedPaymentAmount,
        paymentMethod,
        comment: paymentComment.trim() || undefined,
      }
    }

    const parsedFactoryReadyDate = !isEmptyFactoryReadyDate(factoryReadyDate)
      ? parseFactoryReadyDate(factoryReadyDate)
      : undefined

    const payload: OrderInput = {
      items: payloadItems,
      client: contactPayload(client),
      additionalContacts: additionalContacts
        .map(contactPayload)
        .filter((contact): contact is ContactInput => contact !== undefined),
      initialPayment,
      executionStatus,
      fulfillmentMethod: fulfillmentMethod || undefined,
      deliveryAddress: isDeliveryMethod(fulfillmentMethod) ? deliveryAddress.trim() : undefined,
      comment: comment.trim() || undefined,
      factoryReadyDate: parsedFactoryReadyDate,
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

  if (confirmingClose) {
    return (
      <div className="dialog-backdrop" role="presentation">
        <section
          className="confirm-close-dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="confirm-close-title"
          aria-describedby="confirm-close-text"
          tabIndex={-1}
          onKeyDown={handleConfirmKeyDown}
        >
          <p className="eyebrow">Несохранённый заказ</p>
          <h2 id="confirm-close-title">Заказ не сохранён</h2>
          <p className="confirm-close-text" id="confirm-close-text">
            Если закрыть окно сейчас, все введённые данные будут потеряны.
          </p>
          <div className="confirm-close-actions">
            <button
              ref={continueButtonRef}
              className="button button-primary"
              type="button"
              onClick={resumeEditing}
            >
              Продолжить редактирование
            </button>
            <button
              ref={discardButtonRef}
              className="button button-quiet"
              type="button"
              onClick={onClose}
            >
              Закрыть без сохранения
            </button>
          </div>
        </section>
      </div>
    )
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={requestClose}>
      <section
        ref={dialogRef}
        className="sale-dialog order-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="order-dialog-title"
        tabIndex={-1}
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
          <button className="icon-button" type="button" onClick={requestClose} aria-label="Закрыть">
            ×
          </button>
        </header>

        <form onSubmit={handleSubmit} noValidate>
          <div
            ref={scrollAreaRef}
            className="order-dialog-scroll"
            onScroll={updateFooterElevation}
          >
          <section className="order-form-section">
            <header>
              <h3>Позиции заказа</h3>
              <span>Обязательно</span>
            </header>
            <div className="sale-items order-items">
              {items.map((item, index) => (
                <fieldset className="sale-item" key={item.key}>
                  <legend>Позиция {index + 1}</legend>
                  <label
                    ref={(node) => registerValidationField(itemFieldKey(item.key, 'name'), node)}
                    className="field-name"
                  >
                    Название товара
                    <input
                      value={item.name}
                      onChange={(event) => updateItem(item.key, { name: event.target.value })}
                      placeholder="Например, Готика Голд Кристалл 60 мм"
                      maxLength={500}
                      required
                      autoFocus={index === 0 && !resumedFromConfirmRef.current}
                      aria-invalid={Boolean(validationErrors[itemFieldKey(item.key, 'name')])}
                      aria-describedby={validationErrors[itemFieldKey(item.key, 'name')]
                        ? `order-error-${itemFieldKey(item.key, 'name')}`
                        : undefined}
                    />
                    {validationError(itemFieldKey(item.key, 'name'))}
                  </label>
                  <label ref={(node) => registerValidationField(itemFieldKey(item.key, 'quantity'), node)}>
                    Количество
                    <input
                      inputMode="decimal"
                      value={item.quantity}
                      onFocus={(event) => selectDefaultQuantity(event.currentTarget, !isEditing)}
                      onChange={(event) => updateItem(item.key, { quantity: event.target.value })}
                      required
                      aria-invalid={Boolean(validationErrors[itemFieldKey(item.key, 'quantity')])}
                      aria-describedby={validationErrors[itemFieldKey(item.key, 'quantity')]
                        ? `order-error-${itemFieldKey(item.key, 'quantity')}`
                        : undefined}
                    />
                    {validationError(itemFieldKey(item.key, 'quantity'))}
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
                  <label ref={(node) => registerValidationField(itemFieldKey(item.key, 'unitPrice'), node)}>
                    Цена, ₽
                    <input
                      inputMode="decimal"
                      value={item.unitPrice}
                      onChange={(event) => updateItem(item.key, { unitPrice: event.target.value })}
                      placeholder="0"
                      required
                      aria-invalid={Boolean(validationErrors[itemFieldKey(item.key, 'unitPrice')])}
                      aria-describedby={validationErrors[itemFieldKey(item.key, 'unitPrice')]
                        ? `order-error-${itemFieldKey(item.key, 'unitPrice')}`
                        : undefined}
                    />
                    {validationError(itemFieldKey(item.key, 'unitPrice'))}
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
              <span>Обязательно</span>
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
              <label ref={(node) => registerValidationField('client-phone', node)}>
                Телефон
                <input
                  value={client.phone}
                  onChange={(event) => setClient({ ...client, phone: formatPhone(event.target.value) })}
                  maxLength={100}
                  inputMode="tel"
                  placeholder="+7 (999) 123-45-67"
                  required
                  aria-invalid={Boolean(validationErrors['client-phone'])}
                  aria-describedby={validationErrors['client-phone']
                    ? 'order-error-client-phone'
                    : undefined}
                />
                {validationError('client-phone')}
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
                      onChange={(event) => updateAdditionalContact(contact.key, { phone: formatPhone(event.target.value) })}
                      maxLength={100}
                      inputMode="tel"
                      placeholder="+7 (999) 123-45-67"
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

          {!isEditing && (
            <section className="order-form-section order-payment-section">
              <header>
                <h3>Оплата</h3>
                <span>необязательно</span>
              </header>
              {!initialPaymentOpen && (
                <button
                  className="button button-quiet"
                  type="button"
                  onClick={() => {
                    setInitialPaymentOpen(true)
                    setError('')
                  }}
                >
                  + Добавить платёж
                </button>
              )}
              {initialPaymentOpen && (
                <fieldset className="order-payment-fields">
                  <button
                    className="remove-item remove-payment"
                    type="button"
                    onClick={() => {
                      setInitialPaymentOpen(false)
                      setError('')
                    }}
                  >
                    Удалить платёж
                  </button>
                  <label ref={(node) => registerValidationField('payment-amount', node)}>
                    Сумма платежа, ₽
                    <input
                      inputMode="decimal"
                      value={paymentAmount}
                      onChange={(event) => setPaymentAmount(event.target.value)}
                      placeholder="0"
                      required
                      aria-invalid={Boolean(validationErrors['payment-amount'])}
                      aria-describedby={validationErrors['payment-amount']
                        ? 'order-error-payment-amount'
                        : undefined}
                    />
                    {validationError('payment-amount')}
                  </label>
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
                      placeholder="Например, аванс наличными"
                    />
                  </label>
                  {!hasFullInitialPayment && (
                    <button
                      className="button button-quiet order-payment-full-amount"
                      type="button"
                      disabled={total <= 0}
                      onClick={() => {
                        setPaymentAmount(String(total))
                        setError('')
                      }}
                    >
                      Внести всю сумму — {formatMoney(total)}
                    </button>
                  )}
                </fieldset>
              )}
            </section>
          )}

          <section className="order-form-section">
            <header>
              <h3>Состояние заказа</h3>
            </header>
            <div className="order-settings-groups">

              <div className="order-settings-group">
                <label
                  ref={(node) => registerValidationField('factory-ready-date', node)}
                  className="order-settings-primary-field"
                >
                  <span className="field-label">
                    Дата готовности на заводе <small>необязательно</small>
                  </span>
                  <FactoryReadyDateInput
                    value={factoryReadyDate}
                    onChange={setFactoryReadyDate}
                    ariaLabel="Дата готовности на заводе в формате ДД.ММ.ГГГГ"
                    ariaInvalid={Boolean(validationErrors['factory-ready-date'])}
                    ariaDescribedBy={validationErrors['factory-ready-date']
                      ? 'order-error-factory-ready-date'
                      : undefined}
                  />
                  {validationError('factory-ready-date')}
                </label>
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
                      if (!isDeliveryMethod(next)) setDeliveryAddress('')
                    }}
                  >
                    <option value="">Не указан</option>
                    {Object.entries(fulfillmentLabels).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
                {isDeliveryMethod(fulfillmentMethod) && (
                  <div className="order-settings-dependent-fields">
                    <label ref={(node) => registerValidationField('delivery-address', node)}>
                      Адрес доставки
                      <input
                        value={deliveryAddress}
                        onChange={(event) => setDeliveryAddress(event.target.value)}
                        maxLength={2000}
                        placeholder="Населённый пункт, улица, участок или ориентир"
                        required
                        aria-invalid={Boolean(validationErrors['delivery-address'])}
                        aria-describedby={validationErrors['delivery-address']
                          ? 'order-error-delivery-address'
                          : undefined}
                      />
                      {validationError('delivery-address')}
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
          </div>

          <footer className={`dialog-footer${footerElevated ? ' dialog-footer-elevated' : ''}`}>
            <div>
              <span>Итого</span>
              <strong>{formatMoney(total)}</strong>
            </div>
            <button className="button button-quiet" type="button" onClick={requestClose}>
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
