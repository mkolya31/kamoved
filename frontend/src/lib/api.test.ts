import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadJournal, searchJournal } from './api'

describe('loadJournal', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('requests the selected mode and the next 30-entry page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [],
      page: 2,
      size: 30,
      hasNext: false,
      todayRevenue: 0,
    }), {
      status: 200,
      headers: {'Content-Type': 'application/json'},
    }))
    vi.stubGlobal('fetch', fetchMock)

    await loadJournal('active', 2)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/journal?mode=active&page=2&size=30',
      {credentials: 'include'},
    )
  })

  it('encodes a server-side journal search request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [], page: 0, size: 30, hasNext: false, todayRevenue: 0, totalItems: 0,
    }), {status: 200, headers: {'Content-Type': 'application/json'}}))
    vi.stubGlobal('fetch', fetchMock)

    await searchJournal('Владимир готика', 'active')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/journal/search?query=%D0%92%D0%BB%D0%B0%D0%B4%D0%B8%D0%BC%D0%B8%D1%80+%D0%B3%D0%BE%D1%82%D0%B8%D0%BA%D0%B0&mode=active&page=0&size=30',
      {credentials: 'include'},
    )
  })
})
