<script setup lang="ts">
import { ref, watch } from 'vue'
import { getClause } from '../api/client'
import type { Clause } from '../api/types'

const props = defineProps<{ clauseRef: string | null }>()
const emit = defineEmits<{ close: [] }>()

const clause = ref<Clause | null>(null)
const error = ref('')
const loading = ref(false)

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
  <el-drawer :model-value="clauseRef !== null" :title="clauseRef ?? ''" size="40%" @close="emit('close')">
    <div v-loading="loading">
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <template v-if="clause">
        <p class="path">{{ clause.path }}</p>
        <h3>{{ clause.clauseNo }} {{ clause.title }}</h3>
        <p class="meta">
          <el-tag v-if="clause.layer" size="small">{{ clause.layer }}</el-tag>
          <el-tag v-if="clause.levels" size="small" type="info">适用等级 {{ clause.levels }}</el-tag>
          <el-tag v-if="clause.clauseType" size="small" type="success">{{ clause.clauseType }}</el-tag>
        </p>
        <p class="body">{{ clause.body }}</p>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.path {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.meta .el-tag {
  margin-right: 6px;
}
.body {
  white-space: pre-wrap;
  line-height: 1.8;
}
</style>
