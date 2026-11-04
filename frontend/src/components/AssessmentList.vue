<script setup lang="ts">
import { Plus, Tickets } from '@element-plus/icons-vue'
import { onMounted, reactive, ref } from 'vue'
import { assessmentsApi, type AssessProject } from '../api/assessments'
import { statusTone } from '../utils/display'
import { statusText } from '../utils/status'
import { formatBeijing, formatTimesInText } from '../utils/time'
import AssessmentDetail from './AssessmentDetail.vue'

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
  <div class="assessments">
    <aside class="surface list">
      <header class="section-head">
        <div class="section-title"><el-icon><Tickets /></el-icon>评估项目</div>
        <span class="count num">{{ projects.length }}</span>
        <span class="spacer" />
        <el-button size="small" type="primary" :icon="Plus" @click="creating = true">新建</el-button>
      </header>
      <el-alert v-if="error" type="error" :title="error" :closable="false" class="list-alert" />
      <el-empty v-if="!projects.length" description="还没有评估项目" :image-size="64" />
      <div class="items">
        <button
          v-for="p in projects"
          :key="p.id"
          type="button"
          class="item"
          :class="{ active: p.id === selected }"
          :aria-current="p.id === selected ? 'true' : undefined"
          @click="selected = p.id"
        >
          <div class="item-name">{{ formatTimesInText(p.name) }}</div>
          <div class="item-meta">{{ p.systemName }} · 第{{ p.level }}级</div>
          <div class="item-foot">
            <span class="badge" :class="`tone-${statusTone(p.status)}`">{{ statusText(p.status) }}</span>
            <span class="item-time">创建于 {{ formatBeijing(p.createdAt) }}</span>
          </div>
        </button>
      </div>
    </aside>

    <section class="detail-pane">
      <AssessmentDetail v-if="selected !== null" :id="selected" @changed="load" />
      <div v-else class="surface placeholder">
        <el-empty description="选择左侧的评估项目，或新建一个开始自查" :image-size="96">
          <el-button type="primary" :icon="Plus" @click="creating = true">新建评估项目</el-button>
        </el-empty>
      </div>
    </section>

    <el-dialog v-model="creating" title="新建评估项目" width="480px">
      <el-form label-position="top" @submit.prevent="create">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" maxlength="128" placeholder="例如：营销系统上线前自查" />
        </el-form-item>
        <el-form-item label="被测系统" required>
          <el-input v-model="form.systemName" maxlength="128" placeholder="例如：营销业务系统" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.level" style="width: 100%">
            <el-option v-for="l in [1, 2, 3, 4]" :key="l" :value="l" :label="`第${l}级`" />
          </el-select>
        </el-form-item>
        <div class="dialog-actions">
          <el-button @click="creating = false">取消</el-button>
          <el-button type="primary" native-type="submit" :disabled="!form.name.trim() || !form.systemName.trim()">创建</el-button>
        </div>
      </el-form>
    </el-dialog>
  </div>
</template>

<style scoped>
.assessments {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.list {
  position: sticky;
  top: 84px;
  max-height: calc(100vh - 108px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list .section-head {
  padding: 14px 16px;
}

.count {
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  font-size: 12px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.list-alert {
  margin: 12px;
}

.items {
  padding: 8px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-sm);
  background: transparent;
  text-align: left;
  font: inherit;
  color: inherit;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.item:hover {
  background: var(--el-fill-color-light);
}

.item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.item-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.item-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.item-foot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin-top: 8px;
}

.item-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.detail-pane {
  min-width: 0;
}

.placeholder {
  padding: 48px 16px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

@media (max-width: 1360px) {
  .assessments {
    grid-template-columns: 240px minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .assessments {
    grid-template-columns: 1fr;
  }
  .list {
    position: static;
    max-height: none;
  }
}
</style>
