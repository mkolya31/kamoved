import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ApiError,
  loadJournal,
  loadJournalEntry,
  logout,
  updateOrderExecutionStatus,
} from '../lib/api'
import {
  executionLabels,
  formatDate,
  formatMoney,
  formatQuantity,
  formatTime,
  fulfillmentLabels,
  paymentLabels,
} from '../lib/format'
import type { ExecutionStatus, JournalEntry, JournalEntryDetails, User } from '../types'
import { OrderDialog } from './OrderDialog'
import { PaymentDialog } from './PaymentDialog'
import { SaleDialog } from './SaleDialog'

interface JournalPageProps {
  user: User
  onLogout: () => void
}

const executionStatuses = Object.keys(executionLabels) as ExecutionStatus[]
const terminalExecutionStatuses: ExecutionStatus[] = ['COMPLETED', 'CANCELLED']

export function JournalPage({ user, onLogout }: JournalPageProps) {
  const [mode, setMode] = useState<'all' | 'active'>('all')
  const [entries, setEntries] = useState<JournalEntry[]>([])
  const [todayRevenue, setTodayRevenue] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saleOpen, setSaleOpen] = useState(false)
  const [orderOpen, setOrderOpen] = useState(false)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [details, setDetails] = useState<Map<number, JournalEntryDetails>>(new Map())
  const [updatingStatusId, setUpdatingStatusId] = useState<number | null>(null)
  const [paymentEntry, setPaymentEntry] = useState<JournalEntry | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const journal = await loadJournal(mode)
      setEntries(journal.items)
      setTodayRevenue(journal.todayRevenue)
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onLogout()
        return
      }
      setError(cause instanceof ApiError ? cause.message : 'Не удалось загрузить журнал')
    } finally {
      setLoading(false)
    }
  }, [mode, onLogout])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const groups = useMemo(() => {
    const grouped = new Map<string, JournalEntry[]>()
    entries.forEach((entry) => {
      const key = formatDate(entry.createdAt)
      grouped.set(key, [...(grouped.get(key) ?? []), entry])
    })
    return [...grouped.entries()]
  }, [entries])

  async function handleLogout() {
    try {
      await logout()
    } finally {
      onLogout()
    }
  }

  async function toggleExpanded(id: number) {
    const wasExpanded = expanded.has(id)
    setExpanded((current) => {
      const next = new Set(current)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })

    if (!wasExpanded && !details.has(id)) {
      try {
        const loaded = await loadJournalEntry(id)
        setDetails((current) => new Map(current).set(id, loaded))
      } catch (cause) {
        setExpanded((current) => {
          const next = new Set(current)
          next.delete(id)
          return next
        })
        setError(cause instanceof ApiError ? cause.message : 'Не удалось открыть запись')
      }
    }
  }

  async function handleExecutionStatusChange(entry: JournalEntry, executionStatus: ExecutionStatus) {
    if (executionStatus === entry.executionStatus) return

    setUpdatingStatusId(entry.id)
    setError('')
    try {
      const updated = await updateOrderExecutionStatus(entry.id, executionStatus, entry.version)
      setEntries((current) => (
        mode === 'active' && terminalExecutionStatuses.includes(updated.executionStatus)
          ? current.filter(({ id }) => id !== updated.id)
          : current.map((item) => item.id === updated.id ? updated : item)
      ))
      setDetails((current) => {
        const loaded = current.get(updated.id)
        if (!loaded) return current
        const next = new Map(current)
        next.set(updated.id, {
          ...loaded,
          executionStatus: updated.executionStatus,
          version: updated.version,
        })
        return next
      })
      if (mode === 'active' && terminalExecutionStatuses.includes(updated.executionStatus)) {
        setExpanded((current) => {
          const next = new Set(current)
          next.delete(updated.id)
          return next
        })
      }
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Не удалось изменить статус заказа')
      if (cause instanceof ApiError && cause.status === 409) {
        await refresh()
      }
    } finally {
      setUpdatingStatusId(null)
    }
  }

  function handlePaymentUpdated(updated: JournalEntry) {
    setEntries((current) => current.map((entry) => entry.id === updated.id ? updated : entry))
    setDetails((current) => {
      const loaded = current.get(updated.id)
      if (!loaded) return current
      const next = new Map(current)
      next.set(updated.id, {
        ...loaded,
        paymentStatus: updated.paymentStatus,
        prepaymentAmount: updated.prepaymentAmount,
        remainingAmount: updated.remainingAmount,
        version: updated.version,
      })
      return next
    })
    setPaymentEntry(null)
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-block">
          <img className="kamoved-logo" src="/brand/favicon-192.png" alt="kamoved logo"/>
          <div>
            <h1>Камовед</h1>
            <p>Журнал заказов</p>
          </div>
        </div>

        <div className="header-actions">
          <button className="button button-sale" onClick={() => setSaleOpen(true)}>
            + Продажа из наличия
          </button>
          <button
            className="button button-primary"
            onClick={() => setOrderOpen(true)}
          >
            + Новый заказ
          </button>
          <div className="user-menu">
            <span>{user.displayName}</span>
            <button type="button" onClick={() => void handleLogout()}>Выйти</button>
          </div>
        </div>
      </header>

      <main className="journal-main">
        <section className="journal-heading">
          <div>
            <p className="eyebrow">Рабочий журнал</p>
            <h2>{mode === 'all' ? 'Последние записи' : 'Активные заказы'}</h2>
          </div>
          <div className="journal-heading-actions">
            <div className="revenue-today" aria-live="polite">
              <span>Выручка сегодня</span>
              <strong>{todayRevenue === null ? '—' : formatMoney(todayRevenue)}</strong>
            </div>
            <div className="mode-switch" aria-label="Режим журнала">
              <button
                className={mode === 'all' ? 'active' : ''}
                onClick={() => setMode('all')}
              >
                Все записи
              </button>
              <button
                className={mode === 'active' ? 'active' : ''}
                onClick={() => setMode('active')}
              >
                Активные заказы
              </button>
            </div>
          </div>
        </section>

        {error && (
          <div className="notice notice-error" role="alert">
            <span>{error}</span>
            <button onClick={() => void refresh()}>Повторить</button>
          </div>
        )}

        {loading ? (
          <div className="loading-list" aria-label="Загружаем журнал">
            <div />
            <div />
            <div />
          </div>
        ) : groups.length === 0 ? (
          <section className="empty-state">
            <div aria-hidden="true">○</div>
            <h3>{mode === 'active' ? 'Активных заказов нет' : 'Журнал пока пуст'}</h3>
            <p>
              {mode === 'active'
                ? 'Незавершённые заказы появятся здесь.'
                : 'Добавьте первую продажу из наличия.'}
            </p>
            {mode === 'all' && (
              <button className="button button-primary" onClick={() => setSaleOpen(true)}>
                + Первая продажа
              </button>
            )}
          </section>
        ) : (
          <div className="journal-groups">
            {groups.map(([date, dateEntries]) => (
              <section className="journal-group" key={date}>
                <header className="date-divider">
                  <h3>{date}</h3>
                  <span>{dateEntries.length} {dateEntries.length === 1 ? 'запись' : 'записи'}</span>
                </header>
                <div className="entry-list">
                  {dateEntries.map((entry) => {
                    const mainItem = entry.mainItem
                    const isOrder = entry.type === 'ORDER'
                    const isExpanded = expanded.has(entry.id)
                    const entryDetails = details.get(entry.id)
                    return (
                      <article className="entry-card" key={entry.id}>
                        <div className="entry-summary">
                          <button
                            className="entry-summary-toggle"
                            type="button"
                            onClick={() => void toggleExpanded(entry.id)}
                            aria-expanded={isExpanded}
                            aria-label={`${isExpanded ? 'Свернуть' : 'Открыть'} запись ${isOrder ? 'З' : 'П'}-${entry.id}`}
                          />
                          <span className="entry-number">
                            <strong>{isOrder ? 'З' : 'П'}-{entry.id}</strong>
                            <small>{formatTime(entry.createdAt)}</small>
                          </span>
                          <span className={`entry-kind ${isOrder ? 'entry-kind-order' : ''}`}>
                            <i aria-hidden="true">●</i>
                            {isOrder
                              ? (entry.clientName || 'Заказ с сопровождением')
                              : 'Продажа из наличия'}
                          </span>
                          <span className="entry-product">
                            <strong>{mainItem?.name ?? 'Без позиции'}</strong>
                            {mainItem && <small>{formatQuantity(mainItem.quantity, mainItem.unit)}</small>}
                            {isOrder && entry.fulfillmentMethod && (
                              <small>
                                {fulfillmentLabels[entry.fulfillmentMethod]}
                                {entry.deliveryAddress ? ` · ${entry.deliveryAddress}` : ''}
                              </small>
                            )}
                          </span>
                          <strong className="entry-total">{formatMoney(entry.totalAmount)}</strong>
                          <span className="status-stack">
                            <span className="payment-status-summary">
                              {isOrder ? (
                                <button
                                  className={`status payment-status-button status-payment-${entry.paymentStatus.toLowerCase()}`}
                                  type="button"
                                  onClick={() => setPaymentEntry(entry)}
                                  aria-label={`Изменить оплату заказа З-${entry.id}: ${paymentLabels[entry.paymentStatus]}`}
                                >
                                  <span>{paymentLabels[entry.paymentStatus]}</span>
                                  {entry.executionStatus !== 'COMPLETED'
                                    && entry.paymentStatus !== 'PAID' && (
                                    <svg
                                      className="payment-status-edit-icon"
                                      viewBox="0 0 16 16"
                                      aria-hidden="true"
                                    >
                                      <path d="M10.7 2.3a1.4 1.4 0 0 1 2 0l1 1a1.4 1.4 0 0 1 0 2L5.2 13.8l-3 .7.7-3L10.7 2.3Z" />
                                      <path d="m9.7 3.3 3 3" />
                                    </svg>
                                  )}
                                </button>
                              ) : (
                                <span className={`status status-payment-${entry.paymentStatus.toLowerCase()}`}>
                                  {paymentLabels[entry.paymentStatus]}
                                </span>
                              )}
                              {isOrder && entry.paymentStatus === 'PREPAID' && (
                                <small>Осталось {formatMoney(entry.remainingAmount)}</small>
                              )}
                            </span>
                            {isOrder ? (
                              <select
                                className={`status quick-status-select status-execution-${entry.executionStatus.toLowerCase()}`}
                                aria-label={`Статус исполнения заказа З-${entry.id}`}
                                value={entry.executionStatus}
                                disabled={updatingStatusId === entry.id}
                                onChange={(event) => void handleExecutionStatusChange(
                                  entry,
                                  event.target.value as ExecutionStatus,
                                )}
                              >
                                {executionStatuses.map((status) => (
                                  <option key={status} value={status}>{executionLabels[status]}</option>
                                ))}
                              </select>
                            ) : (
                              <span className={`status status-execution-${entry.executionStatus.toLowerCase()}`}>
                                {executionLabels[entry.executionStatus]}
                              </span>
                            )}
                          </span>
                          <span className="chevron" aria-hidden="true">{isExpanded ? '⌃' : '⌄'}</span>
                        </div>

                        {isExpanded && !entryDetails && (
                          <div className="entry-details-loading">
                            {isOrder ? 'Открываем заказ…' : 'Открываем состав продажи…'}
                          </div>
                        )}

                        {isExpanded && entryDetails && (
                          <div className="entry-details">
                            <div>
                              <h4>{isOrder ? 'Состав заказа' : 'Состав продажи'}</h4>
                              <ul>
                                {entryDetails.items.map((item) => (
                                  <li key={item.id}>
                                    <span>
                                      <strong>{item.name}</strong>
                                      <small>
                                        {formatQuantity(item.quantity, item.unit)} × {formatMoney(item.unitPrice)}
                                      </small>
                                    </span>
                                    <strong>{formatMoney(item.lineTotal)}</strong>
                                  </li>
                                ))}
                              </ul>
                            </div>
                            {isOrder ? (
                              <div className="order-details-meta">
                                {(entryDetails.client || entryDetails.additionalContacts.length > 0) && (
                                  <section>
                                    <h4>Контакты</h4>
                                    {entryDetails.client && (
                                      <p>
                                        <strong>{entryDetails.client.name || 'Клиент'}</strong>
                                        {entryDetails.client.phone && <span>{entryDetails.client.phone}</span>}
                                        {entryDetails.client.comment && <small>{entryDetails.client.comment}</small>}
                                      </p>
                                    )}
                                    {entryDetails.additionalContacts.map((contact) => (
                                      <p key={contact.id}>
                                        <strong>{contact.name || 'Дополнительный контакт'}</strong>
                                        {contact.phone && <span>{contact.phone}</span>}
                                        {contact.comment && <small>{contact.comment}</small>}
                                      </p>
                                    ))}
                                  </section>
                                )}
                                <section>
                                  <h4>Детали</h4>
                                  <dl className="payment-details">
                                    <div>
                                      <dt>Сумма заказа</dt>
                                      <dd>{formatMoney(entryDetails.totalAmount)}</dd>
                                    </div>
                                    <div>
                                      <dt>Внесено</dt>
                                      <dd>{formatMoney(
                                        entryDetails.paymentStatus === 'PAID'
                                          ? entryDetails.totalAmount
                                          : (entryDetails.prepaymentAmount ?? 0),
                                      )}</dd>
                                    </div>
                                    <div>
                                      <dt>Осталось</dt>
                                      <dd>{formatMoney(entryDetails.remainingAmount)}</dd>
                                    </div>
                                  </dl>
                                  {entryDetails.fulfillmentMethod && (
                                    <p>
                                      <strong>{fulfillmentLabels[entryDetails.fulfillmentMethod]}</strong>
                                      {entryDetails.deliveryAddress && <span>{entryDetails.deliveryAddress}</span>}
                                    </p>
                                  )}
                                  {entryDetails.comment && <p className="order-comment">{entryDetails.comment}</p>}
                                  <small>Создал: {entryDetails.createdByDisplayName}</small>
                                </section>
                              </div>
                            ) : (
                              <p>Продажа автоматически отмечена как оплаченная и завершённая.</p>
                            )}
                          </div>
                        )}
                      </article>
                    )
                  })}
                </div>
              </section>
            ))}
          </div>
        )}
      </main>

      {saleOpen && (
        <SaleDialog
          onClose={() => setSaleOpen(false)}
          onCreated={(sale) => {
            setSaleOpen(false)
            setMode('all')
            setEntries((current) => [sale, ...current.filter(({ id }) => id !== sale.id)])
            setTodayRevenue((current) => (current ?? 0) + sale.totalAmount)
            setExpanded(new Set())
          }}
        />
      )}

      {orderOpen && (
        <OrderDialog
          onClose={() => setOrderOpen(false)}
          onCreated={(order) => {
            setOrderOpen(false)
            setMode('all')
            setEntries((current) => [order, ...current.filter(({ id }) => id !== order.id)])
            setExpanded(new Set())
          }}
        />
      )}

      {paymentEntry && (
        <PaymentDialog
          entry={paymentEntry}
          onClose={() => setPaymentEntry(null)}
          onUpdated={handlePaymentUpdated}
        />
      )}
    </div>
  )
}
