<script setup lang="ts">
import { Collection, List, QuestionFilled, Search } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { browseClauses, search } from '../api/client'
import { LAYERS, type ClausePage, type SearchMode, type SearchResponse } from '../api/types'
import { formatDuration, scoreWidth, timingItems } from '../utils/display'
import ClauseDrawer from './ClauseDrawer.vue'

const PAGE_SIZE = 10

const form = reactive({
  query: '',
  mode: 'hybrid_rerank' as SearchMode,
  k: 10,
  layer: '' as string,
  level: null as number | null,
})
const response = ref<SearchResponse | null>(null)
const hitPage = ref(1)
const browse = ref<ClausePage | null>(null)
const browsePage = ref(1)
const error = ref('')
const loading = ref(false)
const openedRef = ref<string | null>(null)

const modes: { value: SearchMode; label: string }[] = [
  { value: 'bm25', label: 'BM25' },
  { value: 'dense', label: '向量' },
  { value: 'hybrid', label: '混合（RRF）' },
  { value: 'hybrid_rerank', label: '混合 + 重排' },
]
const modeLabel = (m: string) => modes.find((x) => x.value === m)?.label ?? m

/** 检索结果一次取回 k 条，页面上每页显示 10 条 */
const pagedHits = computed(() => {
  const hits = response.value?.hits ?? []
  return hits.slice((hitPage.value - 1) * PAGE_SIZE, hitPage.value * PAGE_SIZE)
})
const timings = computed(() => timingItems(response.value?.timings))

async function loadBrowse(page = 1) {
  loading.value = true
  error.value = ''
  try {
    browse.value = await browseClauses({ page, size: PAGE_SIZE, layer: form.layer || null, level: form.level })
    browsePage.value = page
  } catch (e) {
    error.value = (e as Error).message
    browse.value = null
  } finally {
    loading.value = false
  }
}

async function run() {
  if (!form.query.trim()) {
    // 没有查询语句时显示默认的条款列表
    response.value = null
    await loadBrowse(1)
    return
  }
  loading.value = true
  error.value = ''
  try {
    response.value = await search({
      query: form.query.trim(),
      mode: form.mode,
      k: form.k,
      layer: form.layer || null,
      level: form.level,
    })
    hitPage.value = 1
  } catch (e) {
    error.value = (e as Error).message
    response.value = null
  } finally {
    loading.value = false
  }
}

// 浏览模式下改层面、等级立即刷新；清空查询回到浏览
watch(
  () => [form.layer, form.level],
  () => {
    if (!response.value) loadBrowse(1)
  },
)
watch(
  () => form.query,
  (q) => {
    if (!q.trim() && response.value) {
      response.value = null
      loadBrowse(1)
    }
  },
)

onMounted(() => loadBrowse(1))

function fmt(value: number | null, digits = 4): string {
  return value === null || value === undefined ? '—' : value.toFixed(digits)
}

function levels(value: string | null): string[] {
  return value ? value.split(',') : []
}
</script>

