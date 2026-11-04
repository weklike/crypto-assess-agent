<script setup lang="ts">
import { ChatLineSquare, CircleCheck, Document, Promotion, Search, Timer, VideoPause } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import { ApiError, streamQa } from '../api/client'
import type { ProblemDetail, QaCitations, QaDone, QaMeta } from '../api/types'
import { splitAnswer } from '../utils/citations'
import { formatDuration, scoreWidth, timingItems } from '../utils/display'
import { plainText } from '../utils/plainText'
import ClauseDrawer from './ClauseDrawer.vue'

const question = ref('')
const asked = ref('')
const mode = ref<'hybrid' | 'hybrid_rerank'>('hybrid_rerank')
const answer = ref('')
const meta = ref<QaMeta | null>(null)
const citations = ref<QaCitations | null>(null)
const done = ref<QaDone | null>(null)
const problem = ref<ProblemDetail | null>(null)
const failure = ref('')
const running = ref(false)
const openedRef = ref<string | null>(null)
let controller: AbortController | null = null

const modeOptions = [
  { label: '混合 + 重排', value: 'hybrid_rerank' },
  { label: '混合', value: 'hybrid' },
]
const MODE_LABELS: Record<string, string> = { hybrid_rerank: '混合 + 重排', hybrid: '混合（RRF）', bm25: 'BM25', dense: '向量' }
const EXAMPLES = [
  '第三级系统的数据库里保存身份证号，需要怎么做？',
  '运维人员远程登录服务器，可以用明文协议吗？',
  '机房门禁刷卡需要用密码技术吗？',
  '对外接口传输敏感字段要在应用层加密吗？',
]

const segments = computed(() =>
  splitAnswer(plainText(answer.value), citations.value?.valid.map((c) => c.clauseRef) ?? []),
)
const FORMAT_NAMES: Record<string, string> = {
  heading: '标题',
  bold: '加粗',
  bullet: '列表符号',
  code: '代码标记',
  quote: '引用符号',
  table: '表格',
  link: '链接',
}
const formatIssues = computed(() => (done.value?.formatIssues ?? []).map((k) => FORMAT_NAMES[k] ?? k))
const citationsKnown = computed(() => citations.value !== null)
const hasRun = computed(
  () => running.value || meta.value !== null || answer.value !== '' || problem.value !== null || failure.value !== '',
)

/** 重排分落在 0–1，按绝对值画条并标出拒答阈值；RRF 分没有固定上限，按本次最高分换算。 */
const reranked = computed(() => meta.value?.mode === 'hybrid_rerank')
const scoreName = computed(() => (reranked.value ? '重排分' : meta.value?.mode === 'hybrid' ? 'RRF 分' : '分数'))
const maxHitScore = computed(() => Math.max(0, ...(meta.value?.hits ?? []).map((h) => h.score ?? 0)))
const hitWidth = (score: number | null) => (reranked.value ? scoreWidth(score) : scoreWidth(score, maxHitScore.value || 1))
const thresholdLeft = computed(() => (reranked.value && meta.value ? scoreWidth(meta.value.refuseThreshold) : null))
const timings = computed(() => timingItems(meta.value?.timings))

async function ask() {
  if (!question.value.trim() || running.value) return
  asked.value = question.value.trim()
  answer.value = ''
  meta.value = null
  citations.value = null
  done.value = null
  problem.value = null
  failure.value = ''
  running.value = true
  controller = new AbortController()
  try {
    await streamQa(
      { question: asked.value, mode: mode.value },
      {
        onMeta: (m) => (meta.value = m),
        onToken: (t) => (answer.value += t.text),
        onCitations: (c) => (citations.value = c),
        onDone: (d) => (done.value = d),
        onError: (p) => (problem.value = p),
      },
      controller.signal,
    )
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      failure.value = '已停止。回答不完整，引用未经校验。'
    } else if (e instanceof ApiError) {
      problem.value = e.problem
    } else {
      failure.value = (e as Error).message
    }
  } finally {
    running.value = false
    controller = null
  }
}

function onKeydown(event: Event | KeyboardEvent) {
  const e = event as KeyboardEvent
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    ask()
  }
}

