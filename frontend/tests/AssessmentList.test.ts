import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import AssessmentList from '../src/components/AssessmentList.vue'

vi.mock('../src/api/assessments', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/api/assessments')>()
  return {
    ...original,
    assessmentsApi: {
      ...original.assessmentsApi,
      list: async () => [
        { id: 3, name: 'eval-gap 2026-09-25T01:11:31.562501174Z', systemName: '模拟系统', level: 3, status: 'REVIEW',
          lastError: null, version: 1, createdAt: '2026-09-25T01:11:31.6Z', updatedAt: '2026-09-25T01:20:00Z' },
      ],
    },
  }
})

describe('AssessmentList', () => {
  it('shows names and times in Beijing 24-hour time and the status in Chinese', async () => {
    const wrapper = mount(AssessmentList, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const item = wrapper.find('.item').text()
    expect(item).toContain('eval-gap 2026-09-25 09:11:31')
    expect(item).not.toContain('T01:11')
    expect(item).toContain('待复核')
    expect(item).toContain('创建于 2026-09-25 09:11')
  })
})