<template>
  <div class="search-page">
    <form class="surface toolbar" @submit.prevent="run">
      <div class="query-row">
        <el-input
          v-model="form.query"
          size="large"
          :prefix-icon="Search"
          placeholder="例如：数据库里的身份证号要加密吗"
          clearable
          class="query"
        />
        <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">检索</el-button>
      </div>
      <div class="filters">
        <div class="field">
          <span class="field-label">模式</span>
          <el-segmented v-model="form.mode" :options="modes" size="default" />
        </div>
        <div class="field">
          <span class="field-label">层面</span>
          <el-select v-model="form.layer" clearable placeholder="不限" style="width: 140px">
            <el-option v-for="l in LAYERS" :key="l" :value="l" :label="l" />
          </el-select>
        </div>
        <div class="field">
          <span class="field-label">等级</span>
          <el-select v-model="form.level" clearable placeholder="不限" style="width: 110px">
            <el-option v-for="l in [1, 2, 3, 4]" :key="l" :value="l" :label="`第${l}级`" />
          </el-select>
        </div>
        <div class="field">
          <el-tooltip content="检索一次返回的结果总数（1–50），不是页码；结果在表格下方分页显示，每页 10 条">
            <span class="field-label help">返回条数（Top K）<el-icon><QuestionFilled /></el-icon></span>
          </el-tooltip>
          <el-input-number v-model="form.k" :min="1" :max="50" controls-position="right" style="width: 110px" />
        </div>
      </div>
    </form>

    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon class="block" />

    <section v-if="response" class="surface results">
      <header class="section-head wrap">
        <div class="section-title"><el-icon><List /></el-icon>检索结果</div>
        <span class="muted">共 {{ response.hits.length }} 条 · {{ modeLabel(response.mode) }}</span>
        <span class="spacer" />
        <div class="timings" aria-label="各阶段耗时">
          <span v-for="t in timings" :key="t.key" class="timing" :class="{ slow: t.slowest }">
            {{ t.label }} <strong class="num">{{ t.text }}</strong><em v-if="t.slowest">最慢</em>
          </span>
          <span v-if="response.timings.totalMs !== undefined" class="timing total">
            总计 <strong class="num">{{ formatDuration(response.timings.totalMs) }}</strong>
          </span>
        </div>
      </header>
      <el-table :data="pagedHits" data-testid="hits" class="table">
        <el-table-column label="#" width="56" align="center">
          <template #default="{ $index }">
            <span class="rank num">{{ (hitPage - 1) * 10 + $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="条款" min-width="230">
          <template #default="{ row }">
            <el-link type="primary" class="mono" @click="openedRef = row.clauseRef">{{ row.clauseRef }}</el-link>
            <div class="title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="260">
          <template #default="{ row }"><span class="snippet">{{ row.snippet }}</span></template>
        </el-table-column>
        <el-table-column label="层面" width="112">
          <template #default="{ row }"><span v-if="row.layer" class="badge plain">{{ row.layer }}</span><span v-else class="muted">—</span></template>
        </el-table-column>
        <el-table-column label="BM25 名次 / 分" width="122">
          <template #default="{ row }">
            <span class="rk num" :class="{ none: row.bm25Rank == null }">{{ row.bm25Rank ?? '—' }}</span>
            <span class="muted num">{{ fmt(row.bm25Score, 2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="向量 名次 / 分" width="122">
          <template #default="{ row }">
            <span class="rk num" :class="{ none: row.denseRank == null }">{{ row.denseRank ?? '—' }}</span>
            <span class="muted num">{{ fmt(row.denseScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="RRF" width="84">
          <template #default="{ row }"><span class="num">{{ fmt(row.rrfScore) }}</span></template>
        </el-table-column>
        <el-table-column label="重排分" width="150">
          <template #default="{ row }">
            <div v-if="row.rerankScore !== null" class="score-cell" :title="`重排分 ${row.rerankScore}`">
              <span class="meter"><span class="fill" :style="{ width: scoreWidth(row.rerankScore) }" /></span>
              <span class="num">{{ fmt(row.rerankScore) }}</span>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination v-model:current-page="hitPage" layout="total, prev, pager, next" :page-size="10"
          :total="response.hits.length" data-testid="hits-pager" />
      </div>
    </section>

    <section v-else-if="browse" class="surface results">
      <header class="section-head wrap">
        <div class="section-title"><el-icon><Collection /></el-icon>知识库条款</div>
        <span class="muted">未输入查询语句，按原文顺序浏览知识库条款（可按层面、等级筛选）。输入查询后点“检索”查看检索结果。</span>
      </header>
      <el-table :data="browse.items" data-testid="browse" v-loading="loading" class="table">
        <el-table-column label="条款" min-width="230">
          <template #default="{ row }">
            <el-link type="primary" class="mono" @click="openedRef = row.clauseRef">{{ row.clauseRef }}</el-link>
            <div class="title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="正文" min-width="340">
          <template #default="{ row }"><span class="snippet clamp">{{ row.body || '（章节标题，无正文）' }}</span></template>
        </el-table-column>
        <el-table-column label="层面" width="112">
          <template #default="{ row }"><span v-if="row.layer" class="badge plain">{{ row.layer }}</span><span v-else class="muted">—</span></template>
        </el-table-column>
        <el-table-column label="适用等级" width="190">
          <template #default="{ row }">
            <span v-if="!levels(row.levels).length" class="muted">—</span>
            <span v-for="l in levels(row.levels)" :key="l" class="level">{{ l }}级</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }"><span v-if="row.clauseType" class="badge plain">{{ row.clauseType }}</span><span v-else class="muted">—</span></template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination :current-page="browsePage" layout="total, prev, pager, next" :page-size="10"
          :total="browse.total" data-testid="browse-pager" @current-change="loadBrowse" />
      </div>
    </section>
    <ClauseDrawer :clause-ref="openedRef" @close="openedRef = null" />
  </div>
</template>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.query-row {
  display: flex;
  gap: 10px;
}

.query {
  flex: 1;
}

.submit {
  min-width: 104px;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
}

.field {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.field-label.help {
  cursor: help;
}

.block {
  margin: 0;
}

.results {
  overflow: hidden;
}

.section-head.wrap {
  flex-wrap: wrap;
}

.table :deep(.el-table__cell) {
  padding: 10px 0;
}

.title {
  margin-top: 2px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.snippet {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rank {
  display: inline-grid;
  place-items: center;
  min-width: 24px;
  height: 24px;
  padding: 0 4px;
  border-radius: 7px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-weight: 600;
}

.rk {
  display: inline-block;
  min-width: 22px;
  margin-right: 6px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rk.none {
  color: var(--el-text-color-placeholder);
  font-weight: 400;
}

.score-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-cell .meter {
  flex: 1;
}

.level {
  display: inline-block;
  margin: 0 4px 4px 0;
  padding: 0 6px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 20px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
}

.timings {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.timing {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.timing strong {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.timing.slow {
  background: var(--tone-warning-bg);
  color: var(--tone-warning-fg);
}

.timing.slow strong {
  color: var(--tone-warning-fg);
}

.timing.total {
  background: transparent;
  border: 1px solid var(--app-border);
}

.timing em {
  font-style: normal;
  font-size: 11px;
}

.pager-row {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--app-border);
}
</style>
