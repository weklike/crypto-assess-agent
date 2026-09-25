import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import AssessmentDetail from '../src/components/AssessmentDetail.vue'
import type { AssessmentDetail as Detail } from '../src/api/assessments'

const base = (status: string, reviewed: boolean): Detail => ({
  project: { id: 1, name: '自查 2026-09-25T01:11:31Z', systemName: '营销系统', level: 3, status: status as never, lastError: null, version: 1, createdAt: '', updatedAt: '' },
  objects: [{ id: 10, layer: '应用和数据', name: '数据库', description: null, measures: { transport: [], storage: [{ algorithm: 'SM4' }], auth: [], key_mgmt: [], other: [] } }],
  steps: [],
  findings: [
    { id: 100, projectId: 1, objectId: 10, clauseRef: 'F#1', judgment: '不符合', evidence: '证据', ruleHitsJson: null, rationale: '理由', remediation: '建议', missingInfoJson: null, source: 'MODEL', llmCallId: 1, reviewed, reviewerNote: null, reviewedAt: null, createdAt: '', updatedAt: '', dimD: true, dimA: false, dimK: true, ra: null, rk: null, moduleLevel: null },
  ],
  scores: [],
})

const detail = vi.fn()
const review = vi.fn()
vi.mock('../src/api/assessments', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/api/assessments')>()
  return {
    ...original,
    assessmentsApi: {
      ...original.assessmentsApi,
      detail: (...args: unknown[]) => detail(...args),
      review: (...args: unknown[]) => review(...args),
    },
  }
})

describe('AssessmentDetail', () => {
  it('shows backend status and only enables confirm when every finding is reviewed', async () => {
    detail.mockResolvedValue(base('REVIEW', false))
    const wrapper = mount(AssessmentDetail, { props: { id: 1 }, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.find('[data-testid="status"]').text()).toBe('待复核')
    expect(wrapper.find('[data-testid="confirm"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="analyze"]').attributes('disabled')).toBeUndefined()

    detail.mockResolvedValue(base('REVIEW', true))
    await wrapper.setProps({ id: 2 })
    await flushPromises()
    expect(wrapper.find('[data-testid="confirm"]').attributes('disabled')).toBeUndefined()
  })

  it('disables analysis while the backend reports ANALYZING', async () => {
    detail.mockResolvedValue(base('ANALYZING', false))
    const wrapper = mount(AssessmentDetail, { props: { id: 3 }, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.find('[data-testid="status"]').text()).toBe('分析中')
    expect(wrapper.find('[data-testid="analyze"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('shows D/A/K for technical findings, flags a missing Ra and sends dimensions on edit', async () => {
    detail.mockResolvedValue(base('REVIEW', false))
    review.mockResolvedValue({})
    const wrapper = mount(AssessmentDetail, { props: { id: 4 }, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const dims = wrapper.find('[data-testid="dims-100"]')
    expect(dims.text()).toContain('D')
    expect(wrapper.find('[data-testid="missing-100"]').text()).toContain('Ra')

    await wrapper.find('[data-testid="dim-k-100"] input').setValue(false)
    await flushPromises()
    expect(review).toHaveBeenCalledWith(4, 100, { d: true, a: false, k: false, ra: null, rk: null })
    wrapper.unmount()
  })

  it('shows why there is no total instead of a number', async () => {
    const scored = base('SCORED', true)
    scored.scores = [
      { scope: 'total', scopeKey: 'total', score: null, ruleVersion: 'scoring.v2', detailJson: { note: '密码应用管理要求的所有安全层面都不适用，不出总分，转人工判断' } },
      { scope: 'layer', scopeKey: '应用和数据', score: 0.5, ruleVersion: 'scoring.v2', detailJson: {} },
    ]
    detail.mockResolvedValue(scored)
    const wrapper = mount(AssessmentDetail, { props: { id: 5 }, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.find('[data-testid="total"]').text()).toContain('不出总分')
    expect(wrapper.find('[data-testid="total"]').text()).toContain('转人工判断')
    wrapper.unmount()
  })

  it('explains the D/A/K dimensions in words and formats the title time', async () => {
    detail.mockResolvedValue(base('REVIEW', false))
    const wrapper = mount(AssessmentDetail, { props: { id: 6 }, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.find('h3').text()).toContain('自查 2026-09-25 09:11:31')
    const headers = wrapper.find('[data-testid="findings"]').text()
    expect(headers).toContain('测评维度')
    expect(headers).not.toContain('D / A / K')
    const dims = wrapper.find('[data-testid="dims-100"]').text()
    expect(dims).toContain('有效性')
    expect(dims).toContain('算法合规')
    expect(dims).toContain('密钥管理')
    wrapper.unmount()
  })
})
