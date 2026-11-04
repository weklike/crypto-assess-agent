import { describe, expect, it } from 'vitest'
import { applyTheme, initialTheme, saveTheme } from '../src/utils/theme'

function memoryStorage(initial: Record<string, string> = {}): Storage {
  const data = new Map(Object.entries(initial))
  return {
    get length() {
      return data.size
    },
    clear: () => data.clear(),
    getItem: (key: string) => data.get(key) ?? null,
    key: (index: number) => [...data.keys()][index] ?? null,
    removeItem: (key: string) => void data.delete(key),
    setItem: (key: string, value: string) => void data.set(key, value),
  }
}

const throwing: Storage = {
  length: 0,
  clear: () => {},
  getItem: () => {
    throw new Error('blocked')
  },
  key: () => null,
  removeItem: () => {},
  setItem: () => {
    throw new Error('blocked')
  },
}

describe('theme', () => {
  it('prefers the saved choice over the system setting', () => {
    expect(initialTheme(memoryStorage({ 'crypto-assess.theme': 'light' }), true)).toBe('light')
    expect(initialTheme(memoryStorage({ 'crypto-assess.theme': 'dark' }), false)).toBe('dark')
  })

  it('follows the system setting when nothing usable is saved', () => {
    expect(initialTheme(memoryStorage(), true)).toBe('dark')
    expect(initialTheme(memoryStorage(), false)).toBe('light')
    expect(initialTheme(memoryStorage({ 'crypto-assess.theme': 'purple' }), false)).toBe('light')
    expect(initialTheme(null, true)).toBe('dark')
  })

  it('treats unreadable storage as nothing saved', () => {
    expect(initialTheme(throwing, true)).toBe('dark')
  })

  it('switches the dark class and color scheme on the root element', () => {
    const root = document.createElement('html')
    applyTheme('dark', root)
    expect(root.classList.contains('dark')).toBe(true)
    expect(root.dataset.theme).toBe('dark')
    expect(root.style.colorScheme).toBe('dark')
    applyTheme('light', root)
    expect(root.classList.contains('dark')).toBe(false)
    expect(root.dataset.theme).toBe('light')
  })

  it('saves the choice and survives storage that refuses writes', () => {
    const storage = memoryStorage()
    saveTheme('dark', storage)
    expect(storage.getItem('crypto-assess.theme')).toBe('dark')
    expect(() => saveTheme('light', throwing)).not.toThrow()
    expect(() => saveTheme('light', null)).not.toThrow()
  })
})
