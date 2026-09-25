<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { CryptoMeasures, Measure } from '../api/assessments'
import { LAYERS } from '../api/types'

const emit = defineEmits<{ submit: [body: { layer: string; name: string; description: string; measures: CryptoMeasures }]; cancel: [] }>()

const categories: { key: keyof CryptoMeasures; label: string }[] = [
  { key: 'transport', label: '传输' },
  { key: 'storage', label: '存储' },
  { key: 'auth', label: '身份鉴别' },
  { key: 'key_mgmt', label: '密钥管理' },
  { key: 'other', label: '其他' },
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
  <el-form label-width="90px" @submit.prevent="submit">
    <el-form-item label="安全层面">
      <el-select v-model="form.layer" style="width: 200px">
        <el-option v-for="l in LAYERS" :key="l" :value="l" :label="l" />
      </el-select>
    </el-form-item>
    <el-form-item label="对象名称">
      <el-input v-model="form.name" maxlength="128" placeholder="例如：客户信息数据库" />
    </el-form-item>
    <el-form-item label="描述">
      <el-input v-model="form.description" type="textarea" :rows="2" maxlength="2000" />
    </el-form-item>
    <el-divider content-position="left">密码措施</el-divider>
    <div v-for="c in categories" :key="c.key" class="category">
      <div class="category-head">
        <strong>{{ c.label }}</strong>
        <el-button size="small" text type="primary" @click="add(c.key)">添加一项</el-button>
      </div>
      <div v-for="(m, i) in measures[c.key]" :key="i" class="measure">
        <el-input v-model="m.algorithm" placeholder="算法，如 SM4-GCM" />
        <el-input v-model="m.protocol" placeholder="协议，如 国密 TLS" />
        <el-input v-model="m.product" placeholder="产品，如 服务器密码机" />
        <el-input v-model="m.evidence" placeholder="证据说明" />
        <el-button size="small" text type="danger" @click="remove(c.key, i)">删除</el-button>
      </div>
    </div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <div class="actions">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit">保存</el-button>
    </div>
  </el-form>
</template>

<style scoped>
.category {
  margin-bottom: 10px;
}
.category-head {
  display: flex;
  gap: 8px;
  align-items: center;
}
.measure {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1.5fr auto;
  gap: 6px;
  margin: 6px 0;
}
.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
