import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import QaChat from '../src/components/QaChat.vue'
import { streamQa, type QaHandlers } from '../src/api/client'

vi.mock('../src/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/api/client')>()
  return {
    ...original,
    streamQa: vi.fn(async (_request: unknown, handlers: QaHandlers) => {
      handlers.onMeta?.({ mode: 'hybrid_rerank', refuseThreshold: 0.1, topScore: 0.9, hits: [], timings: {} })
      handlers.onToken?.({ text: '应加密存储<img src=x onerror=alert(1)>[FIX/T 0001-2026#5.4.3]' })
      handlers.onToken?.({ text: '，另见[FIX/T 0001-2026#9.9]。' })
      handlers.onCitations?.({
        valid: [{ clauseRef: 'FIX/T 0001-2026#5.4.3', title: '重要数据存储机密性' }],
        invalid: [{ clauseRef: 'FIX/T 0001-2026#9.9', reason: 'NOT_FOUND' }],
        invalidCount: 1,
      })
      handlers.onDone?.({ qaRecordId: 1, refused: false, latencyMs: 10, firstTokenMs: 5, model: 'm', inputTokens: 1, outputTokens: 2, formatIssues: [] })
    }),
  }
})

describe('QaChat', () => {
  it('renders model output as text and marks invalid citations', async () => {
    const wrapper = mount(QaChat, { global: { plugins: [ElementPlus] } })
    await wrapper.find('textarea').setValue('数据库里的敏感信息怎么保存')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const answer = wrapper.find('[data-testid="answer"]')
    expect(answer.find('img').exists()).toBe(false)
    expect(answer.text()).toContain('<img src=x onerror=alert(1)>')
    expect(answer.text()).toContain('FIX/T 0001-2026#9.9（无效）')
    expect(wrapper.find('[data-testid="invalid"]').text()).toContain('已剔除 1 条无效引用')
    expect(wrapper.text()).toContain('重要数据存储机密性')
  })

  it('shows markdown answers as plain text and says the format check failed', async () => {
    vi.mocked(streamQa).mockImplementationOnce(async (_request, handlers: QaHandlers) => {
      handlers.onMeta?.({ mode: 'hybrid_rerank', refuseThreshold: 0.1, topScore: 0.9, hits: [], timings: {} })
      handlers.onToken?.({ text: '## 结论\n- **应加密**存储[FIX/T 0001-2026#5.4.3]' })
      handlers.onCitations?.({ valid: [{ clauseRef: 'FIX/T 0001-2026#5.4.3', title: '重要数据存储机密性' }], invalid: [], invalidCount: 0 })
      handlers.onDone?.({ qaRecordId: 2, refused: false, latencyMs: 10, firstTokenMs: 5, model: 'm', inputTokens: 1, outputTokens: 2, formatIssues: ['heading', 'bold', 'bullet'] })
    })
    const wrapper = mount(QaChat, { global: { plugins: [ElementPlus] } })
    await wrapper.find('textarea').setValue('数据库里的敏感信息怎么保存')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const answer = wrapper.find('[data-testid="answer"]').text()
    expect(answer).not.toContain('**')
    expect(answer).not.toContain('##')
    expect(answer).toContain('• 应加密存储')
    expect(wrapper.find('[data-testid="format-issues"]').text()).toContain('标题')
  })
})