function openCitation(ref: string) {
  // 引用校验完成前不打开详情：此时还不知道它是否有效
  if (citationsKnown.value) {
    openedRef.value = ref
  }
}

function stop() {
  controller?.abort()
}

const reasonText: Record<string, string> = {
  NOT_FOUND: '知识库中不存在',
  NOT_IN_RESULTS: '不在本次检索结果中',
}
</script>

<template>
  <div class="qa">
    <section v-if="!hasRun" class="hero">
      <span class="hero-icon"><el-icon><ChatLineSquare /></el-icon></span>
      <h1>问一个密评问题</h1>
      <p>回答只依据检索到的条款生成，每条结论都标注来源条款，无法从条款得出时会直接拒答。</p>
    </section>

    <form class="composer surface" :class="{ compact: hasRun }" @submit.prevent="ask">
      <el-input
        v-model="question"
        type="textarea"
        :autosize="{ minRows: hasRun ? 1 : 3, maxRows: 8 }"
        resize="none"
        maxlength="1000"
        show-word-limit
        placeholder="例如：第三级系统的数据库里保存身份证号，需要怎么做？"
        @keydown="onKeydown"
      />
      <div class="composer-bar">
        <el-segmented v-model="mode" :options="modeOptions" size="small" />
        <span class="hint">Ctrl / ⌘ + Enter 发送</span>
        <span class="spacer" />
        <el-button v-if="running" :icon="VideoPause" @click="stop">停止</el-button>
        <el-button type="primary" native-type="submit" :loading="running" :icon="Promotion">提问</el-button>
      </div>
    </form>
    <p class="disclaimer">回答仅依据检索到的条款生成，用于辅助自查，不构成正式测评结论。</p>

    <div v-if="!hasRun" class="examples">
      <span class="examples-label">可以这样问</span>
      <button v-for="q in EXAMPLES" :key="q" type="button" class="example" @click="question = q">{{ q }}</button>
    </div>

    <el-alert v-if="problem" type="error" :closable="false" show-icon data-testid="problem" class="block"
      :title="problem.title || '请求失败'" :description="problem.detail" />
    <el-alert v-if="failure" type="warning" :closable="false" show-icon class="block" :title="failure" />

    <div v-if="hasRun" class="qa-grid">
      <div class="qa-main">
        <article class="surface">
          <header class="section-head">
            <div class="section-title"><el-icon><Document /></el-icon>回答</div>
            <span v-if="running" class="badge tone-progress">生成中</span>
            <span v-if="done?.refused" class="badge tone-warning">已拒答</span>
            <el-tooltip v-if="formatIssues.length" content="提示词要求纯文本；模型仍输出了 Markdown 标记，页面已去掉这些标记后显示">
              <span class="badge tone-warning" data-testid="format-issues">格式校验未通过：含{{ formatIssues.join('、') }}</span>
            </el-tooltip>
            <span v-else-if="citationsKnown && !done?.refused" class="badge tone-good">引用已校验</span>
          </header>
          <div class="section-body">
            <p class="asked">{{ asked }}</p>
            <el-skeleton v-if="running && !answer" :rows="3" animated />
            <!-- 模型输出按纯文本渲染：逐段生成文本节点与引用标签，不使用 v-html -->
            <p v-else class="answer" data-testid="answer">
              <template v-for="(s, i) in segments" :key="i">
                <span v-if="s.kind === 'text'">{{ s.text }}</span>
                <button
                  v-else-if="!citationsKnown || s.valid"
                  type="button"
                  class="cite"
                  :class="{ pending: !citationsKnown }"
                  :title="citationsKnown ? '查看条款原文' : '引用校验中'"
                  @click="openCitation(s.ref)"
                >{{ s.ref }}</button>
                <span v-else class="cite invalid" title="无效引用，已剔除">{{ s.ref }}（无效）</span>
              </template>
              <span v-if="running" class="caret" aria-hidden="true" />
            </p>
          </div>
          <footer v-if="done" class="answer-stats">
            <span><el-icon><Timer /></el-icon>耗时 {{ formatDuration(done.latencyMs) }}</span>
            <span v-if="done.firstTokenMs !== null">首字 {{ formatDuration(done.firstTokenMs) }}</span>
            <span v-if="done.inputTokens !== null" class="num">token {{ done.inputTokens }} / {{ done.outputTokens }}</span>
            <span v-if="done.model" class="mono">{{ done.model }}</span>
          </footer>
        </article>

        <section v-if="citations" class="surface">
          <header class="section-head">
            <div class="section-title"><el-icon><CircleCheck /></el-icon>引用来源（已校验）</div>
            <span class="muted">{{ citations.valid.length }} 条有效</span>
          </header>
          <div class="section-body">
            <el-empty v-if="citations.valid.length === 0" description="没有有效引用" :image-size="60" />
            <div class="cite-grid">
              <button v-for="c in citations.valid" :key="c.clauseRef" type="button" class="cite-card" @click="openedRef = c.clauseRef">
                <span class="mono cite-ref">{{ c.clauseRef }}</span>
                <span class="cite-title">{{ c.title }}</span>
              </button>
            </div>
            <el-alert
              v-if="citations.invalidCount > 0"
              type="warning"
              :closable="false"
              show-icon
              class="invalid-alert"
              data-testid="invalid"
              :title="`已剔除 ${citations.invalidCount} 条无效引用，不作为来源`"
            >
              <div v-for="c in citations.invalid" :key="c.clauseRef"><span class="mono">{{ c.clauseRef }}</span>：{{ reasonText[c.reason] }}</div>
            </el-alert>
          </div>
        </section>
      </div>

      <aside class="qa-side">
        <section v-if="meta" class="surface">
          <header class="section-head">
            <div class="section-title"><el-icon><Search /></el-icon>检索到的条款</div>
            <span class="spacer" />
            <span class="badge plain">{{ MODE_LABELS[meta.mode] ?? meta.mode }}</span>
          </header>
          <div class="section-body">
            <div class="score-summary">
              <span>最高{{ scoreName }} <strong class="num">{{ meta.topScore?.toFixed(3) ?? '—' }}</strong></span>
              <span class="muted">拒答阈值 <span class="num">{{ meta.refuseThreshold }}</span></span>
            </div>
            <el-empty v-if="!meta.hits.length" description="没有检索到条款" :image-size="56" />
            <ol class="hits">
              <li v-for="(h, i) in meta.hits" :key="h.clauseRef">
                <button type="button" class="hit" @click="openedRef = h.clauseRef">
                  <span class="hit-rank num">{{ i + 1 }}</span>
                  <span class="hit-body">
                    <span class="mono hit-ref">{{ h.clauseRef }}</span>
                    <span class="hit-title">{{ h.title }}</span>
                    <span class="hit-score" :title="`${scoreName} ${h.score ?? '—'}${reranked ? `，拒答阈值 ${meta.refuseThreshold}` : ''}`">
                      <span class="meter">
                        <span class="fill" :style="{ width: hitWidth(h.score) }" />
                        <span v-if="thresholdLeft" class="threshold" :style="{ left: thresholdLeft }" />
                      </span>
                      <span class="num">{{ h.score?.toFixed(3) ?? '—' }}</span>
                    </span>
                  </span>
                </button>
              </li>
            </ol>
            <p v-if="thresholdLeft && meta.hits.length" class="muted legend">竖线为拒答阈值：最高重排分低于它时拒答</p>
            <div v-if="timings.length" class="timings">
              <span v-for="t in timings" :key="t.key" class="timing" :class="{ slow: t.slowest }">
                {{ t.label }} <strong class="num">{{ t.text }}</strong><em v-if="t.slowest">最慢</em>
              </span>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <ClauseDrawer :clause-ref="openedRef" @close="openedRef = null" />
  </div>
