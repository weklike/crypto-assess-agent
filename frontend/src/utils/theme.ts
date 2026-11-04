export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'crypto-assess.theme'

/** localStorage 在隐私模式或被禁用时访问就会抛异常，统一当作不可用。 */
function defaultStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

export function systemPrefersDark(): boolean {
  try {
    return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches === true
  } catch {
    return false
  }
}

/** 用户手动选过就用它；没选过（或读不到）就跟随系统设置。 */
export function initialTheme(storage: Storage | null = defaultStorage(), prefersDark = systemPrefersDark()): Theme {
  try {
    const saved = storage?.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // 读不到按没选过处理
  }
  return prefersDark ? 'dark' : 'light'
}

/**
 * Element Plus 的暗色变量挂在 html.dark 上；color-scheme 让滚动条、原生输入框跟着切换。
 */
export function applyTheme(theme: Theme, root: HTMLElement = document.documentElement): void {
  root.classList.toggle('dark', theme === 'dark')
  root.dataset.theme = theme
  root.style.colorScheme = theme
}

/** 保存失败只影响下次打开时的默认值，不影响本次切换。 */
export function saveTheme(theme: Theme, storage: Storage | null = defaultStorage()): void {
  try {
    storage?.setItem(STORAGE_KEY, theme)
  } catch {
    // 忽略：本次会话内主题照常生效
  }
}
