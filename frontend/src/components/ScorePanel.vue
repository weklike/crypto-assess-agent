<script setup lang="ts">
import { computed } from 'vue'
import type { AssessScore } from '../api/assessments'
import { scoreWidth } from '../utils/display'

const props = defineProps<{ scores: AssessScore[] }>()

const total = computed(() => props.scores.find((s) => s.scope === 'total'))
const groups = computed(() => props.scores.filter((s) => s.scope === 'group'))
const layers = computed(() => props.scores.filter((s) => s.scope === 'layer'))
const totalNote = computed(() => total.value?.detailJson?.note ?? '全部不适用')

const fmt = (score: number | null) => (score === null || score === undefined ? '—' : String(score))
</script>

<template>
  <div class="score-panel">
    <div class="overview">
      <div class="hero" data-testid="total">
        <div class="label">总分</div>
        <template v-if="total?.score != null">
          <div class="hero-value">{{ total.score }}<small>/ 100</small></div>
          <div class="meter big" :title="`总分 ${total.score} / 100`">
            <span class="fill" :style="{ width: scoreWidth(total.score, 100) }" />
          </div>
        </template>
        <template v-else>
          <div class="hero-value none">不出总分</div>
          <p class="note">{{ totalNote }}</p>
        </template>
      </div>

      <div v-if="groups.length" class="groups">
        <div v-for="g in groups" :key="g.scopeKey" class="group">
          <div class="label">{{ g.scopeKey }}</div>
          <div class="group-value">{{ fmt(g.score) }}<small>/ 1</small></div>
          <div class="meter" :title="`${g.scopeKey} ${fmt(g.score)}`">
            <span class="fill" :style="{ width: scoreWidth(g.score) }" />
          </div>
        </div>
      </div>
    </div>

    <div v-if="layers.length" class="layers">
      <div class="label">安全层面得分（满分 1）</div>
      <div v-for="l in layers" :key="l.scopeKey" class="layer-row" :title="`${l.scopeKey}：${l.score ?? '全部不适用'}`">
        <span class="layer-name">{{ l.scopeKey }}</span>
        <span class="layer-bar">
          <span v-if="l.score != null" class="meter"><span class="fill" :style="{ width: scoreWidth(l.score) }" /></span>
          <span v-else class="na">全部不适用</span>
        </span>
        <span class="layer-value num">{{ l.score ?? '—' }}</span>
      </div>
    </div>

    <p class="footnote">得分按《商用密码应用安全性评估量化评估规则（2023 版）》计算（技术 70 + 管理 30），属于辅助自查草稿，需测评人员确认。</p>
  </div>
</template>

<style scoped>
.score-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.overview {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(0, 2fr);
  gap: 16px;
}

.label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hero {
  padding: 16px 18px;
  border-radius: var(--app-radius-sm);
  background: linear-gradient(135deg, var(--el-color-primary-light-9), var(--app-surface));
  border: 1px solid var(--el-color-primary-light-8);
}

/* 每个视图只有一个大号数字；大号数字用比例字形 */
.hero-value {
  margin: 4px 0 10px;
  font-size: 48px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--el-text-color-primary);
  letter-spacing: -0.02em;
}

.hero-value small {
  margin-left: 6px;
  font-size: 15px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  letter-spacing: 0;
}

.hero-value.none {
  font-size: 28px;
  margin-bottom: 6px;
}

.note {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.meter.big {
  height: 10px;
  border-radius: 5px;
}

.groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.group {
  padding: 16px 18px;
  border-radius: var(--app-radius-sm);
  border: 1px solid var(--app-border);
  background: var(--app-surface-muted);
}

.group-value {
  margin: 4px 0 10px;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.group-value small {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.layers {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.layer-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 64px;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.layer-name {
  color: var(--el-text-color-regular);
}

.layer-value {
  text-align: right;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.na {
  display: block;
  height: 8px;
  line-height: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.footnote {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 860px) {
  .overview {
    grid-template-columns: 1fr;
  }
}
</style>
