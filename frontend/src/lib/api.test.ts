import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadJournal } from './api'

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
})
