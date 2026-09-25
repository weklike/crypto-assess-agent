<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { assessmentsApi, type AssessProject } from '../api/assessments'
import AssessmentDetail from './AssessmentDetail.vue'
import { statusText } from '../utils/status'
import { formatBeijing, formatTimesInText } from '../utils/time'

const projects = ref<AssessProject[]>([])
const selected = ref<number | null>(null)
const creating = ref(false)
const error = ref('')
const form = reactive({ name: '', systemName: '', level: 3 })

async function load() {
  try {
    projects.value = await assessmentsApi.list()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function create() {
  if (!form.name.trim() || !form.systemName.trim()) return
  try {
    const project = await assessmentsApi.create({ name: form.name.trim(), systemName: form.systemName.trim(), level: form.level })
    creating.value = false
    form.name = ''
    form.systemName = ''
    await load()
    selected.value = project.id
  } catch (e) {
    error.value = (e as Error).message
  }
}

onMounted(load)
</script>

<template>
  <el-row :gutter="16">
    <el-col :span="5">
      <el-card shadow="never">
        <template #header>
          评估项目
          <el-button size="small" type="primary" @click="creating = true">新建</el-button>
        </template>
        <el-alert v-if="error" type="error" :title="error" :closable="false" />
        <el-empty v-if="!projects.length" description="还没有评估项目" :image-size="60" />
        <div v-for="p in projects" :key="p.id" class="item" :class="{ active: p.id === selected }" @click="selected = p.id">
          <div>{{ formatTimesInText(p.name) }}</div>
          <div class="muted">{{ p.systemName }} · 第{{ p.level }}级 · {{ statusText(p.status) }}</div>
          <div class="muted">创建于 {{ formatBeijing(p.createdAt) }}</div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="19">
      <AssessmentDetail v-if="selected !== null" :id="selected" @changed="load" />
      <el-empty v-else description="选择或新建一个评估项目" />
    </el-col>
  </el-row>
  <el-dialog v-model="creating" title="新建评估项目" width="480px">
    <el-form label-width="90px" @submit.prevent="create">
      <el-form-item label="项目名称"><el-input v-model="form.name" maxlength="128" /></el-form-item>
      <el-form-item label="被测系统"><el-input v-model="form.systemName" maxlength="128" /></el-form-item>
      <el-form-item label="等级">
        <el-select v-model="form.level">
          <el-option v-for="l in [1, 2, 3, 4]" :key="l" :value="l" :label="`第${l}级`" />
        </el-select>
      </el-form-item>
      <div style="text-align: right"><el-button type="primary" native-type="submit">创建</el-button></div>
    </el-form>
  </el-dialog>
</template>

<style scoped>
.item {
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
}
.item:hover,
.item.active {
  background: var(--el-fill-color-light);
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
