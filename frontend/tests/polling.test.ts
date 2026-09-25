import { afterEach, describe, expect, it, vi } from 'vitest'
import { pollUntil } from '../src/utils/polling'

afterEach(() => vi.useRealTimers())

describe('pollUntil', () => {
  it('polls until the backend reports a settled status', async () => {
    vi.useFakeTimers()
    const statuses = ['ANALYZING', 'ANALYZING', 'REVIEW']
    const fetcher = vi.fn(async () => ({ status: statuses.shift() ?? 'REVIEW' }))
    const seen: string[] = []

    const promise = pollUntil(fetcher, (d) => d.status !== 'ANALYZING', { intervalMs: 1000, onUpdate: (d) => seen.push(d.status) })
    await vi.advanceTimersByTimeAsync(2500)
    const result = await promise

    expect(result.status).toBe('REVIEW')
    expect(fetcher).toHaveBeenCalledTimes(3)
    expect(seen).toEqual(['ANALYZING', 'ANALYZING', 'REVIEW'])
  })

  it('stops when aborted', async () => {
    vi.useFakeTimers()
    const controller = new AbortController()
    const fetcher = vi.fn(async () => ({ status: 'ANALYZING' }))

    const promise = pollUntil(fetcher, () => false, { intervalMs: 1000, signal: controller.signal })
    const rejected = expect(promise).rejects.toThrow('aborted')
    await vi.advanceTimersByTimeAsync(1500)
    controller.abort()
    await vi.advanceTimersByTimeAsync(1500)

    await rejected
    expect(fetcher.mock.calls.length).toBeLessThanOrEqual(3)
  })
})
