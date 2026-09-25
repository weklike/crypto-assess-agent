import { SseParser } from './sse'
import type { Clause, ClausePage, ProblemDetail, QaCitations, QaDone, QaMeta, QaRequest, SearchRequest, SearchResponse } from './types'

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `HTTP ${status}`)
    this.status = status
    this.problem = problem
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = { status: response.status, title: response.statusText }
  try {
    problem = await response.json()
  } catch {
    // 非 JSON 错误体，保留状态码
  }
  return new ApiError(response.status, problem)
}

export async function search(request: SearchRequest): Promise<SearchResponse> {
  const response = await fetch('/api/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw await toApiError(response)
  return response.json()
}

export async function browseClauses(params: {
  page: number
  size: number
  layer: string | null
  level: number | null
}): Promise<ClausePage> {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.layer) query.set('layer', params.layer)
  if (params.level != null) query.set('level', String(params.level))
  const response = await fetch(`/api/kb/clauses/page?${query}`)
  if (!response.ok) throw await toApiError(response)
  return response.json()
}

export async function getClause(ref: string): Promise<Clause> {
  const response = await fetch(`/api/kb/clauses?ref=${encodeURIComponent(ref)}`)
  if (!response.ok) throw await toApiError(response)
  return response.json()
}

export interface QaHandlers {
  onMeta?: (meta: QaMeta) => void
  onToken?: (token: { text: string }) => void
  onCitations?: (citations: QaCitations) => void
  onDone?: (done: QaDone) => void
  onError?: (problem: ProblemDetail) => void
}

/**
 * 发起问答并逐个分发 SSE 事件。请求被拒（4xx/5xx）时抛 ApiError；流在 done/error 之前结束视为中断。
 * 断线不自动重连：问答会写 qa_record，重发请求不是幂等的，由用户决定是否重问。
 */
export async function streamQa(request: QaRequest, handlers: QaHandlers, signal?: AbortSignal): Promise<void> {
  const response = await fetch('/api/qa', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream, application/problem+json' },
    body: JSON.stringify(request),
    signal,
  })
  if (!response.ok || !response.body) throw await toApiError(response)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()
  let finished = false
  const dispatch = (name: string, data: string) => {
    const payload = JSON.parse(data)
    switch (name) {
      case 'meta':
        handlers.onMeta?.(payload)
        break
      case 'token':
        handlers.onToken?.(payload)
        break
      case 'citations':
        handlers.onCitations?.(payload)
        break
      case 'done':
        finished = true
        handlers.onDone?.(payload)
        break
      case 'error':
        finished = true
        handlers.onError?.(payload)
        break
    }
  }
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    for (const event of parser.push(decoder.decode(value, { stream: true }))) dispatch(event.event, event.data)
  }
  for (const event of parser.flush()) dispatch(event.event, event.data)
  if (!finished) {
    throw new Error('连接中断，回答不完整')
  }
}
