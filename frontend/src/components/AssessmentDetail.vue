<script setup lang="ts">
import { Box, DocumentChecked, Download, Plus, RefreshRight, Select, TrendCharts, VideoPlay } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ApiError } from '../api/client'
import { assessmentsApi, JUDGMENTS, type AssessFinding, type AssessmentDetail, type CryptoMeasures, type Measure, type ReviewBody } from '../api/assessments'
import { judgmentTone, scoreWidth, statusTone } from '../utils/display'
import { filterFindings, isTechnicalLayer, needsParameter, RA_OPTIONS, rkOptions, summarize } from '../utils/findings'
import { pollUntil } from '../utils/polling'
import { statusText } from '../utils/status'
import { formatBeijing, formatTimesInText } from '../utils/time'
import ClauseDrawer from './ClauseDrawer.vue'
import ObjectForm from './ObjectForm.vue'
import ScorePanel from './ScorePanel.vue'
import WorkflowSteps from './WorkflowSteps.vue'

const props = defineProps<{ id: number }>()
const emit = defineEmits<{ changed: [] }>()

const detail = ref<AssessmentDetail | null>(null)
const error = ref('')
const busy = ref(false)
const showObjectForm = ref(false)
const openedRef = ref<string | null>(null)
const filter = reactive({ objectId: null as number | null, judgment: '', gapsOnly: false, unreviewedOnly: false })
let polling: AbortController | null = null

const status = computed(() => detail.value?.project.status)
const objectName = (id: number) => detail.value?.objects.find((o) => o.id === id)?.name ?? String(id)
const technical = (f: AssessFinding) => isTechnicalLayer(detail.value?.objects.find((o) => o.id === f.objectId)?.layer)
const shown = computed(() => (detail.value ? filterFindings(detail.value.findings, filter) : []))
const progress = computed(() => summarize(detail.value?.findings ?? []))
const total = computed(() => detail.value?.scores.find((s) => s.scope === 'total'))
const stepStats = computed(() => {
  const steps = detail.value?.steps ?? []
  return {
    total: steps.length,
    succeeded: steps.filter((s) => s.status === 'SUCCEEDED').length,
    failed: steps.filter((s) => s.status === 'FAILED').length,
  }
})
const gapCounts = computed(() => {
  const findings = detail.value?.findings ?? []
  return {
    fail: findings.filter((f) => f.judgment === '不符合').length,
    partial: findings.filter((f) => f.judgment === '部分符合').length,
  }
})
const layersCovered = computed(() => new Set(detail.value?.objects.map((o) => o.layer) ?? []).size)
const analyzeLabel = computed(() =>
  status.value === 'FAILED' ? '从失败步骤继续' : status.value === 'REVIEW' ? '重新分析' : '开始分析',
)

const CATEGORIES: { key: keyof CryptoMeasures; label: string }[] = [
  { key: 'transport', label: '传输' },
  { key: 'storage', label: '存储' },
  { key: 'auth', label: '身份鉴别' },
  { key: 'key_mgmt', label: '密钥管理' },
  { key: 'other', label: '其他' },
]
const measureText = (m: Measure) => [m.algorithm, m.protocol, m.product].filter(Boolean).join(' / ') || m.evidence || ''
const hasMeasures = (measures: CryptoMeasures) => CATEGORIES.some((c) => (measures[c.key]?.length ?? 0) > 0)

async function load() {
  try {
    detail.value = await assessmentsApi.detail(props.id)
    error.value = ''
    if (detail.value.project.status === 'ANALYZING') startPolling()
  } catch (e) {
    error.value = (e as Error).message
  }
}

function startPolling() {
  if (polling) return
  polling = new AbortController()
  pollUntil(() => assessmentsApi.detail(props.id), (d) => d.project.status !== 'ANALYZING', {
    intervalMs: 2000,
    signal: polling.signal,
    onUpdate: (d) => (detail.value = d),
  })
    .then(() => emit('changed'))
    .catch(() => undefined)
    .finally(() => (polling = null))
}

