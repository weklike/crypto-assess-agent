<script setup lang="ts">
import { ChatDotRound, Moon, Search, Sunny, Tickets } from '@element-plus/icons-vue'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import AssessmentList from './components/AssessmentList.vue'
import QaChat from './components/QaChat.vue'
import SearchDebug from './components/SearchDebug.vue'
import { applyTheme, saveTheme, type Theme } from './utils/theme'

type Page = 'qa' | 'search' | 'assessments'

const PAGES: { key: Page; label: string; hash: string; icon: typeof Search }[] = [
  { key: 'qa', label: '条款问答', hash: '#/qa', icon: ChatDotRound },
  { key: 'search', label: '检索调试', hash: '#/search', icon: Search },
  { key: 'assessments', label: '评估项目', hash: '#/assessments', icon: Tickets },
]

function pageFromHash(hash: string): Page {
  return PAGES.find((p) => p.hash === hash)?.key ?? 'qa'
}

const page = ref<Page>(pageFromHash(window.location.hash))
// 页面切换用 v-show 保留各页状态（问答结果、检索条件）；评估项目页第一次打开时才挂载
const visited = reactive<Record<Page, boolean>>({ qa: true, search: true, assessments: page.value === 'assessments' })

function go(key: Page, push = true) {
  page.value = key
  visited[key] = true
  const hash = PAGES.find((p) => p.key === key)!.hash
  if (push && window.location.hash !== hash) window.history.pushState(null, '', hash)
}

// 浏览器前进、后退时同步页面
const onLocationChange = () => go(pageFromHash(window.location.hash), false)
onMounted(() => {
  window.addEventListener('hashchange', onLocationChange)
  window.addEventListener('popstate', onLocationChange)
})
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', onLocationChange)
  window.removeEventListener('popstate', onLocationChange)
})

// 当前主题以 html 上的 dark 类为准（main.ts 在挂载前已按保存的选择或系统设置加上）
const theme = ref<Theme>(document.documentElement.classList.contains('dark') ? 'dark' : 'light')
function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  applyTheme(theme.value)
  saveTheme(theme.value)
}
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="topbar-inner">
        <a class="brand" href="#/qa" @click.prevent="go('qa')">
          <span class="logo" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3l7 3v5.4c0 4.3-2.9 8-7 9.1-4.1-1.1-7-4.8-7-9.1V6l7-3z" />
              <circle cx="12" cy="10.6" r="1.7" />
              <path d="M12 12.3v2.9" />
            </svg>
          </span>
          <span class="brand-text">
            <strong>密评智能助手</strong>
            <small>商用密码应用安全性评估 · 辅助自查</small>
          </span>
        </a>

        <nav class="nav" data-testid="nav" aria-label="主导航">
          <button
            v-for="p in PAGES"
            :key="p.key"
            type="button"
            class="nav-item"
            :class="{ active: page === p.key }"
            :aria-current="page === p.key ? 'page' : undefined"
            :aria-label="p.label"
            :title="p.label"
            @click="go(p.key)"
          >
            <el-icon aria-hidden="true"><component :is="p.icon" /></el-icon>
            <span>{{ p.label }}</span>
          </button>
        </nav>

        <span class="spacer" />
        <span class="notice-pill" title="页面中的回答、判定和得分都是辅助自查草稿，不能替代商用密码检测机构出具的正式测评报告">
          辅助自查草稿 · 需测评人员确认
        </span>
        <el-tooltip :content="theme === 'dark' ? '切换到亮色' : '切换到暗色'" placement="bottom">
          <button
            type="button"
            class="icon-btn"
            data-testid="theme-toggle"
            :aria-label="theme === 'dark' ? '切换到亮色' : '切换到暗色'"
            @click="toggleTheme"
          >
            <el-icon aria-hidden="true"><Sunny v-if="theme === 'dark'" /><Moon v-else /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </header>

    <main class="main">
      <QaChat v-show="page === 'qa'" data-testid="page-qa" />
      <SearchDebug v-show="page === 'search'" data-testid="page-search" />
      <AssessmentList v-if="visited.assessments" v-show="page === 'assessments'" data-testid="page-assessments" />
    </main>

    <footer class="footer">
      密评智能助手 · 页面中的回答、判定和得分均为辅助自查草稿，需测评人员确认，不构成正式测评结论
    </footer>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: var(--app-header-bg);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--app-border);
}

.topbar-inner {
  max-width: 1440px;
  margin: 0 auto;
  height: 60px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
  flex: none;
}

.logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, var(--app-brand-from), var(--app-brand-to));
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.28);
}

.logo svg {
  width: 20px;
  height: 20px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-text strong {
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.brand-text small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.nav {
  display: flex;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--app-border);
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}

.nav-item:hover {
  color: var(--el-text-color-primary);
  background: var(--el-fill-color);
}

.nav-item.active {
  color: var(--el-color-primary);
  background: var(--app-surface);
  font-weight: 600;
  box-shadow: var(--app-shadow);
}

.notice-pill {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--tone-warning-fg);
  background: var(--tone-warning-bg);
  white-space: nowrap;
}

.icon-btn {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  border: 1px solid var(--app-border);
  background: var(--app-surface);
  color: var(--el-text-color-regular);
  cursor: pointer;
  font-size: 16px;
  transition: color 0.15s, border-color 0.15s;
}

.icon-btn:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

.main {
  flex: 1;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px;
}

.footer {
  padding: 16px 24px 28px;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1100px) {
  .notice-pill {
    display: none;
  }
}

@media (max-width: 860px) {
  .topbar-inner {
    gap: 12px;
    padding: 0 16px;
  }
  .brand-text small,
  .nav-item span {
    display: none;
  }
  .main {
    padding: 16px;
  }
}
</style>
