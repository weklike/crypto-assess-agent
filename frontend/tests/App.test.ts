import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../src/App.vue'

vi.mock('../src/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/api/client')>()
  return {
    ...original,
    browseClauses: vi.fn(async () => ({ items: [], total: 0, page: 1, size: 10 })),
  }
})

vi.mock('../src/api/assessments', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/api/assessments')>()
  return { ...original, assessmentsApi: { ...original.assessmentsApi, list: async () => [] } }
})

const nav = (wrapper: ReturnType<typeof mount>) => wrapper.findAll('[data-testid="nav"] button')
const navButton = (wrapper: ReturnType<typeof mount>, label: string) => {
  const button = nav(wrapper).find((b) => b.text().includes(label))
  if (!button) throw new Error(`no nav button ${label}`)
  return button
}

describe('App shell', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/')
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })
  afterEach(() => {
    document.documentElement.classList.remove('dark')
  })

  it('shows the brand, the self-check notice and three pages, starting with Q&A', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('密评智能助手')
    expect(wrapper.text()).toContain('辅助自查')
    expect(nav(wrapper).map((b) => b.text())).toEqual(['条款问答', '检索调试', '评估项目'])
    expect(navButton(wrapper, '条款问答').attributes('aria-current')).toBe('page')
    expect(wrapper.find('[data-testid="page-qa"]').isVisible()).toBe(true)
    wrapper.unmount()
  })

  it('switches pages from the navigation and keeps the page in the address', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flushPromises()

    await navButton(wrapper, '检索调试').trigger('click')
    await flushPromises()
    expect(navButton(wrapper, '检索调试').attributes('aria-current')).toBe('page')
    expect(navButton(wrapper, '条款问答').attributes('aria-current')).toBeUndefined()
    expect(wrapper.find('[data-testid="page-search"]').isVisible()).toBe(true)
    expect(wrapper.find('[data-testid="page-qa"]').isVisible()).toBe(false)
    expect(window.location.hash).toBe('#/search')
    wrapper.unmount()
  })

  it('opens the page named in the address', async () => {
    window.history.replaceState(null, '', '/#/assessments')
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(navButton(wrapper, '评估项目').attributes('aria-current')).toBe('page')
    expect(wrapper.find('[data-testid="page-assessments"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('toggles dark mode and remembers the choice', async () => {
    const wrapper = mount(App, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const before = document.documentElement.classList.contains('dark')

    await wrapper.find('[data-testid="theme-toggle"]').trigger('click')
    expect(document.documentElement.classList.contains('dark')).toBe(!before)
    expect(localStorage.getItem('crypto-assess.theme')).toBe(before ? 'light' : 'dark')

    await wrapper.find('[data-testid="theme-toggle"]').trigger('click')
    expect(document.documentElement.classList.contains('dark')).toBe(before)
    wrapper.unmount()
  })
})
