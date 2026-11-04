import { describe, expect, it } from 'vitest'
import {
  formatDuration,
  judgmentTone,
  scoreWidth,
  statusTone,
  timingItems,
  timingLabel,
  workflowStages,
} from '../src/utils/display'

describe('durations and retrieval timings', () => {
  it('shows milliseconds below one second and seconds above', () => {
    expect(formatDuration(0)).toBe('0 ms')
    expect(formatDuration(23)).toBe('23 ms')
    expect(formatDuration(402.6)).toBe('403 ms')
    expect(formatDuration(1000)).toBe('1.0 s')
    expect(formatDuration(16210)).toBe('16.2 s')
    expect(formatDuration(null)).toBe('—')
    expect(formatDuration(undefined)).toBe('—')
    expect(formatDuration(Number.NaN)).toBe('—')
  })

  it('names the retrieval stages in Chinese instead of field names', () => {
    expect(timingLabel('bm25Ms')).toBe('BM25')
    expect(timingLabel('denseMs')).toBe('向量')
    expect(timingLabel('fusionMs')).toBe('融合')
    expect(timingLabel('rerankMs')).toBe('重排')
    expect(timingLabel('totalMs')).toBe('总计')
    expect(timingLabel('cacheMs')).toBe('cache')
  })

  it('lists stages in pipeline order without the total and marks the slowest', () => {
    const items = timingItems({ totalMs: 16640, rerankMs: 16210, fusionMs: 1, denseMs: 402, bm25Ms: 23 })
    expect(items.map((i) => i.label)).toEqual(['BM25', '向量', '融合', '重排'])
    expect(items.filter((i) => i.slowest).map((i) => i.label)).toEqual(['重排'])
    expect(items[0].text).toBe('23 ms')
    expect(timingItems({ totalMs: 5 })).toEqual([])
    expect(timingItems(null)).toEqual([])
  })
})

describe('tones for judgments and project status', () => {
  it('colors judgments by severity', () => {
    expect(judgmentTone('符合')).toBe('good')
    expect(judgmentTone('部分符合')).toBe('warning')
    expect(judgmentTone('不符合')).toBe('critical')
    expect(judgmentTone('不适用')).toBe('neutral')
    expect(judgmentTone('其他')).toBe('neutral')
  })

  it('colors project status by what the user should do next', () => {
    expect(statusTone('DRAFT')).toBe('neutral')
    expect(statusTone('READY')).toBe('info')
    expect(statusTone('ANALYZING')).toBe('progress')
    expect(statusTone('REVIEW')).toBe('warning')
    expect(statusTone('FAILED')).toBe('critical')
    expect(statusTone('SCORED')).toBe('good')
    expect(statusTone('REPORTED')).toBe('good')
  })
})

describe('workflow stages', () => {
  const states = (status: string) => workflowStages(status).map((s) => s.state)

  it('marks earlier stages done and the backend status as current', () => {
    expect(workflowStages('REVIEW').map((s) => s.label)).toEqual([
      '草稿',
      '待分析',
      '分析中',
      '待复核',
      '已确认',
      '已评分',
      '已出报告',
    ])
    expect(states('REVIEW')).toEqual(['done', 'done', 'done', 'current', 'todo', 'todo', 'todo'])
    expect(states('DRAFT')).toEqual(['current', 'todo', 'todo', 'todo', 'todo', 'todo', 'todo'])
  })

  it('shows a failed analysis at the analysis stage and a finished project as all done', () => {
    const failed = workflowStages('FAILED')
    expect(failed.map((s) => s.state)).toEqual(['done', 'done', 'error', 'todo', 'todo', 'todo', 'todo'])
    expect(failed[2].label).toBe('分析失败')
    expect(states('REPORTED')).toEqual(['done', 'done', 'done', 'done', 'done', 'done', 'done'])
  })
})

describe('score bars', () => {
  it('maps a score to a bar width and keeps it inside the track', () => {
    expect(scoreWidth(0.8333)).toBe('83.33%')
    expect(scoreWidth(62.48, 100)).toBe('62.48%')
    expect(scoreWidth(1.4)).toBe('100%')
    expect(scoreWidth(-1)).toBe('0%')
    expect(scoreWidth(null)).toBe('0%')
  })
})
