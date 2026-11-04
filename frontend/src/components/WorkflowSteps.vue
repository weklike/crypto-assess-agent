<script setup lang="ts">
import { Check, Close } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { workflowStages } from '../utils/display'

const props = defineProps<{ status: string }>()

// 只展示后端返回的状态，前端不推断下一步
const stages = computed(() => workflowStages(props.status))
</script>

<template>
  <ol class="stages" aria-label="评估进度">
    <li
      v-for="(s, i) in stages"
      :key="s.key"
      class="stage"
      :class="s.state"
      :aria-current="s.state === 'current' || s.state === 'error' ? 'step' : undefined"
    >
      <span class="dot">
        <el-icon v-if="s.state === 'done'"><Check /></el-icon>
        <el-icon v-else-if="s.state === 'error'"><Close /></el-icon>
        <span v-else class="num">{{ i + 1 }}</span>
      </span>
      <span class="label">{{ s.label }}</span>
    </li>
  </ol>
</template>

<style scoped>
.stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.stage {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* 阶段之间的连线：从本阶段圆点右侧连到下一个圆点 */
.stage:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 12px;
  left: calc(50% + 16px);
  right: calc(-50% + 16px);
  height: 2px;
  border-radius: 1px;
  background: var(--el-border-color);
}

.stage.done:not(:last-child)::after {
  background: var(--el-color-primary-light-5);
}

.dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  border: 2px solid var(--el-border-color);
  background: var(--app-surface);
  color: var(--el-text-color-placeholder);
}

.stage.done .dot {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
  color: #fff;
}

.stage.done .label {
  color: var(--el-text-color-regular);
}

.stage.current .dot {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  box-shadow: 0 0 0 4px var(--el-color-primary-light-9);
}

.stage.current .label {
  color: var(--el-color-primary);
  font-weight: 600;
}

.stage.error .dot {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger);
  color: #fff;
  box-shadow: 0 0 0 4px var(--el-color-danger-light-9);
}

.stage.error .label {
  color: var(--el-color-danger);
  font-weight: 600;
}

@media (max-width: 720px) {
  .label {
    display: none;
  }
}
</style>