async function act(action: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof ApiError ? e.problem.detail || e.message : (e as Error).message
  } finally {
    busy.value = false
  }
}

const addObject = (body: Parameters<typeof assessmentsApi.addObject>[1]) =>
  act(async () => {
    await assessmentsApi.addObject(props.id, body)
    showObjectForm.value = false
  })
const analyze = () => act(() => assessmentsApi.analyze(props.id))
const confirm = () => act(() => assessmentsApi.confirm(props.id))
const review = (f: AssessFinding, body: ReviewBody) => act(() => assessmentsApi.review(props.id, f.id, body))

/** 技术层面改一个维度时把 D/A/K 与 Ra/Rk 一起提交，判定由后端按表 1 推出；满足的维度不带修正参数。 */
const DIMENSION_HELP =
  '技术层面按《商用密码应用安全性评估量化评估规则（2023 版）》表 1 逐项判断：' +
  'D 密码使用有效性（是否正确、有效地使用了密码技术）；A 密码算法/技术合规性（所用算法和技术是否符合国家要求）；' +
  'K 密钥管理安全（密钥全生命周期管理是否安全）。D 不满足为 0 分；A 不满足按算法安全强度取 Ra（1 / 0.5 / 0.2）；' +
  'K 不满足取 Rk（一般为 1，第三级、第四级满足相应密码模块等级时为 1.2 / 1.5）。管理层面不用这些维度。'

function missingInfo(f: AssessFinding): string {
  const list = f.missingInfoJson
  return Array.isArray(list) ? list.join('；') : ''
}

function reviewDims(f: AssessFinding, change: Partial<Record<'d' | 'a' | 'k', boolean>> & { ra?: number; rk?: number }) {
  const d = change.d ?? f.dimD ?? true
  const a = change.a ?? f.dimA ?? true
  const k = change.k ?? f.dimK ?? true
  return review(f, { d, a, k, ra: a ? null : (change.ra ?? f.ra), rk: k ? null : (change.rk ?? f.rk) })
}

