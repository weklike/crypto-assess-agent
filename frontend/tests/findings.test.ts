import { describe, expect, it } from 'vitest'
import { dimensionsText, filterFindings, isTechnicalLayer, needsParameter, rkOptions, summarize } from '../src/utils/findings'
import type { AssessFinding } from '../src/api/assessments'

const f = (id: number, objectId: number, judgment: string, reviewed: boolean): AssessFinding => ({
  id,
  projectId: 1,
  objectId,
  clauseRef: `F#${id}`,
  judgment,
  evidence: null,
  ruleHitsJson: null,
  rationale: null,
  remediation: null,
  missingInfoJson: null,
  source: 'MODEL',
  llmCallId: null,
  reviewed,
  reviewerNote: null,
  reviewedAt: null,
  createdAt: '',
  updatedAt: '',
  dimD: null,
  dimA: null,
  dimK: null,
  ra: null,
  rk: null,
  moduleLevel: null,
})

describe('findings helpers', () => {
  const findings = [f(1, 1, '符合', true), f(2, 1, '不符合', false), f(3, 2, '部分符合', false), f(4, 2, '不适用', true)]

  it('filters by object, judgment and review state', () => {
    expect(filterFindings(findings, { objectId: 1 }).map((x) => x.id)).toEqual([1, 2])
    expect(filterFindings(findings, { gapsOnly: true }).map((x) => x.id)).toEqual([2, 3])
    expect(filterFindings(findings, { unreviewedOnly: true }).map((x) => x.id)).toEqual([2, 3])
    expect(filterFindings(findings, { judgment: '不适用' }).map((x) => x.id)).toEqual([4])
  })

  it('summarizes counts for the review progress bar', () => {
    expect(summarize(findings)).toEqual({ total: 4, reviewed: 2, gaps: 2 })
  })
})

describe('D/A/K helpers', () => {
  const dims = (d: boolean | null, a: boolean | null, k: boolean | null, ra: number | null = null, rk: number | null = null) => ({
    ...f(9, 1, '部分符合', false),
    dimD: d,
    dimA: a,
    dimK: k,
    ra,
    rk,
  })

  it('knows which layers are scored by D/A/K', () => {
    expect(isTechnicalLayer('应用和数据')).toBe(true)
    expect(isTechnicalLayer('物理和环境')).toBe(true)
    expect(isTechnicalLayer('管理制度')).toBe(false)
  })

  it('describes dimensions like the report does', () => {
    expect(dimensionsText(dims(true, true, true))).toBe('D√ A√ K√')
    expect(dimensionsText(dims(false, null, null))).toBe('D×')
    expect(dimensionsText(dims(true, false, true, 0.5))).toBe('D√ A× K√ Ra=0.5')
    expect(dimensionsText(dims(true, false, false, 0.2, 1.2))).toBe('D√ A× K× Ra=0.2 Rk=1.2')
    expect(dimensionsText(dims(null, null, null))).toBe('')
  })

  it('flags a missing Ra or Rk that scoring will need', () => {
    expect(needsParameter(dims(true, false, true))).toBe('Ra')
    expect(needsParameter(dims(true, true, false))).toBe('Rk')
    expect(needsParameter(dims(true, false, true, 0.5))).toBeNull()
    expect(needsParameter(dims(false, null, null))).toBeNull()
  })

  it('offers Rk values allowed for the level (Table 3)', () => {
    expect(rkOptions(2)).toEqual([1])
    expect(rkOptions(3)).toEqual([1, 1.2])
    expect(rkOptions(4)).toEqual([1, 1.5])
  })
})
