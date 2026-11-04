<script setup lang="ts">
import { Connection, Delete, Files, Key, More, Plus, User } from '@element-plus/icons-vue'
import { reactive, ref } from 'vue'
import type { CryptoMeasures, Measure } from '../api/assessments'
import { LAYERS } from '../api/types'

const emit = defineEmits<{ submit: [body: { layer: string; name: string; description: string; measures: CryptoMeasures }]; cancel: [] }>()

const categories: { key: keyof CryptoMeasures; label: string; hint: string; icon: typeof Plus }[] = [
  { key: 'transport', label: '传输', hint: '如 国密 TLS、IPSec VPN', icon: Connection },
  { key: 'storage', label: '存储', hint: '如 字段加密、完整性校验', icon: Files },
  { key: 'auth', label: '身份鉴别', hint: '如 数字证书、动态口令', icon: User },
  { key: 'key_mgmt', label: '密钥管理', hint: '如 密码机、密钥更新', icon: Key },
  { key: 'other', label: '其他', hint: '制度文件、证书等', icon: More },
]

const form = reactive({ layer: '应用和数据', name: '', description: '' })
const measures = reactive<CryptoMeasures>({ transport: [], storage: [], auth: [], key_mgmt: [], other: [] })
const error = ref('')

function add(key: keyof CryptoMeasures) {
  measures[key].push({ algorithm: '', protocol: '', product: '', evidence: '' })
}

function remove(key: keyof CryptoMeasures, index: number) {
  measures[key].splice(index, 1)
}

function clean(list: Measure[]): Measure[] {
  return list
    .map((m) => ({
      algorithm: m.algorithm?.trim() || null,
      protocol: m.protocol?.trim() || null,
      product: m.product?.trim() || null,
      evidence: m.evidence?.trim() || null,
    }))
    .filter((m) => m.algorithm || m.protocol || m.product || m.evidence)
}

function submit() {
  if (!form.name.trim()) {
    error.value = '请填写测评对象名称'
    return
  }
  error.value = ''
  emit('submit', {
    layer: form.layer,
    name: form.name.trim(),
    description: form.description.trim(),
    measures: {
      transport: clean(measures.transport),
      storage: clean(measures.storage),
      auth: clean(measures.auth),
      key_mgmt: clean(measures.key_mgmt),
      other: clean(measures.other),
    },
  })
}
</script>

<template>
  <el-form label-position="top" class="object-form" @submit.prevent="submit">
    <div class="basics">
      <el-form-item label="安全层面">
        <el-select v-model="form.layer" style="width: 100%">
          <el-option v-for="l in LAYERS" :key="l" :value="l" :label="l" />
        </el-select>
      </el-form-item>
      <el-form-item label="对象名称" required>
        <el-input v-model="form.name" maxlength="128" placeholder="例如：客户信息数据库" />
      </el-form-item>
    </div>
    <el-form-item label="描述">
      <el-input v-model="form.description" type="textarea" :rows="2" maxlength="2000" placeholder="例如：部署方式、保存哪些数据、谁来访问" />
    </el-form-item>

    <div class="measures-head">
      <span class="section-title">密码措施</span>
      <span class="muted">按用途分类录入；规则检查会从算法、协议、产品和证据文字中识别算法</span>
    </div>
    <div class="categories">
      <section v-for="c in categories" :key="c.key" class="category">
        <header class="category-head">
          <span class="category-icon"><el-icon><component :is="c.icon" /></el-icon></span>
          <strong>{{ c.label }}</strong>
          <span class="muted">{{ c.hint }}</span>
          <span class="spacer" />
          <span v-if="measures[c.key].length" class="count num">{{ measures[c.key].length }}</span>
          <el-button size="small" text type="primary" :icon="Plus" @click="add(c.key)">添加一项</el-button>
        </header>
        <div v-for="(m, i) in measures[c.key]" :key="i" class="measure">
          <el-input v-model="m.algorithm" placeholder="算法，如 SM4-GCM" />
          <el-input v-model="m.protocol" placeholder="协议，如 国密 TLS" />
          <el-input v-model="m.product" placeholder="产品，如 服务器密码机" />
          <el-input v-model="m.evidence" placeholder="证据说明" />
          <el-button text type="danger" :icon="Delete" aria-label="删除这一项" title="删除这一项" @click="remove(c.key, i)" />
        </div>
      </section>
    </div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon class="error" />
    <div class="actions">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit">保存</el-button>
    </div>
  </el-form>
</template>

<style scoped>
.basics {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
}

.measures-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 4px 0 12px;
}

.categories {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.category {
  padding: 10px 12px;
  border-radius: var(--app-radius-sm);
  border: 1px solid var(--app-border);
  background: var(--app-surface-muted);
}

.category-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.category-icon {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  font-size: 12px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.measure {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1.5fr auto;
  gap: 8px;
  margin-top: 8px;
}

.error {
  margin-top: 12px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 720px) {
  .basics,
  .measure {
    grid-template-columns: 1fr;
  }
}
</style>
