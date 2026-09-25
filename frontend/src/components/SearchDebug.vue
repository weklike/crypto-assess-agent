<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { browseClauses, search } from '../api/client'
import { LAYERS, type ClausePage, type SearchMode, type SearchResponse } from '../api/types'
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

/** 检索结果一次取回 k 条，页面上每页显示 10 条 */
const pagedHits = computed(() => {
  const hits = response.value?.hits ?? []
  return hits.slice((hitPage.value - 1) * PAGE_SIZE, hitPage.value * PAGE_SIZE)
})

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

function levelsText(levels: string | null): string {
  return levels ? levels.split(',').map((l) => `${l}级`).join(' ') : '—'
}
</script>

<template>
  <div>
    <el-form :inline="true" @submit.prevent="run">
      <el-form-item label="查询">
        <el-input v-model="form.query" placeholder="例如：数据库里的身份证号要加密吗" style="width: 360px" clearable />
      </el-form-item>
      <el-form-item label="模式">
        <el-select v-model="form.mode" style="width: 150px">
          <el-option v-for="m in modes" :key="m.value" :value="m.value" :label="m.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="层面">
        <el-select v-model="form.layer" clearable placeholder="不限" style="width: 130px">
          <el-option v-for="l in LAYERS" :key="l" :value="l" :label="l" />
        </el-select>
      </el-form-item>
      <el-form-item label="等级">
        <el-select v-model="form.level" clearable placeholder="不限" style="width: 100px">
          <el-option v-for="l in [1, 2, 3, 4]" :key="l" :value="l" :label="`第${l}级`" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label>
          <el-tooltip content="检索一次返回的结果总数（1–50），不是页码；结果在表格下方分页显示，每页 10 条">
            <span>返回条数（Top K）</span>
          </el-tooltip>
        </template>
        <el-input-number v-model="form.k" :min="1" :max="50" style="width: 100px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading">检索</el-button>
      </el-form-item>
    </el-form>

    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />

    <template v-if="response">
      <div class="timings">
        <el-tag v-for="(ms, key) in response.timings" :key="key" type="info" size="small">{{ key }} {{ ms }}</el-tag>
      </div>
      <el-table :data="pagedHits" stripe size="small" data-testid="hits">
        <el-table-column label="#" width="45">
          <template #default="{ $index }">{{ (hitPage - 1) * 10 + $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="条款" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" @click="openedRef = row.clauseRef">{{ row.clauseRef }}</el-link>
            <div class="title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="280">
          <template #default="{ row }"><span class="snippet">{{ row.snippet }}</span></template>
        </el-table-column>
        <el-table-column prop="layer" label="层面" width="100" />
        <el-table-column label="BM25 名次/分" width="115">
          <template #default="{ row }">{{ row.bm25Rank ?? '—' }} / {{ fmt(row.bm25Score, 2) }}</template>
        </el-table-column>
        <el-table-column label="向量 名次/分" width="115">
          <template #default="{ row }">{{ row.denseRank ?? '—' }} / {{ fmt(row.denseScore) }}</template>
        </el-table-column>
        <el-table-column label="RRF" width="85">
          <template #default="{ row }">{{ fmt(row.rrfScore) }}</template>
        </el-table-column>
        <el-table-column label="重排分" width="85">
          <template #default="{ row }">{{ fmt(row.rerankScore) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="hitPage" class="pager" layout="total, prev, pager, next" :page-size="10"
        :total="response.hits.length" data-testid="hits-pager" />
    </template>

    <template v-else-if="browse">
      <p class="hint">未输入查询语句，按原文顺序浏览知识库条款（可按层面、等级筛选）。输入查询后点“检索”查看检索结果。</p>
      <el-table :data="browse.items" stripe size="small" data-testid="browse" v-loading="loading">
        <el-table-column label="条款" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" @click="openedRef = row.clauseRef">{{ row.clauseRef }}</el-link>
            <div class="title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="正文" min-width="320">
          <template #default="{ row }"><span class="snippet">{{ row.body || '（章节标题，无正文）' }}</span></template>
        </el-table-column>
        <el-table-column label="层面" width="100">
          <template #default="{ row }">{{ row.layer ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="适用等级" width="130">
          <template #default="{ row }">{{ levelsText(row.levels) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="70">
          <template #default="{ row }">{{ row.clauseType ?? '—' }}</template>
        </el-table-column>
      </el-table>
      <el-pagination :current-page="browsePage" class="pager" layout="total, prev, pager, next" :page-size="10"
        :total="browse.total" data-testid="browse-pager" @current-change="loadBrowse" />
    </template>
    <ClauseDrawer :clause-ref="openedRef" @close="openedRef = null" />
  </div>
</template>

<style scoped>
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin: 0 0 8px;
}
.timings .el-tag {
  margin: 0 6px 10px 0;
}
.title {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.snippet {
  font-size: 12px;
}
</style>
