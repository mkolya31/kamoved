import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import {
  ApiError,
  loadJournal,
  loadJournalEntry,
  logout,
  searchJournal,
  updateOrderExecutionStatus,
} from '../lib/api'
import {
  executionLabels,
  formatMoney,
  formatQuantity,
  formatTime,
  fulfillmentLabels,
  paymentLabels,
  paymentMethodLabels,
} from '../lib/format'
import { formatDate } from '../lib/format'
import {
  appendUniqueEntries,
  groupJournalEntries,
  initialJournalPaginationState,
  isLatestJournalRequest,
  journalPaginationReducer,
  type JournalMode,
} from '../lib/journalPagination'
import { formatSearchMatches, isJournalSearchActive } from '../lib/journalSearch'
import { summaryFromDetails } from '../lib/order'
import type { ExecutionStatus, JournalEntry, JournalEntryDetails, PaymentDetails, User } from '../types'
import { OrderDialog } from './OrderDialog'
import { PaymentDialog } from './PaymentDialog'
import { PaymentCorrectionDialog } from './PaymentCorrectionDialog'
import { SaleDialog } from './SaleDialog'

interface JournalPageProps {
  user: User
  onLogout: () => void
}

const executionStatuses = Object.keys(executionLabels) as ExecutionStatus[]
const terminalExecutionStatuses: ExecutionStatus[] = ['COMPLETED', 'CANCELLED']

function comparePaymentsNewestFirst(first: PaymentDetails, second: PaymentDetails) {
  return second.receivedAt.localeCompare(first.receivedAt)
    || second.createdAt.localeCompare(first.createdAt)
    || second.id - first.id
}

