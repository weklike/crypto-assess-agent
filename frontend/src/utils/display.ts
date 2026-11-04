/** 页面展示用的纯函数：耗时格式、检索阶段名称、徽标色调、工作流阶段。不含业务判断。 */

export type Tone = 'good' | 'warning' | 'critical' | 'neutral' | 'info' | 'progress'

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || Number.isNaN(ms)) return '—'
  if (ms < 1000) return `${Math.round(ms)} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

const TIMING_LABELS: Record<string, string> = {
  bm25Ms: 'BM25',
  denseMs: '向量',
  fusionMs: '融合',
  rerankMs: '重排',
  totalMs: '总计',
}

/** 检索接口按流水线顺序返回的阶段，未知阶段排在后面 */
const TIMING_ORDER = ['bm25Ms', 'denseMs', 'fusionMs', 'rerankMs']

export function timingLabel(key: string): string {
  return TIMING_LABELS[key] ?? key.replace(/Ms$/, '')
}

export interface TimingItem {
  key: string
  label: string
  ms: number
  text: string
  slowest: boolean
}

/** 各阶段耗时（不含总计），标出最慢的一环。BM25 与向量并行执行，所以不画成首尾相接的条。 */
export function timingItems(timings: Record<string, number> | null | undefined): TimingItem[] {
  if (!timings) return []
  const keys = Object.keys(timings)
    .filter((k) => k !== 'totalMs')
    .sort((a, b) => rank(a) - rank(b))
  const max = Math.max(0, ...keys.map((k) => timings[k] ?? 0))
  return keys.map((key) => ({
    key,
    label: timingLabel(key),
    ms: timings[key],
    text: formatDuration(timings[key]),
    slowest: max > 0 && timings[key] === max,
  }))
}

function rank(key: string): number {
  const index = TIMING_ORDER.indexOf(key)
  return index < 0 ? TIMING_ORDER.length : index
}

const JUDGMENT_TONES: Record<string, Tone> = {
  符合: 'good',
  部分符合: 'warning',
  不符合: 'critical',
  不适用: 'neutral',
}

export function judgmentTone(judgment: string): Tone {
  return JUDGMENT_TONES[judgment] ?? 'neutral'
}

const STATUS_TONES: Record<string, Tone> = {
  DRAFT: 'neutral',
  READY: 'info',
  ANALYZING: 'progress',
  REVIEW: 'warning',
  FAILED: 'critical',
  CONFIRMED: 'info',
  SCORED: 'good',
  REPORTED: 'good',
}

/** 状态色调只表达“下一步该谁做什么”：待复核要人处理，分析中在跑，失败要处理。 */
export function statusTone(status: string): Tone {
  return STATUS_TONES[status] ?? 'neutral'
}

export interface WorkflowStage {
  key: string
  label: string
  state: 'done' | 'current' | 'error' | 'todo'
}

const STAGES: { key: string; label: string }[] = [
  { key: 'DRAFT', label: '草稿' },
  { key: 'READY', label: '待分析' },
  { key: 'ANALYZING', label: '分析中' },
  { key: 'REVIEW', label: '待复核' },
  { key: 'CONFIRMED', label: '已确认' },
  { key: 'SCORED', label: '已评分' },
  { key: 'REPORTED', label: '已出报告' },
]

/**
 * 把后端返回的状态画成阶段条。只做展示：当前阶段就是后端状态，前端不推断下一步。
 * FAILED 画在“分析中”的位置；REPORTED 是终态，全部标为完成。
 */
export function workflowStages(status: string): WorkflowStage[] {
  const failed = status === 'FAILED'
  const current = failed ? STAGES.findIndex((s) => s.key === 'ANALYZING') : STAGES.findIndex((s) => s.key === status)
  return STAGES.map((stage, index) => {
    if (status === 'REPORTED' || index < current) return { ...stage, state: 'done' }
    if (index === current) return failed ? { ...stage, label: '分析失败', state: 'error' } : { ...stage, state: 'current' }
    return { ...stage, state: 'todo' }
  })
}

/** 得分条宽度：按满分换算成百分比，夹在 0–100% 之间；没有分数时为 0。 */
export function scoreWidth(score: number | null | undefined, max = 1): string {
  if (score === null || score === undefined || Number.isNaN(score) || max <= 0) return '0%'
  const percent = Math.min(100, Math.max(0, (score / max) * 100))
  return `${Number(percent.toFixed(2))}%`
}