async function download() {
  await act(async () => {
    const response = await fetch(assessmentsApi.reportUrl(props.id))
    if (!response.ok) {
      const problem = await response.json().catch(() => ({ status: response.status }))
      throw new ApiError(response.status, problem)
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `assessment-${props.id}-draft.docx`
    a.click()
    URL.revokeObjectURL(url)
  })
}

watch(() => props.id, load, { immediate: true })
onBeforeUnmount(() => polling?.abort())
</script>

<template>
  <div v-if="detail" class="detail">
    <section class="surface head">
      <div class="head-top">
        <div class="head-text">
          <h3>{{ formatTimesInText(detail.project.name) }} · {{ detail.project.systemName }}（第{{ detail.project.level }}级）</h3>
          <div class="head-meta">
            <span class="badge" :class="`tone-${statusTone(detail.project.status)}`" data-testid="status">{{ statusText(detail.project.status) }}</span>
            <span class="muted">创建 {{ formatBeijing(detail.project.createdAt) }} · 更新 {{ formatBeijing(detail.project.updatedAt) }}</span>
          </div>
        </div>
        <div class="head-actions">
          <el-button :icon="Plus" :disabled="!['DRAFT', 'READY', 'REVIEW', 'FAILED'].includes(status!)" @click="showObjectForm = true">添加对象</el-button>
          <el-button type="primary" :icon="status === 'REVIEW' ? RefreshRight : VideoPlay" :loading="busy || status === 'ANALYZING'"
            :disabled="!['READY', 'REVIEW', 'FAILED'].includes(status!)" data-testid="analyze" @click="analyze">
            {{ analyzeLabel }}
          </el-button>
        </div>
      </div>
      <WorkflowSteps :status="detail.project.status" class="steps" />
    </section>

    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <el-alert v-if="status === 'FAILED'" type="warning" :closable="false" show-icon
      :title="`分析失败：${detail.project.lastError ?? ''}`" description="已成功的步骤会保留，重新分析只执行剩余步骤。" />

    <div class="kpi-row">
      <div class="kpi">
        <div class="kpi-label">测评对象</div>
        <div class="kpi-value">{{ detail.objects.length }}<small>个</small></div>
        <div class="kpi-foot">覆盖 {{ layersCovered }} 个安全层面</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">复核进度</div>
        <div class="kpi-value">{{ progress.reviewed }}<small>/ {{ progress.total }}</small></div>
        <div class="meter kpi-meter" :title="`已复核 ${progress.reviewed} / ${progress.total}`">
          <span class="fill" :style="{ width: scoreWidth(progress.reviewed, progress.total || 1) }" />
        </div>
      </div>
      <div class="kpi">
        <div class="kpi-label">差距项</div>
        <div class="kpi-value">{{ progress.gaps }}<small>条</small></div>
        <div class="kpi-foot">不符合 {{ gapCounts.fail }} · 部分符合 {{ gapCounts.partial }}</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">分析步骤</div>
        <div class="kpi-value">{{ stepStats.succeeded }}<small>/ {{ stepStats.total }} 成功</small></div>
        <div class="kpi-foot" :class="{ danger: stepStats.failed > 0 }">失败 {{ stepStats.failed }}</div>
      </div>
    </div>

    <section class="surface">
      <header class="section-head">
        <div class="section-title"><el-icon><Box /></el-icon>测评对象</div>
        <span class="muted">{{ detail.objects.length }} 个</span>
      </header>
      <el-table :data="detail.objects" class="table" empty-text="还没有测评对象，点右上角“添加对象”录入">
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }">
            <div class="obj-name">{{ row.name }}</div>
            <div v-if="row.description" class="obj-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="安全层面" width="120">
          <template #default="{ row }"><span class="badge plain">{{ row.layer }}</span></template>
        </el-table-column>
        <el-table-column label="密码措施" min-width="380">
          <template #default="{ row }">
            <div class="measures">
              <template v-for="c in CATEGORIES" :key="c.key">
                <div v-if="row.measures[c.key]?.length" class="measure-group">
                  <span class="measure-cat">{{ c.label }}</span>
                  <span v-for="(m, i) in row.measures[c.key]" :key="i" class="measure-chip">{{ measureText(m) }}</span>
                </div>
              </template>
              <span v-if="!hasMeasures(row.measures)" class="muted">未填写</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="detail.findings.length" class="surface">
      <header class="section-head">
        <div class="section-title"><el-icon><DocumentChecked /></el-icon>差距项复核</div>
        <span class="muted">已复核 {{ progress.reviewed }}/{{ progress.total }}，差距 {{ progress.gaps }} 条</span>
        <span class="spacer" />
        <el-button type="success" :icon="Select" :disabled="status !== 'REVIEW' || progress.reviewed < progress.total"
          data-testid="confirm" @click="confirm">全部确认并评分</el-button>
      </header>
      <div class="filters">
        <el-select v-model="filter.objectId" clearable placeholder="全部对象" style="width: 170px">
          <el-option v-for="o in detail.objects" :key="o.id" :value="o.id" :label="o.name" />
        </el-select>
        <el-select v-model="filter.judgment" clearable placeholder="全部判定" style="width: 130px">
          <el-option v-for="j in JUDGMENTS" :key="j" :value="j" :label="j" />
        </el-select>
        <el-checkbox v-model="filter.gapsOnly">只看差距</el-checkbox>
        <el-checkbox v-model="filter.unreviewedOnly">只看未复核</el-checkbox>
        <span class="spacer" />
        <span class="muted">共 {{ shown.length }} 条</span>
      </div>
      <el-table :data="shown" data-testid="findings" row-key="id" class="table findings">
        <el-table-column type="expand" width="36">
          <template #default="{ row }">
            <el-descriptions :column="1" border size="small" class="expand">
              <el-descriptions-item label="证据">{{ row.evidence || '—' }}</el-descriptions-item>
              <el-descriptions-item label="理由">{{ row.rationale || '—' }}</el-descriptions-item>
              <el-descriptions-item label="缺少的信息">{{ missingInfo(row) || '—' }}</el-descriptions-item>
              <el-descriptions-item label="整改建议">{{ row.remediation || '—' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-table-column>
        <el-table-column label="条款 / 对象" width="190">
          <template #default="{ row }">
            <el-link type="primary" class="mono ref" @click="openedRef = row.clauseRef">{{ row.clauseRef }}</el-link>
            <div class="obj-of">{{ objectName(row.objectId) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="判定" width="130">
          <template #default="{ row }">
            <el-select :model-value="row.judgment" size="small" :disabled="status !== 'REVIEW'"
              :class="`judgment tone-${judgmentTone(row.judgment)}`" @change="(v: string) => review(row, { judgment: v })">
              <template #prefix><span class="judgment-dot" /></template>
              <!-- 技术层面的“部分符合”要说明是 A 还是 K 不满足，请在测评维度列修改 -->
              <el-option v-for="j in JUDGMENTS" :key="j" :value="j" :label="j" :disabled="technical(row) && j === '部分符合'" />
            </el-select>
            <div class="sources">
              <span v-if="row.source === 'RULE'" class="badge tone-critical">规则改判</span>
              <span v-else-if="row.source === 'REVIEWER'" class="badge tone-warning">人工修改</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column width="146">
          <template #header>
            <el-tooltip placement="top" :content="DIMENSION_HELP">
              <span class="help">测评维度 ⓘ</span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <div v-if="technical(row) && row.judgment !== '不适用'" :data-testid="`dims-${row.id}`" class="dims">
              <el-checkbox :model-value="row.dimD ?? false" :disabled="status !== 'REVIEW'" :data-testid="`dim-d-${row.id}`"
                title="D：密码使用有效性" @change="(v: boolean) => reviewDims(row, { d: v })">D 有效性</el-checkbox>
              <template v-if="row.dimD">
                <el-checkbox :model-value="row.dimA ?? false" :disabled="status !== 'REVIEW'" :data-testid="`dim-a-${row.id}`"
                  title="A：密码算法/技术合规性" @change="(v: boolean) => reviewDims(row, { a: v })">A 算法合规</el-checkbox>
                <el-checkbox :model-value="row.dimK ?? false" :disabled="status !== 'REVIEW'" :data-testid="`dim-k-${row.id}`"
                  title="K：密钥管理安全" @change="(v: boolean) => reviewDims(row, { k: v })">K 密钥管理</el-checkbox>
                <el-select v-if="row.dimA === false" :model-value="row.ra" size="small" placeholder="Ra" class="param"
                  :disabled="status !== 'REVIEW'" @change="(v: number) => reviewDims(row, { ra: v })">
                  <el-option v-for="r in RA_OPTIONS" :key="r" :value="r" :label="`Ra=${r}`" />
                </el-select>
                <el-select v-if="row.dimK === false" :model-value="row.rk" size="small" placeholder="Rk" class="param"
                  :disabled="status !== 'REVIEW'" @change="(v: number) => reviewDims(row, { rk: v })">
                  <el-option v-for="r in rkOptions(detail.project.level)" :key="r" :value="r" :label="`Rk=${r}`" />
                </el-select>
              </template>
              <span v-if="needsParameter(row)" class="badge tone-warning" :data-testid="`missing-${row.id}`">
                待填 {{ needsParameter(row) }}
              </span>
            </div>
            <span v-else class="muted">管理层面直接判定</span>
          </template>
        </el-table-column>
        <el-table-column label="证据与理由" min-width="230">
          <template #default="{ row }">
            <div class="text clamp" :title="row.evidence ?? ''">{{ row.evidence }}</div>
            <div class="text clamp secondary" :title="row.rationale ?? ''">{{ row.rationale }}</div>
          </template>
        </el-table-column>
        <el-table-column label="整改建议" min-width="180">
          <template #default="{ row }">
            <div class="text clamp" :title="row.remediation ?? ''">{{ row.remediation || '—' }}</div>
          </template>
        </el-table-column>
        <!-- 窄屏时表格横向滚动，复核操作固定在右侧始终可见 -->
        <el-table-column label="复核" width="156" fixed="right">
          <template #default="{ row }">
            <el-input :model-value="row.reviewerNote ?? ''" size="small" placeholder="备注" :disabled="status !== 'REVIEW'"
              @change="(v: string) => review(row, { note: v })" />
            <el-checkbox :model-value="row.reviewed" :disabled="status !== 'REVIEW'" class="reviewed"
              @change="(v: boolean) => review(row, { reviewed: v })">已确认</el-checkbox>
          </template>
        </el-table-column>
      </el-table>
      <p class="table-hint">点行首箭头展开查看完整的证据、理由、缺少的信息和整改建议。</p>
    </section>

    <section v-if="total" class="surface">
      <header class="section-head">
        <div class="section-title"><el-icon><TrendCharts /></el-icon>量化评分</div>
        <span class="badge plain mono">{{ total.ruleVersion }}</span>
        <span class="spacer" />
        <el-button type="primary" :icon="Download" :disabled="!['SCORED', 'REPORTED'].includes(status!)" data-testid="report" @click="download">
          下载报告草稿
        </el-button>
      </header>
      <div class="section-body">
        <ScorePanel :scores="detail.scores" />
      </div>
    </section>

    <el-dialog v-model="showObjectForm" title="添加测评对象" width="920px" destroy-on-close>
      <ObjectForm @submit="addObject" @cancel="showObjectForm = false" />
    </el-dialog>
    <ClauseDrawer :clause-ref="openedRef" @close="openedRef = null" />
  </div>
  <el-alert v-else-if="error" type="error" :title="error" :closable="false" />
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.head {
  padding: 20px 20px 18px;
}

.head-top {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.head-text {
  flex: 1;
  min-width: 260px;
}

.head h3 {
  font-size: 19px;
  font-weight: 650;
  line-height: 1.4;
}

.head-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 8px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.steps {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--app-border);
}

.kpi-meter {
  margin-top: 12px;
}

.kpi-foot.danger {
  color: var(--el-color-danger);
}

.table {
  width: 100%;
}

.obj-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.obj-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.measures {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.measure-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.measure-cat {
  min-width: 56px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.measure-chip {
  padding: 1px 8px;
  border-radius: 6px;
  border: 1px solid var(--app-border);
  background: var(--app-surface-muted);
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface-muted);
}

.ref {
  white-space: nowrap;
}

.obj-of {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 判定下拉框左侧的色点：颜色只是辅助，判定文字始终可见 */
.judgment-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tone-neutral-dot);
}

.judgment.tone-good .judgment-dot {
  background: var(--tone-good-dot);
}
.judgment.tone-warning .judgment-dot {
  background: var(--tone-warning-dot);
}
.judgment.tone-critical .judgment-dot {
  background: var(--tone-critical-dot);
}

.sources {
  margin-top: 6px;
}

.help {
  cursor: help;
  border-bottom: 1px dashed var(--el-text-color-secondary);
}

.dims {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.dims .el-checkbox {
  height: 22px;
}

.param {
  width: 96px;
  margin-top: 2px;
}

.text {
  white-space: pre-wrap;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
}

.text.secondary {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.reviewed {
  margin-top: 4px;
}

.expand {
  margin: 8px 16px 8px 52px;
}

.expand :deep(.el-descriptions__content) {
  white-space: pre-wrap;
}

.expand :deep(.el-descriptions__label) {
  width: 96px;
}

.table-hint {
  margin: 0;
  padding: 10px 20px 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