export function JournalPage({ user, onLogout }: JournalPageProps) {
  const [pagination, dispatchPagination] = useReducer(
    journalPaginationReducer,
    undefined,
    () => initialJournalPaginationState(),
  )
  const {mode, page, hasNext, loadingMore, loadMoreError, announcement} = pagination
  const [entries, setEntries] = useState<JournalEntry[]>([])
  const [todayRevenue, setTodayRevenue] = useState<number | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [totalItems, setTotalItems] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saleOpen, setSaleOpen] = useState(false)
  const [orderOpen, setOrderOpen] = useState(false)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [details, setDetails] = useState<Map<number, JournalEntryDetails>>(new Map())
  const [updatingStatusId, setUpdatingStatusId] = useState<number | null>(null)
  const [paymentEntry, setPaymentEntry] = useState<JournalEntry | null>(null)
  const [correctionPayment, setCorrectionPayment] = useState<{
    entryType: JournalEntry['type']
    payment: PaymentDetails
  } | null>(null)
  const [editingOrder, setEditingOrder] = useState<JournalEntryDetails | null>(null)
  const requestGenerationRef = useRef(0)
  const loadingMoreRef = useRef(false)
  const searchActive = isJournalSearchActive(searchQuery)

  useEffect(() => {
    if (!isJournalSearchActive(searchInput)) {
      setSearchQuery('')
      return
    }
    const timeout = window.setTimeout(() => setSearchQuery(searchInput.trim()), 300)
    return () => window.clearTimeout(timeout)
  }, [searchInput])

  const refresh = useCallback(async () => {
    const requestGeneration = ++requestGenerationRef.current
    loadingMoreRef.current = false
    dispatchPagination({type: 'reset', mode})
    setEntries([])
    setLoading(true)
    setError('')
    try {
      const journal = searchActive
        ? await searchJournal(searchQuery, mode)
        : await loadJournal(mode)
      if (!isLatestJournalRequest(requestGeneration, requestGenerationRef.current)) return
      setEntries(appendUniqueEntries([], journal.items))
      setTodayRevenue(journal.todayRevenue)
      setTotalItems(journal.totalItems)
      dispatchPagination({
        type: 'first-page-loaded',
        mode,
        page: journal.page,
        hasNext: journal.hasNext,
      })
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onLogout()
        return
      }
      if (!isLatestJournalRequest(requestGeneration, requestGenerationRef.current)) return
      setError(cause instanceof ApiError ? cause.message : 'Не удалось загрузить журнал')
    } finally {
      if (isLatestJournalRequest(requestGeneration, requestGenerationRef.current)) {
        setLoading(false)
      }
    }
  }, [mode, onLogout, searchActive, searchQuery])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const groups = useMemo(
    () => searchActive
      ? [['search-results', entries] as [string, JournalEntry[]]]
      : groupJournalEntries(entries),
    [entries, searchActive],
  )

  function changeMode(nextMode: JournalMode) {
    if (nextMode === mode) return

    requestGenerationRef.current += 1
    loadingMoreRef.current = false
    dispatchPagination({type: 'reset', mode: nextMode})
    setEntries([])
    setExpanded(new Set())
    setDetails(new Map())
    setError('')
    setLoading(true)
  }

  async function handleLoadMore() {
    if (loading || loadingMoreRef.current || !hasNext) return

    const requestedMode = mode
    const requestGeneration = requestGenerationRef.current
    const nextPage = page + 1
    loadingMoreRef.current = true
    dispatchPagination({type: 'load-more-started', mode: requestedMode})

    try {
      const journal = searchActive
        ? await searchJournal(searchQuery, requestedMode, nextPage)
        : await loadJournal(requestedMode, nextPage)
      if (!isLatestJournalRequest(requestGeneration, requestGenerationRef.current)) return

      setEntries((current) => appendUniqueEntries(current, journal.items))
      setTodayRevenue(journal.todayRevenue)
      setTotalItems(journal.totalItems)
      dispatchPagination({
        type: 'load-more-loaded',
        mode: requestedMode,
        page: journal.page,
        hasNext: journal.hasNext,
      })
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onLogout()
        return
      }
      if (!isLatestJournalRequest(requestGeneration, requestGenerationRef.current)) return
      dispatchPagination({type: 'load-more-failed', mode: requestedMode})
    } finally {
      if (isLatestJournalRequest(requestGeneration, requestGenerationRef.current)) {
        loadingMoreRef.current = false
      }
    }
  }

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
      if (searchActive) {
        await refresh()
        return
      }
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
    if (searchActive) {
      setPaymentEntry(null)
      void refresh()
      return
    }
    const previous = entries.find((entry) => entry.id === updated.id)
    if (previous) {
      setTodayRevenue((current) => (current ?? 0) + updated.paidAmount - previous.paidAmount)
    }
    setEntries((current) => current.map((entry) => entry.id === updated.id ? updated : entry))
    setDetails((current) => {
      const loaded = current.get(updated.id)
      if (!loaded) return current
      const next = new Map(current)
      next.set(updated.id, {
        ...loaded,
        paymentStatus: updated.paymentStatus,
        prepaymentAmount: updated.prepaymentAmount,
        paidAmount: updated.paidAmount,
        remainingAmount: updated.remainingAmount,
        version: updated.version,
      })
      return next
    })
    void loadJournalEntry(updated.id).then((loaded) => {
      setDetails((current) => new Map(current).set(updated.id, loaded))
    })
    setPaymentEntry(null)
  }

  function handlePaymentCorrected(updated: JournalEntryDetails) {
    setDetails((current) => new Map(current).set(updated.id, updated))
    setEntries((current) => current.map((entry) => (
      entry.id === updated.id ? summaryFromDetails(entry, updated) : entry
    )))
    setCorrectionPayment(null)
    void refresh()
  }

  function handleOrderUpdated(updated: JournalEntryDetails) {
    if (searchActive) {
      setEditingOrder(null)
      void refresh()
      return
    }
    const removeFromActive = mode === 'active'
      && terminalExecutionStatuses.includes(updated.executionStatus)

    setEntries((current) => (
      removeFromActive
        ? current.filter(({ id }) => id !== updated.id)
        : current.map((entry) => (
          entry.id === updated.id ? summaryFromDetails(entry, updated) : entry
        ))
    ))
    setDetails((current) => {
      const next = new Map(current)
      removeFromActive ? next.delete(updated.id) : next.set(updated.id, updated)
      return next
    })
    if (removeFromActive) {
      setExpanded((current) => {
        const next = new Set(current)
        next.delete(updated.id)
        return next
      })
    }
    setEditingOrder(null)
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
                onClick={() => changeMode('all')}
              >
                Все записи
              </button>
              <button
                className={mode === 'active' ? 'active' : ''}
                onClick={() => changeMode('active')}
              >
                Активные заказы
              </button>
            </div>
          </div>
        </section>

        <form
          className="journal-search"
          role="search"
          onSubmit={(event) => {
            event.preventDefault()
            if (isJournalSearchActive(searchInput)) setSearchQuery(searchInput.trim())
          }}
        >
          <span className="journal-search-icon" aria-hidden="true">⌕</span>
          <input
            type="search"
            value={searchInput}
            placeholder="Телефон, клиент, адрес, товар или номер записи"
            aria-label="Поиск по журналу"
            onChange={(event) => {
              const nextValue = event.target.value
              requestGenerationRef.current += 1
              loadingMoreRef.current = false
              if (searchActive || isJournalSearchActive(nextValue)) {
                setLoading(true)
                setError('')
              }
              setSearchInput(nextValue)
            }}
          />
          {searchInput && (
            <button
              type="button"
              aria-label="Очистить поиск"
              onClick={() => {
                requestGenerationRef.current += 1
                loadingMoreRef.current = false
                setLoading(true)
                setSearchInput('')
                setSearchQuery('')
              }}
            >
              ×
            </button>
          )}
        </form>

        {error && (
          <div className="notice notice-error" role="alert">
            <span>{error}</span>
            <button onClick={() => void refresh()}>Повторить</button>
          </div>
        )}

        {loading ? (
          searchActive ? (
            <div className="search-loading" role="status">Ищем записи…</div>
          ) : (
            <div className="loading-list" aria-label="Загружаем журнал">
              <div />
              <div />
              <div />
            </div>
          )
        ) : entries.length === 0 ? (
          <section className="empty-state">
            <div aria-hidden="true">○</div>
            <h3>{searchActive
              ? `По запросу «${searchQuery}» ничего не найдено ${mode === 'active' ? 'в активных заказах' : 'во всех записях'}`
              : (mode === 'active' ? 'Активных заказов нет' : 'Журнал пока пуст')}</h3>
            <p>
              {searchActive
                ? 'Попробуйте изменить запрос или область поиска.'
                : mode === 'active'
                ? 'Незавершённые заказы появятся здесь.'
                : 'Добавьте первую продажу из наличия.'}
            </p>
            {searchActive && mode === 'active' && (
              <button className="button button-primary" onClick={() => changeMode('all')}>
                Искать во всех записях
              </button>
            )}
            {!searchActive && mode === 'all' && (
              <button className="button button-primary" onClick={() => setSaleOpen(true)}>
                + Первая продажа
              </button>
            )}
          </section>
        ) : (
          <div className="journal-groups" id="journal-entry-groups">
            {searchActive && (
              <div className="search-result-count" role="status">
                Найдено записей: <strong>{totalItems}</strong>
              </div>
            )}
            {groups.map(([date, dateEntries]) => (
              <section className="journal-group" key={date}>
                {!searchActive && (
                  <header className="date-divider">
                    <h3>{date}</h3>
                    <span>{dateEntries.length} {dateEntries.length === 1 ? 'запись' : 'записи'}</span>
                  </header>
                )}
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
                            <small>{searchActive
                              ? `${formatDate(entry.createdAt)} · ${formatTime(entry.createdAt)}`
                              : formatTime(entry.createdAt)}</small>
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
                              {isOrder && entry.remainingAmount > 0 ? (
                                <button
                                  className={`status payment-status-button status-payment-${entry.paymentStatus.toLowerCase()}`}
                                  type="button"
                                  onClick={() => setPaymentEntry(entry)}
                                  aria-label={`Добавить платёж к заказу З-${entry.id}: ${paymentLabels[entry.paymentStatus]}`}
                                >
                                  <span>{entry.paymentStatus === 'PREPAID'
                                    ? `Предоплата ${formatMoney(entry.paidAmount)}`
                                    : paymentLabels[entry.paymentStatus]}</span>
                                  {entry.paymentStatus !== 'PAID' && (
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
                                  {entry.paymentStatus === 'PREPAID'
                                    ? `Предоплата ${formatMoney(entry.paidAmount)}`
                                    : paymentLabels[entry.paymentStatus]}
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

                        {searchActive && entry.matches.length > 0 && (
                          <p className="search-match">
                            <strong>Совпадение:</strong> {formatSearchMatches(entry.matches)}
                          </p>
                        )}

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
                              <>
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
                                          entryDetails.paidAmount,
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
                                <div className="order-details-actions">
                                  <button
                                    className="button button-quiet"
                                    type="button"
                                    onClick={() => setEditingOrder(entryDetails)}
                                  >
                                    Изменить заказ
                                  </button>
                                </div>
                              </>
                            ) : (
                              <div className="sale-details-meta">
                                <p>Продажа автоматически отмечена как оплаченная и завершённая.</p>
                                {entryDetails.comment && (
                                  <p className="order-comment">{entryDetails.comment}</p>
                                )}
                                <small>Создал: {entryDetails.createdByDisplayName}</small>
                              </div>
                            )}
                            <section className="payment-history">
                              <header className="payment-history-header">
                                <h4>История платежей</h4>
                                {isOrder && entry.remainingAmount > 0 && (
                                  <button
                                    className="button button-quiet"
                                    type="button"
                                    onClick={() => setPaymentEntry(entry)}
                                  >
                                    + Добавить платёж
                                  </button>
                                )}
                              </header>
                              {entryDetails.payments.length === 0 ? (
                                <p>Платежей пока нет.</p>
                              ) : (
                                <ul className="payment-history-list">
                                  {[...entryDetails.payments].sort(comparePaymentsNewestFirst).map((payment) => {
                                    const correction = entryDetails.payments.find(
                                      (candidate) => candidate.correctionOfId === payment.id,
                                    )
                                    return (
                                      <li key={payment.id} className={payment.active ? '' : 'payment-voided'}>
                                        <div>
                                          <strong>{formatMoney(payment.amount)}</strong>
                                          <span>{payment.paymentMethod
                                            ? paymentMethodLabels[payment.paymentMethod]
                                            : 'Не указано'}</span>
                                        </div>
                                        <small>
                                          {new Date(payment.receivedAt).toLocaleString('ru-RU')} · {payment.createdByDisplayName}
                                        </small>
                                        {payment.comment && <p>{payment.comment}</p>}
                                        {!payment.active && correction && (
                                          <p className="payment-correction-note">
                                            Исправлен {new Date(correction.createdAt).toLocaleString('ru-RU')}
                                            {' · '}{correction.createdByDisplayName}. Причина: {correction.correctionReason}
                                          </p>
                                        )}
                                        {payment.active && payment.correctionOfId && (
                                          <p className="payment-correction-note">Актуальная версия исправленного платежа</p>
                                        )}
                                        {payment.active && (
                                          <button
                                            className="button button-quiet payment-correct-button"
                                            type="button"
                                            onClick={() => setCorrectionPayment({ entryType: entryDetails.type, payment })}
                                          >
                                            Исправить платёж
                                          </button>
                                        )}
                                      </li>
                                    )
                                  })}
                                </ul>
                              )}
                            </section>
                          </div>
                        )}
                      </article>
                    )
                  })}
                </div>
              </section>
            ))}
            <div className="journal-pagination">
              {loadMoreError ? (
                <div className="journal-pagination-error" role="alert">
                  <span>{searchActive
                    ? 'Не удалось загрузить ещё записи'
                    : 'Не удалось загрузить более ранние записи'}</span>
                  <button
                    className="button button-quiet"
                    type="button"
                    onClick={() => void handleLoadMore()}
                  >
                    Повторить
                  </button>
                </div>
              ) : hasNext ? (
                <button
                  className="button button-quiet journal-load-more"
                  type="button"
                  disabled={loadingMore}
                  aria-controls="journal-entry-groups"
                  onClick={() => void handleLoadMore()}
                >
                  {loadingMore ? 'Загружаем…' : (searchActive ? 'Показать ещё' : 'Показать более ранние записи')}
                </button>
              ) : (
                <p className="journal-end">
                  {searchActive ? 'Все найденные записи загружены' : 'Более ранних записей нет'}
                </p>
              )}
              <p className="visually-hidden" aria-live="polite" aria-atomic="true">
                {announcement}
              </p>
            </div>
          </div>
        )}
      </main>

      {saleOpen && (
        <SaleDialog
          onClose={() => setSaleOpen(false)}
          onCreated={(sale) => {
            setSaleOpen(false)
            setSearchInput('')
            setSearchQuery('')
            changeMode('all')
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
            setSearchInput('')
            setSearchQuery('')
            changeMode('all')
            setEntries((current) => [order, ...current.filter(({ id }) => id !== order.id)])
            setTodayRevenue((current) => (current ?? 0) + order.paidAmount)
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

      {correctionPayment && (
        <PaymentCorrectionDialog
          entryType={correctionPayment.entryType}
          payment={correctionPayment.payment}
          onClose={() => setCorrectionPayment(null)}
          onUpdated={handlePaymentCorrected}
        />
      )}

      {editingOrder && (
        <OrderDialog
          order={editingOrder}
          onClose={() => setEditingOrder(null)}
          onUpdated={handleOrderUpdated}
        />
      )}
    </div>
  )
}
