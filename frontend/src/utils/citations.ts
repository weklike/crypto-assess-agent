export type AnswerSegment =
  | { kind: 'text'; text: string }
  | { kind: 'citation'; ref: string; valid: boolean }

const BRACKET = /[[【［]([^[\]【】［］]{1,200})[\]】］]/g
const REF =
  /^([A-Z]{1,4}(?:\s*\/\s*[A-Z]{1,2})?)\s*(\d+(?:\.\d+)?)\s*[-—–－]\s*(\d{4})\s*[#＃]\s*([0-9]+(?:\.[0-9]+)*|[A-Z](?:\.[0-9]+)*)$/
const SEPARATOR = /[;；,，、]/

/** 与后端 CitationParser 相同的规范化规则；不是条款引用时返回 null。 */
export function normalizeRef(raw: string): string | null {
  const match = REF.exec(raw.trim())
  if (!match) {
    return null
  }
  return `${match[1].replace(/\s+/g, '')} ${match[2]}-${match[3]}#${match[4]}`
}

/**
 * 把模型回答拆成纯文本片段和引用片段，页面逐段渲染成文本节点和引用标签，不使用 v-html。
 * 引用是否有效以后端 citations 事件为准（validRefs）。
 */
export function splitAnswer(text: string, validRefs: string[]): AnswerSegment[] {
  const valid = new Set(validRefs)
  const segments: AnswerSegment[] = []
  let last = 0
  const pushText = (value: string) => {
    if (!value) return
    const previous = segments[segments.length - 1]
    if (previous && previous.kind === 'text') {
      previous.text += value
    } else {
      segments.push({ kind: 'text', text: value })
    }
  }
  for (const match of text.matchAll(BRACKET)) {
    const refs = match[1]
      .split(SEPARATOR)
      .map(normalizeRef)
      .filter((ref): ref is string => ref !== null)
    if (refs.length === 0) {
      continue
    }
    pushText(text.slice(last, match.index))
    for (const ref of refs) {
      segments.push({ kind: 'citation', ref, valid: valid.has(ref) })
    }
    last = (match.index ?? 0) + match[0].length
  }
  pushText(text.slice(last))
  return segments
}
