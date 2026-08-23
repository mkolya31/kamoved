import { useCallback, useEffect, useState } from 'react'
import { currentUser } from './lib/api'
import { JournalPage } from './components/JournalPage'
import { LoginPage } from './components/LoginPage'
import type { User } from './types'

function LocalEnvironmentBanner() {
  if (!import.meta.env.DEV) {
    return null
  }

  return (
    <div className="local-environment-banner" role="status">
      Локальная версия
    </div>
  )
}

export default function App() {
  const [user, setUser] = useState<User | null>(null)
  const [checkingSession, setCheckingSession] = useState(true)

  const clearSession = useCallback(() => setUser(null), [])

  useEffect(() => {
    currentUser()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setCheckingSession(false))
  }, [])

  if (checkingSession) {
    return (
      <>
        <LocalEnvironmentBanner />
        <main className="boot-screen">
          <div className={`brand-mark${import.meta.env.DEV ? ' brand-mark-local' : ''}`} aria-hidden="true">К</div>
          <p>Открываем журнал…</p>
        </main>
      </>
    )
  }

  return (
    <>
      <LocalEnvironmentBanner />
      {user
        ? <JournalPage user={user} onLogout={clearSession} />
        : <LoginPage onLogin={setUser} />}
    </>
  )
}
