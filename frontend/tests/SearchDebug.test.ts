import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SearchDebug from '../src/components/SearchDebug.vue'
import type { Clause, SearchHit } from '../src/api/types'

const clause = (n: number): Clause => ({
  clauseRef: `FIX/T 0001-2026#5.${n}`, docCode: 'FIX/T 0001-2026', clauseNo: `5.${n}`, path: '技术要求', title: `条款${n}`,
  body: `正文${n}`, layer: '应用和数据', levels: '1,2,3,4', clauseType: '要求',
})
const hit = (n: number): SearchHit => ({
  clauseRef: `FIX/T 0001-2026#6.${n}`, title: `命中${n}`, snippet: `摘要${n}`, layer: '管理制度', levels: '1,2,3,4',
  bm25Rank: n, bm25Score: 1, denseRank: n, denseScore: 0.5, rrfScore: 0.1, rerankScore: 0.2,
} as SearchHit)

const browseClauses = vi.fn()
const search = vi.fn()
vi.mock('../src/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/api/client')>()
  return { ...original, browseClauses: (...a: unknown[]) => browseClauses(...a), search: (...a: unknown[]) => search(...a) }
})

describe('SearchDebug', () => {
  beforeEach(() => {
    browseClauses.mockReset()
    search.mockReset()
    browseClauses.mockImplementation(async ({ page }: { page: number }) => ({
      items: [clause(page * 10 + 1), clause(page * 10 + 2)], total: 36, page, size: 10,
    }))
  })

  it('shows a default page of clauses when there is no query, with paging', async () => {
    const wrapper = mount(SearchDebug, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(browseClauses).toHaveBeenCalledWith({ page: 1, size: 10, layer: null, level: null })
    expect(wrapper.find('[data-testid="browse"]').text()).toContain('FIX/T 0001-2026#5.11')
    expect(wrapper.find('[data-testid="browse-pager"]').text()).toContain('36')

    await wrapper.find('[data-testid="browse-pager"] .btn-next').trigger('click')
    await flushPromises()
    expect(browseClauses).toHaveBeenLastCalledWith({ page: 2, size: 10, layer: null, level: null })
  })

  it('explains k as the number of results and pages the hits 10 at a time', async () => {
    search.mockResolvedValue({ mode: 'bm25', hits: Array.from({ length: 25 }, (_, i) => hit(i + 1)), timings: {} })
    const wrapper = mount(SearchDebug, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('返回条数')
    await wrapper.find('input[placeholder^="例如"]').setValue('密钥')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const rows = () => wrapper.findAll('[data-testid="hits"] tbody tr')
    expect(rows()).toHaveLength(10)
    expect(wrapper.find('[data-testid="hits-pager"]').text()).toContain('25')
    await wrapper.find('[data-testid="hits-pager"] .btn-next').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="hits"]').text()).toContain('FIX/T 0001-2026#6.11')
    expect(search).toHaveBeenCalledTimes(1)
  })
})
