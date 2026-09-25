import { describe, expect, it } from 'vitest'
import { SseParser } from '../src/api/sse'

describe('SseParser', () => {
  it('emits an event after a blank line', () => {
    const parser = new SseParser()
    expect(parser.push('event:meta\ndata:{"a":1}\n\n')).toEqual([{ event: 'meta', data: '{"a":1}' }])
  })

  it('handles events split across chunks at any position', () => {
    const parser = new SseParser()
    const text = 'event:token\ndata:{"text":"密评"}\n\nevent:done\ndata:{}\n\n'
    const events = []
    for (const ch of text) {
      events.push(...parser.push(ch))
    }
    expect(events).toEqual([
      { event: 'token', data: '{"text":"密评"}' },
      { event: 'done', data: '{}' },
    ])
  })

  it('supports CRLF, optional space after colon, multi-line data and comments', () => {
    const parser = new SseParser()
    const events = parser.push(': keep-alive\r\nevent: token\r\ndata: line1\r\ndata: line2\r\n\r\n')
    expect(events).toEqual([{ event: 'token', data: 'line1\nline2' }])
  })

  it('defaults event name to message and ignores events without data', () => {
    const parser = new SseParser()
    expect(parser.push('data:x\n\nevent:ping\n\n')).toEqual([{ event: 'message', data: 'x' }])
  })

  it('flushes a trailing event without final blank line', () => {
    const parser = new SseParser()
    expect(parser.push('event:done\ndata:{"refused":false}')).toEqual([])
    expect(parser.flush()).toEqual([{ event: 'done', data: '{"refused":false}' }])
  })
})