</template>

<style scoped>
.qa {
  max-width: 1200px;
  margin: 0 auto;
}

.hero {
  text-align: center;
  padding: 40px 16px 24px;
  background: radial-gradient(60% 120% at 50% 0%, var(--app-hero-from), transparent 70%);
  border-radius: var(--app-radius);
}

.hero-icon {
  display: inline-grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  font-size: 26px;
  color: #fff;
  background: linear-gradient(135deg, var(--app-brand-from), var(--app-brand-to));
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.25);
}

.hero h1 {
  margin-top: 16px;
  font-size: 28px;
  font-weight: 700;
}

.hero p {
  margin: 8px auto 0;
  max-width: 560px;
  color: var(--el-text-color-secondary);
}

.composer {
  max-width: 860px;
  margin: 0 auto;
  padding: 14px 14px 10px;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.composer.compact {
  max-width: none;
}

.composer:focus-within {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 0 0 4px var(--el-color-primary-light-9), var(--app-shadow);
}

.composer :deep(.el-textarea__inner) {
  box-shadow: none;
  padding: 4px 6px;
  font-size: 15px;
  line-height: 1.7;
  background: transparent;
}

.composer :deep(.el-input__count) {
  background: transparent;
}

.composer-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.disclaimer {
  max-width: 860px;
  margin: 10px auto 0;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.examples {
  max-width: 860px;
  margin: 28px auto 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 8px;
}

.examples-label {
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 2px;
}

.example {
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--app-border);
  background: var(--app-surface);
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, transform 0.15s;
}

.example:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  transform: translateY(-1px);
}

