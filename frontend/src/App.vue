<script setup lang="ts">
import { ref } from 'vue'
import AssessmentList from './components/AssessmentList.vue'
import QaChat from './components/QaChat.vue'
import SearchDebug from './components/SearchDebug.vue'

const tab = ref('qa')
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <strong>密评智能助手</strong>
      <span class="sub">商用密码应用安全性评估 · 辅助自查工具</span>
    </el-header>
    <el-main>
      <el-tabs v-model="tab">
        <el-tab-pane label="条款问答" name="qa"><QaChat /></el-tab-pane>
        <el-tab-pane label="检索调试" name="search"><SearchDebug /></el-tab-pane>
        <el-tab-pane label="评估项目" name="assessment" lazy><AssessmentList /></el-tab-pane>
      </el-tabs>
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  max-width: 1280px;
  margin: 0 auto;
}
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--el-border-color);
}
.sub {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
