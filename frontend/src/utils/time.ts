const ISO_INSTANT = /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})/g

const BEIJING = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

/** 后端时间一律按北京时间 24 小时制显示：2026-09-25 09:11:31。认不出的原样返回。 */
export function formatBeijing(value: string | null | undefined): string {
  if (!value) return '—'
  // 小数秒可能到纳秒，Date 只认毫秒，先截到 3 位
  const millis = Date.parse(value.replace(/(\.\d{3})\d+/, '$1'))
  if (Number.isNaN(millis)) return value
  const parts = Object.fromEntries(BEIJING.formatToParts(new Date(millis)).map((p) => [p.type, p.value]))
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

/** 把文本里嵌入的 ISO 时间（如旧的评测项目名称）换成北京时间。 */
export function formatTimesInText(text: string): string {
  return text.replace(ISO_INSTANT, (iso) => formatBeijing(iso))
}
