<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getClause } from '../api/client'
import type { Clause } from '../api/types'

const props = defineProps<{ clauseRef: string | null }>()
const emit = defineEmits<{ close: [] }>()

const clause = ref<Clause | null>(null)
const error = ref('')
const loading = ref(false)

const crumbs = computed(() => (clause.value?.path ? clause.value.path.split(' > ') : []))
const levels = computed(() => (clause.value?.levels ? clause.value.levels.split(',') : []))

watch(
  () => props.clauseRef,
  async (value) => {
    clause.value = null
    error.value = ''
    if (!value) return
    loading.value = true
    try {
      clause.value = await getClause(value)
    } catch (e) {
      error.value = (e as Error).message
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-drawer :model-value="clauseRef !== null" size="min(560px, 92vw)" @close="emit('close')">
    <template #header>
      <div class="drawer-head">
        <span class="kicker">条款</span>
        <span class="mono ref">{{ clauseRef ?? '' }}</span>
      </div>
    </template>
    <div v-loading="loading" class="clause">
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
      <template v-if="clause">
        <nav v-if="crumbs.length" class="crumbs" aria-label="章节路径">
          <span v-for="(c, i) in crumbs" :key="i" class="crumb">{{ c }}</span>
        </nav>
        <h3>{{ clause.clauseNo }} {{ clause.title }}</h3>
        <div class="meta">
          <span v-if="clause.layer" class="badge tone-info">{{ clause.layer }}</span>
          <span v-if="clause.clauseType" class="badge plain">{{ clause.clauseType }}</span>
          <span v-if="levels.length" class="levels">
            适用等级
            <span v-for="l in levels" :key="l" class="level">{{ l }}级</span>
          </span>
        </div>
        <div class="body">{{ clause.body || '（章节标题，无正文）' }}</div>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.kicker {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ref {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.clause {
  min-height: 120px;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.crumb:not(:last-child)::after {
  content: '›';
  margin-left: 4px;
  color: var(--el-text-color-placeholder);
}

h3 {
  margin-top: 10px;
  font-size: 18px;
  line-height: 1.4;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.levels {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.level {
  padding: 0 6px;
  border-radius: 6px;
  line-height: 20px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
}

.body {
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: var(--app-radius-sm);
  border: 1px solid var(--app-border);
  background: var(--app-surface-muted);
  white-space: pre-wrap;
  line-height: 1.9;
  font-size: 14.5px;
  color: var(--el-text-color-primary);
}
</style>
