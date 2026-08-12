import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, loadJournal, loadJournalEntry, logout } from '../lib/api'
import {
  executionLabels,
  formatDate,
  formatMoney,
  formatQuantity,
  formatTime,
  paymentLabels,
} from '../lib/format'
import type { JournalEntry, JournalEntryDetails, User } from '../types'
import { SaleDialog } from './SaleDialog'

interface JournalPageProps {
  user: User
  onLogout: () => void
}

export function JournalPage({ user, onLogout }: JournalPageProps) {
  const [mode, setMode] = useState<'all' | 'active'>('all')
  const [entries, setEntries] = useState<JournalEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saleOpen, setSaleOpen] = useState(false)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [details, setDetails] = useState<Map<number, JournalEntryDetails>>(new Map())

  const refresh = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setEntries((await loadJournal(mode)).items)
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

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-block">
          <div className="brand-mark brand-mark-small" aria-hidden="true">К</div>
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
            disabled
            title="Будет добавлено на следующем этапе"
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
                    const isExpanded = expanded.has(entry.id)
                    const entryDetails = details.get(entry.id)
                    return (
                      <article className="entry-card" key={entry.id}>
                        <button
                          className="entry-summary"
                          type="button"
                          onClick={() => void toggleExpanded(entry.id)}
                          aria-expanded={isExpanded}
                        >
                          <span className="entry-number">
                            <strong>П-{entry.id}</strong>
                            <small>{formatTime(entry.createdAt)}</small>
                          </span>
                          <span className="entry-kind">
                            <i aria-hidden="true">●</i>
                            Продажа из наличия
                          </span>
                          <span className="entry-product">
                            <strong>{mainItem?.name ?? 'Без позиции'}</strong>
                            {mainItem && <small>{formatQuantity(mainItem.quantity, mainItem.unit)}</small>}
                          </span>
                          <strong className="entry-total">{formatMoney(entry.totalAmount)}</strong>
                          <span className="status-stack">
                            <span className="status status-paid">
                              {paymentLabels[entry.paymentStatus]}
                            </span>
                            <span className="status status-completed">
                              {executionLabels[entry.executionStatus]}
                            </span>
                          </span>
                          <span className="chevron" aria-hidden="true">{isExpanded ? '⌃' : '⌄'}</span>
                        </button>

                        {isExpanded && !entryDetails && (
                          <div className="entry-details-loading">Открываем состав продажи…</div>
                        )}

                        {isExpanded && entryDetails && (
                          <div className="entry-details">
                            <div>
                              <h4>Состав продажи</h4>
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
                            <p>
                              Продажа автоматически отмечена как оплаченная и завершённая.
                            </p>
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
            setExpanded(new Set())
          }}
        />
      )}
    </div>
  )
}
