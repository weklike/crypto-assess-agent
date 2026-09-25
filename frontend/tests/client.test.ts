import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, streamQa } from '../src/api/client'

function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('streamQa', () => {
  it('dispatches parsed JSON events in order', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          streamOf([
            'event:meta\ndata:{"mode":"hybrid_rerank","hits":[]}\n\nevent:tok',
            'en\ndata:{"text":"答"}\n\nevent:citations\ndata:{"valid":[],"invalid":[],"invalidCount":0}\n\n',
            'event:done\ndata:{"qaRecordId":1,"refused":false}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
      ),
    )
    const seen: string[] = []

    await streamQa({ question: '问题' }, {
      onMeta: () => seen.push('meta'),
      onToken: (t) => seen.push('token:' + t.text),
      onCitations: () => seen.push('citations'),
      onDone: (d) => seen.push('done:' + d.qaRecordId),
      onError: () => seen.push('error'),
    })

    expect(seen).toEqual(['meta', 'token:答', 'citations', 'done:1'])
  })

  it('throws ApiError with the ProblemDetail when the request is rejected', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: 503, title: '外部依赖不可用', detail: 'tei-rerank is unavailable' }), {
          status: 503,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )

    await expect(streamQa({ question: '问题' }, {})).rejects.toBeInstanceOf(ApiError)
  })

  it('reports a stream that ends without done as interrupted', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(streamOf(['event:meta\ndata:{"hits":[]}\n\n']), { status: 200 })),
    )

    await expect(streamQa({ question: '问题' }, {})).rejects.toThrow('中断')
  })
})
