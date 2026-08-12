import { type FormEvent, useState } from 'react'
import { ApiError, login } from '../lib/api'
import type { User } from '../types'

interface LoginPageProps {
  onLogin: (user: User) => void
}

export function LoginPage({ onLogin }: LoginPageProps) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setSubmitting(true)

    try {
      onLogin(await login(username, password))
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Не удалось войти')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-shell">
      <section className="login-card" aria-labelledby="login-title">
        <div className="brand-mark" aria-hidden="true">К</div>
        <p className="eyebrow">Электронный журнал заказов</p>
        <h1 id="login-title">Камовед</h1>
        <p className="login-lead">
          Продажи, заказы и история покупателей в одном понятном журнале.
        </p>

        <form className="login-form" onSubmit={handleSubmit}>
          <label>
            Логин
            <input
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              required
            />
          </label>
          <label>
            Пароль
            <input
              autoComplete="current-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Введите пароль"
              required
              autoFocus
            />
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button button-primary button-wide" disabled={submitting}>
            {submitting ? 'Входим…' : 'Войти в журнал'}
          </button>
        </form>
      </section>
    </main>
  )
}