.block {
  margin-top: 16px;
}

.qa-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 20px;
  margin-top: 20px;
  align-items: start;
}

.qa-main {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.qa-side {
  position: sticky;
  top: 84px;
}

.asked {
  margin: 0 0 12px;
  padding: 10px 14px;
  border-radius: var(--app-radius-sm);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.answer {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.95;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.cite {
  display: inline-flex;
  align-items: center;
  margin: 0 3px;
  padding: 0 7px;
  height: 22px;
  border-radius: 6px;
  border: 1px solid var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-family: var(--app-font-mono);
  font-size: 12px;
  line-height: 1;
  vertical-align: 1px;
  cursor: pointer;
}

.cite:hover {
  border-color: var(--el-color-primary);
}

.cite.pending {
  border-color: var(--el-border-color);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  cursor: default;
}

.cite.invalid {
  border-color: var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  text-decoration: line-through;
  cursor: default;
}

.caret {
  display: inline-block;
  width: 8px;
  height: 18px;
  margin-left: 2px;
  vertical-align: -3px;
  background: var(--el-color-primary);
  border-radius: 2px;
  animation: blink 1s steps(2, start) infinite;
}

@keyframes blink {
  to {
    visibility: hidden;
  }
}

.answer-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  padding: 12px 20px;
  border-top: 1px solid var(--app-border);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.answer-stats span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cite-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.cite-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  text-align: left;
  border-radius: var(--app-radius-sm);
  border: 1px solid var(--app-border);
  background: var(--app-surface-muted);
  font: inherit;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.cite-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--app-shadow-lg);
  transform: translateY(-1px);
}

.cite-ref {
  color: var(--el-color-primary);
  font-weight: 600;
}

.cite-title {
  color: var(--el-text-color-primary);
}

.invalid-alert {
  margin-top: 12px;
}

.score-summary {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.hits {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hit {
  width: 100%;
  display: flex;
  gap: 10px;
  padding: 8px;
  border: 0;
  border-radius: var(--app-radius-sm);
  background: transparent;
  text-align: left;
  font: inherit;
  cursor: pointer;
}

.hit:hover {
  background: var(--el-fill-color-light);
}

.hit-rank {
  flex: none;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 600;
}

.hit-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hit-ref {
  font-size: 12px;
  color: var(--el-color-primary);
}

.hit-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.hit-score {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hit-score .meter {
  flex: 1;
}

.threshold {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  margin-left: -1px;
  background: var(--viz-threshold);
  opacity: 0.55;
}

.legend {
  margin: 8px 0 0;
}

.timings {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--app-border);
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

.timing em {
  font-style: normal;
  font-size: 11px;
}

@media (max-width: 1080px) {
  .qa-grid {
    grid-template-columns: 1fr;
  }
  .qa-side {
    position: static;
  }
}
</style>
