import { describe, expect, it } from 'vitest'
import { normalizeRef, splitAnswer } from '../src/utils/citations'

describe('splitAnswer', () => {
  it('turns citations into segments marked valid or invalid', () => {
    const segments = splitAnswer('应加密[FIX/T 0001-2026#5.4.3]，另见【FIX/T 0001-2026#9.9】。', ['FIX/T 0001-2026#5.4.3'])
    expect(segments).toEqual([
      { kind: 'text', text: '应加密' },
      { kind: 'citation', ref: 'FIX/T 0001-2026#5.4.3', valid: true },
      { kind: 'text', text: '，另见' },
      { kind: 'citation', ref: 'FIX/T 0001-2026#9.9', valid: false },
      { kind: 'text', text: '。' },
    ])
  })

  it('keeps ordinary brackets as text and never produces HTML', () => {
    const segments = splitAnswer('[注意] <script>alert(1)</script>', [])
    expect(segments).toEqual([{ kind: 'text', text: '[注意] <script>alert(1)</script>' }])
  })

  it('splits multiple refs inside one bracket', () => {
    const segments = splitAnswer('[A/T 1-2020#1；A/T 1-2020#2]', ['A/T 1-2020#1', 'A/T 1-2020#2'])
    expect(segments.filter((s) => s.kind === 'citation')).toHaveLength(2)
  })
})

describe('normalizeRef', () => {
  it('normalizes spacing and dashes like the backend parser', () => {
    expect(normalizeRef(' GB/T39786—2021 # 6.2.1 ')).toBe('GB/T 39786-2021#6.2.1')
    expect(normalizeRef('注意')).toBeNull()
  })
})
