<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, streamQa } from '../api/client'
import type { ProblemDetail, QaCitations, QaDone, QaMeta } from '../api/types'
import { splitAnswer } from '../utils/citations'
import { plainText } from '../utils/plainText'
import ClauseDrawer from './ClauseDrawer.vue'

const question = ref('')
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

async function ask() {
  if (!question.value.trim() || running.value) return
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
      { question: question.value.trim(), mode: mode.value },
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
  <div>
    <el-alert
      type="info"
      :closable="false"
      title="回答仅依据检索到的条款生成，用于辅助自查，不构成正式测评结论。"
      class="notice"
    />
    <el-form @submit.prevent="ask">
      <el-input
        v-model="question"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="例如：第三级系统的数据库里保存身份证号，需要怎么做？"
      />
      <div class="actions">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="hybrid_rerank">混合 + 重排</el-radio-button>
          <el-radio-button value="hybrid">混合</el-radio-button>
        </el-radio-group>
        <span>
          <el-button v-if="running" @click="stop">停止</el-button>
          <el-button type="primary" native-type="submit" :loading="running">提问</el-button>
        </span>
      </div>
    </el-form>

    <el-alert v-if="problem" type="error" :closable="false" show-icon data-testid="problem"
      :title="problem.title || '请求失败'" :description="problem.detail" />
    <el-alert v-if="failure" type="warning" :closable="false" show-icon :title="failure" />

    <el-card v-if="meta" shadow="never" class="block">
      <template #header>
        检索到的条款（{{ meta.mode }}，最高分 {{ meta.topScore?.toFixed(3) ?? '—' }}，拒答阈值 {{ meta.refuseThreshold }}）
      </template>
      <el-tag v-for="h in meta.hits" :key="h.clauseRef" class="hit" effect="plain" @click="openedRef = h.clauseRef">
        {{ h.clauseRef }} {{ h.title }}
      </el-tag>
    </el-card>

    <el-card v-if="answer" shadow="never" class="block">
      <template #header>
        回答
        <el-tag v-if="done?.refused" type="warning" size="small">已拒答</el-tag>
        <el-tooltip v-if="formatIssues.length" content="提示词要求纯文本；模型仍输出了 Markdown 标记，页面已去掉这些标记后显示">
          <el-tag type="warning" size="small" data-testid="format-issues">格式校验未通过：含{{ formatIssues.join('、') }}</el-tag>
        </el-tooltip>
      </template>
      <!-- 模型输出按纯文本渲染：逐段生成文本节点与引用标签，不使用 v-html -->
      <p class="answer" data-testid="answer">
        <template v-for="(s, i) in segments" :key="i">
          <span v-if="s.kind === 'text'">{{ s.text }}</span>
          <el-tag
            v-else-if="!citationsKnown || s.valid"
            size="small"
            :type="citationsKnown ? 'success' : 'info'"
            class="cite"
            @click="openCitation(s.ref)"
          >{{ s.ref }}</el-tag>
          <el-tag v-else size="small" type="danger" class="cite invalid" title="无效引用，已剔除">{{ s.ref }}（无效）</el-tag>
        </template>
      </p>
    </el-card>

    <el-card v-if="citations" shadow="never" class="block">
      <template #header>引用来源（已校验）</template>
      <el-empty v-if="citations.valid.length === 0" description="没有有效引用" :image-size="60" />
      <div class="cards">
        <el-card v-for="c in citations.valid" :key="c.clauseRef" shadow="hover" class="cite-card" @click="openedRef = c.clauseRef">
          <div class="ref">{{ c.clauseRef }}</div>
          <div>{{ c.title }}</div>
        </el-card>
      </div>
      <el-alert
        v-if="citations.invalidCount > 0"
        type="warning"
        :closable="false"
        show-icon
        data-testid="invalid"
        :title="`已剔除 ${citations.invalidCount} 条无效引用，不作为来源`"
      >
        <div v-for="c in citations.invalid" :key="c.clauseRef">{{ c.clauseRef }}：{{ reasonText[c.reason] }}</div>
      </el-alert>
    </el-card>

    <div v-if="done" class="stats">
      <el-tag type="info" size="small">耗时 {{ done.latencyMs }} ms</el-tag>
      <el-tag v-if="done.firstTokenMs !== null" type="info" size="small">首字 {{ done.firstTokenMs }} ms</el-tag>
      <el-tag v-if="done.inputTokens !== null" type="info" size="small">token {{ done.inputTokens }} / {{ done.outputTokens }}</el-tag>
      <el-tag v-if="done.model" type="info" size="small">{{ done.model }}</el-tag>
    </div>
    <ClauseDrawer :clause-ref="openedRef" @close="openedRef = null" />
  </div>
</template>

<style scoped>
.notice,
.block {
  margin-bottom: 12px;
}
.actions {
  display: flex;
  justify-content: space-between;
  margin: 10px 0 14px;
}
.hit {
  margin: 0 6px 6px 0;
  cursor: pointer;
}
.answer {
  white-space: pre-wrap;
  line-height: 1.9;
}
.cite {
  margin: 0 2px;
  cursor: pointer;
}
.cite.invalid {
  text-decoration: line-through;
  cursor: default;
}
.cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.cite-card {
  width: 240px;
  cursor: pointer;
}
.ref {
  font-weight: 600;
}
.stats .el-tag {
  margin-right: 6px;
}
</style>
