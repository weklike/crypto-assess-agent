<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ApiError } from '../api/client'
import { assessmentsApi, JUDGMENTS, type AssessFinding, type AssessmentDetail, type ReviewBody } from '../api/assessments'
import { filterFindings, isTechnicalLayer, needsParameter, RA_OPTIONS, rkOptions, summarize } from '../utils/findings'
import { pollUntil } from '../utils/polling'
import ClauseDrawer from './ClauseDrawer.vue'
import ObjectForm from './ObjectForm.vue'
import { statusText } from '../utils/status'
import { formatBeijing, formatTimesInText } from '../utils/time'

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
const layerScores = computed(() => detail.value?.scores.filter((s) => s.scope === 'layer') ?? [])
const groupScores = computed(() => detail.value?.scores.filter((s) => s.scope === 'group') ?? [])
const totalNote = computed(() => total.value?.detailJson?.note ?? '全部不适用')
const stepStats = computed(() => {
  const steps = detail.value?.steps ?? []
  return {
    total: steps.length,
    succeeded: steps.filter((s) => s.status === 'SUCCEEDED').length,
    failed: steps.filter((s) => s.status === 'FAILED').length,
  }
})

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
  <div v-if="detail">
    <div class="head">
      <h3>{{ formatTimesInText(detail.project.name) }} · {{ detail.project.systemName }}（第{{ detail.project.level }}级）</h3>
      <el-tag data-testid="status">{{ statusText(detail.project.status) }}</el-tag>
      <span class="muted">创建 {{ formatBeijing(detail.project.createdAt) }} · 更新 {{ formatBeijing(detail.project.updatedAt) }}</span>
    </div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon class="block" />
    <el-alert v-if="status === 'FAILED'" type="warning" :closable="false" show-icon class="block"
      :title="`分析失败：${detail.project.lastError ?? ''}`" description="已成功的步骤会保留，重新分析只执行剩余步骤。" />

    <el-card shadow="never" class="block">
      <template #header>
        测评对象（{{ detail.objects.length }}）
        <el-button size="small" :disabled="!['DRAFT', 'READY', 'REVIEW', 'FAILED'].includes(status!)" @click="showObjectForm = true">添加对象</el-button>
        <el-button size="small" type="primary" :loading="busy || status === 'ANALYZING'"
          :disabled="!['READY', 'REVIEW', 'FAILED'].includes(status!)" data-testid="analyze" @click="analyze">
          {{ status === 'FAILED' ? '从失败步骤继续' : status === 'REVIEW' ? '重新分析' : '开始分析' }}
        </el-button>
        <span v-if="stepStats.total" class="muted">步骤 {{ stepStats.succeeded }}/{{ stepStats.total }} 成功，{{ stepStats.failed }} 失败</span>
      </template>
      <el-table :data="detail.objects" size="small">
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="layer" label="安全层面" width="120" />
        <el-table-column label="密码措施">
          <template #default="{ row }">
            <span v-for="(list, key) in row.measures" :key="key">
              <el-tag v-for="(m, i) in list" :key="i" size="small" effect="plain" class="measure-tag">
                {{ [m.algorithm, m.protocol, m.product].filter(Boolean).join(' / ') || m.evidence }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="detail.findings.length" shadow="never" class="block">
      <template #header>
        差距项：已复核 {{ progress.reviewed }}/{{ progress.total }}，差距 {{ progress.gaps }} 条
        <el-button size="small" type="success" :disabled="status !== 'REVIEW' || progress.reviewed < progress.total"
          data-testid="confirm" @click="confirm">全部确认并评分</el-button>
      </template>
      <div class="filters">
        <el-select v-model="filter.objectId" clearable placeholder="全部对象" size="small" style="width: 160px">
          <el-option v-for="o in detail.objects" :key="o.id" :value="o.id" :label="o.name" />
        </el-select>
        <el-select v-model="filter.judgment" clearable placeholder="全部判定" size="small" style="width: 120px">
          <el-option v-for="j in JUDGMENTS" :key="j" :value="j" :label="j" />
        </el-select>
        <el-checkbox v-model="filter.gapsOnly">只看差距</el-checkbox>
        <el-checkbox v-model="filter.unreviewedOnly">只看未复核</el-checkbox>
      </div>
      <el-table :data="shown" size="small" data-testid="findings" row-key="id">
        <el-table-column type="expand" width="32">
          <template #default="{ row }">
            <el-descriptions :column="1" border size="small" class="expand">
              <el-descriptions-item label="证据">{{ row.evidence || '—' }}</el-descriptions-item>
              <el-descriptions-item label="理由">{{ row.rationale || '—' }}</el-descriptions-item>
              <el-descriptions-item label="缺少的信息">{{ missingInfo(row) || '—' }}</el-descriptions-item>
              <el-descriptions-item label="整改建议">{{ row.remediation || '—' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-table-column>
        <el-table-column label="对象" width="100">
          <template #default="{ row }">{{ objectName(row.objectId) }}</template>
        </el-table-column>
        <el-table-column label="条款" width="165">
          <template #default="{ row }"><el-link type="primary" @click="openedRef = row.clauseRef">{{ row.clauseRef }}</el-link></template>
        </el-table-column>
        <el-table-column label="判定" width="120">
          <template #default="{ row }">
            <el-select :model-value="row.judgment" size="small" :disabled="status !== 'REVIEW'"
              @change="(v: string) => review(row, { judgment: v })">
              <!-- 技术层面的“部分符合”要说明是 A 还是 K 不满足，请在测评维度列修改 -->
              <el-option v-for="j in JUDGMENTS" :key="j" :value="j" :label="j" :disabled="technical(row) && j === '部分符合'" />
            </el-select>
            <el-tag v-if="row.source === 'RULE'" size="small" type="danger">规则改判</el-tag>
            <el-tag v-else-if="row.source === 'REVIEWER'" size="small" type="warning">人工修改</el-tag>
          </template>
        </el-table-column>
        <el-table-column width="150">
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
              <el-tag v-if="needsParameter(row)" size="small" type="warning" :data-testid="`missing-${row.id}`">
                待填 {{ needsParameter(row) }}
              </el-tag>
            </div>
            <span v-else class="muted">管理层面直接判定</span>
          </template>
        </el-table-column>
        <el-table-column label="证据与理由" min-width="280">
          <template #default="{ row }">
            <div class="text clamp" :title="row.evidence ?? ''">{{ row.evidence }}</div>
            <div class="text clamp muted-text" :title="row.rationale ?? ''">{{ row.rationale }}</div>
          </template>
        </el-table-column>
        <el-table-column label="整改建议" min-width="240">
          <template #default="{ row }">
            <div class="text clamp" :title="row.remediation ?? ''">{{ row.remediation || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="复核" width="170">
          <template #default="{ row }">
            <el-input :model-value="row.reviewerNote ?? ''" size="small" placeholder="备注" :disabled="status !== 'REVIEW'"
              @change="(v: string) => review(row, { note: v })" />
            <el-checkbox :model-value="row.reviewed" :disabled="status !== 'REVIEW'"
              @change="(v: boolean) => review(row, { reviewed: v })">已确认</el-checkbox>
          </template>
        </el-table-column>
      </el-table>
      <p class="muted">点行首箭头展开查看完整的证据、理由、缺少的信息和整改建议。</p>
    </el-card>

    <el-card v-if="total" shadow="never" class="block">
      <template #header>
        得分（{{ total.ruleVersion }}）
        <el-button size="small" type="primary" :disabled="!['SCORED', 'REPORTED'].includes(status!)" data-testid="report" @click="download">
          下载报告草稿
        </el-button>
      </template>
      <p class="total" data-testid="total">
        总分 <strong v-if="total.score != null">{{ total.score }}</strong>
        <template v-else><strong>不出总分</strong><span class="muted">{{ totalNote }}</span></template>
      </p>
      <p v-if="groupScores.length" class="muted">
        <span v-for="g in groupScores" :key="g.scopeKey">{{ g.scopeKey }}：{{ g.score }}　</span>
      </p>
      <el-table :data="layerScores" size="small">
        <el-table-column prop="scopeKey" label="安全层面" />
        <el-table-column label="得分">
          <template #default="{ row }">{{ row.score ?? '全部不适用' }}</template>
        </el-table-column>
      </el-table>
      <p class="muted">得分按《商用密码应用安全性评估量化评估规则（2023 版）》计算（技术 70 + 管理 30），属于辅助自查草稿，需测评人员确认。</p>
    </el-card>

    <el-dialog v-model="showObjectForm" title="添加测评对象" width="880px" destroy-on-close>
      <ObjectForm @submit="addObject" @cancel="showObjectForm = false" />
    </el-dialog>
    <ClauseDrawer :clause-ref="openedRef" @close="openedRef = null" />
  </div>
  <el-alert v-else-if="error" type="error" :title="error" :closable="false" />
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.block {
  margin-bottom: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 8px;
}
.measure-tag {
  margin: 0 4px 4px 0;
}
.filters {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}
.text {
  white-space: pre-wrap;
  font-size: 12px;
  line-height: 1.5;
}
.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.muted-text {
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.help {
  cursor: help;
  border-bottom: 1px dashed var(--el-text-color-secondary);
}
.expand {
  margin: 4px 16px;
}
.expand :deep(.el-descriptions__content) {
  white-space: pre-wrap;
}
.total {
  font-size: 16px;
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
  width: 90px;
}
</style>
