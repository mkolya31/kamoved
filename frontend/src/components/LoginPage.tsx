import { type FormEvent, useState } from 'react'
import { ApiError, login } from '../lib/api'
import type { User } from '../types'

interface LoginPageProps {
  onLogin: (user: User) => void
}

export function LoginPage({ onLogin }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [passwordVisible, setPasswordVisible] = useState(false)
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
      <section className="login-card">
        <form className="login-form" onSubmit={handleSubmit}>
          <div className="login-field">
            <label htmlFor="login-username">Логин</label>
            <input
              id="login-username"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="Введите логин"
              required
              autoFocus
            />
          </div>
          <div className="login-field">
            <label htmlFor="login-password">Пароль</label>
            <span className="password-field">
              <input
                id="login-password"
                autoComplete="current-password"
                type={passwordVisible ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Введите пароль"
                required
              />
              <button
                className="password-visibility-button"
                type="button"
                aria-label={passwordVisible ? 'Скрыть пароль' : 'Показать пароль'}
                aria-pressed={passwordVisible}
                title={passwordVisible ? 'Скрыть пароль' : 'Показать пароль'}
                onClick={() => setPasswordVisible((visible) => !visible)}
              >
                {passwordVisible ? (
                  <svg aria-hidden="true" viewBox="0 0 24 24">
                    <path d="m3 3 18 18M10.6 10.7a2 2 0 0 0 2.7 2.7M9.9 4.2A10.7 10.7 0 0 1 12 4c5.5 0 9 6 9 6a15.5 15.5 0 0 1-2.1 2.8M6.6 6.7C4.3 8.2 3 10 3 10s3.5 6 9 6c1 0 2-.2 2.9-.6" />
                  </svg>
                ) : (
                  <svg aria-hidden="true" viewBox="0 0 24 24">
                    <path d="M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6Z" />
                    <circle cx="12" cy="12" r="2.5" />
                  </svg>
                )}
              </button>
            </span>
          </div>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button button-primary button-wide" disabled={submitting}>
            {submitting ? 'Входим…' : 'Войти'}
          </button>
        </form>
      </section>
    </main>
  )
}
