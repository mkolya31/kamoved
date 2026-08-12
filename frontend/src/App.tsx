import { useCallback, useEffect, useState } from 'react'
import { currentUser } from './lib/api'
import { JournalPage } from './components/JournalPage'
import { LoginPage } from './components/LoginPage'
import type { User } from './types'

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
      <main className="boot-screen">
        <div className="brand-mark" aria-hidden="true">К</div>
        <p>Открываем журнал…</p>
      </main>
    )
  }

  return user
    ? <JournalPage user={user} onLogout={clearSession} />
    : <LoginPage onLogin={setUser} />
}

