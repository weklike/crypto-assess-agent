export interface PollOptions<T> {
  intervalMs?: number
  signal?: AbortSignal
  onUpdate?: (value: T) => void
}

/**
 * 轮询直到 done 返回 true。状态完全以后端返回为准，前端不推断状态转换。
 * 分析是后台任务、只有状态变化没有增量数据，所以用轮询而不是 SSE。
 */
export async function pollUntil<T>(fetcher: () => Promise<T>, done: (value: T) => boolean, options: PollOptions<T> = {}): Promise<T> {
  const interval = options.intervalMs ?? 2000
  for (;;) {
    if (options.signal?.aborted) throw new Error('polling aborted')
    const value = await fetcher()
    options.onUpdate?.(value)
    if (done(value)) return value
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(resolve, interval)
      options.signal?.addEventListener('abort', () => {
        clearTimeout(timer)
        reject(new Error('polling aborted'))
      }, { once: true })
    })
  }
}
