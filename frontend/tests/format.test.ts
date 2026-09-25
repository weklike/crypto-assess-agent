import { describe, expect, it } from 'vitest'
import { formatBeijing, formatTimesInText } from '../src/utils/time'
import { plainText } from '../src/utils/plainText'

describe('Beijing time formatting', () => {
  it('shows an instant as Beijing 24-hour time', () => {
    expect(formatBeijing('2026-09-25T01:11:31.562501174Z')).toBe('2026-09-25 09:11:31')
    expect(formatBeijing('2026-09-24T19:49:25Z')).toBe('2026-09-25 03:49:25')
    expect(formatBeijing('2026-09-25T15:00:00+08:00')).toBe('2026-09-25 15:00:00')
    expect(formatBeijing(null)).toBe('—')
    expect(formatBeijing('not a time')).toBe('not a time')
  })

  it('rewrites ISO instants embedded in text such as old project names', () => {
    expect(formatTimesInText('eval-gap 2026-09-25T01:11:31.562501174Z')).toBe('eval-gap 2026-09-25 09:11:31')
    expect(formatTimesInText('2026 年度自查')).toBe('2026 年度自查')
  })
})

describe('plain text display of model answers', () => {
  it('removes markdown markers but keeps citations and numbered points', () => {
    const md = '## 结论\n- **应加密**存储 [FIX/T 0001-2026#5.4.3]\n* 使用 `SM4`\n> 注意\n1. 第一点\n详见 [说明](https://example.com)'
    expect(plainText(md)).toBe('结论\n• 应加密存储 [FIX/T 0001-2026#5.4.3]\n• 使用 SM4\n注意\n1. 第一点\n详见 说明')
  })

  it('leaves plain text untouched', () => {
    const text = '1. 重要数据应加密【FIX/T 0001-2026#5.2.3】。\n2. 回答仅供辅助自查。'
    expect(plainText(text)).toBe(text)
  })
})
