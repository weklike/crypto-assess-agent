export interface SseEvent {
  event: string
  data: string
}

/**
 * 增量解析 text/event-stream。网络分块可能在任意位置切断，未完成的行留在缓冲区里等下一块。
 * 用 fetch + ReadableStream 而不是 EventSource：问答是 POST 请求，EventSource 只支持 GET。
 */
export class SseParser {
  private buffer = ''
  private eventName = ''
  private dataLines: string[] = []

  push(chunk: string): SseEvent[] {
    this.buffer += chunk
    const events: SseEvent[] = []
    let newline: number
    while ((newline = this.buffer.search(/\r\n|\n|\r/)) >= 0) {
      const line = this.buffer.slice(0, newline)
      const sepLength = this.buffer.startsWith('\r\n', newline) ? 2 : 1
      // 行尾是单独的 \r 时，下一块可能以 \n 开头；等下一块再判断
      if (sepLength === 1 && this.buffer[newline] === '\r' && newline === this.buffer.length - 1) {
        break
      }
      this.buffer = this.buffer.slice(newline + sepLength)
      const event = this.processLine(line)
      if (event) {
        events.push(event)
      }
    }
    return events
  }

  flush(): SseEvent[] {
    const events: SseEvent[] = []
    if (this.buffer.length > 0) {
      const event = this.processLine(this.buffer)
      this.buffer = ''
      if (event) {
        events.push(event)
      }
    }
    const last = this.dispatch()
    if (last) {
      events.push(last)
    }
    return events
  }

  private processLine(line: string): SseEvent | null {
    if (line === '') {
      return this.dispatch()
    }
    if (line.startsWith(':')) {
      return null
    }
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    let value = colon < 0 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      this.eventName = value
    } else if (field === 'data') {
      this.dataLines.push(value)
    }
    return null
  }

  private dispatch(): SseEvent | null {
    const hasData = this.dataLines.length > 0
    const event = { event: this.eventName || 'message', data: this.dataLines.join('\n') }
    this.eventName = ''
    this.dataLines = []
    return hasData ? event : null
  }
}
