<!--
  @fileoverview CEP 模式编辑器组件 (Vue 3)
  @description 可视化编辑复杂事件处理模式：
  - 事件流定义
  - 窗口类型选择（时间/数量）
  - 条件表达式配置
  - 模式预览
  @module components/rule-engine/CepPatternEditor
  @author ydsz-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * CepPatternEditor - CEP 模式编辑器
 *
 * Props:
 *  - modelValue: CEPPattern 定义
 *
 * Events:
 *  - update:modelValue
 */
import { ref, computed, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'

interface CepEvent {
  name: string
  eventType: string
  condition: string
}

interface CepPattern {
  name: string
  description: string
  windowType: 'TIME' | 'COUNT'
  windowSize: number
  windowUnit: string
  events: CepEvent[]
  aggregation: string
  havingCondition: string
}

interface Props {
  modelValue: CepPattern
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: CepPattern): void
}>()

/** 内部维护的 pattern 副本（避免直接修改 prop） */
const localPattern = ref<CepPattern>({ ...props.modelValue })

/** 外部 modelValue 变化时同步至 localPattern */
watch(() => props.modelValue, (val) => {
  localPattern.value = { ...val }
}, { deep: true })

/** 将当前 localPattern 同步到父组件 */
function update() {
  emit('update:modelValue', { ...localPattern.value })
}

/** 添加一个新的事件流定义 */
function addEvent() {
  localPattern.value.events.push({
    name: `Event${localPattern.value.events.length + 1}`,
    eventType: '',
    condition: ''
  })
  update()
}

/** 移除指定索引的事件流定义 */
function removeEvent(idx: number) {
  localPattern.value.events.splice(idx, 1)
  update()
}

/** 窗口类型选项 */
const windowTypes = [
  { label: '时间窗口', value: 'TIME' },
  { label: '数量窗口', value: 'COUNT' }
]

/** 时间单位选项 */
const timeUnits = [
  { label: '秒', value: 'SECONDS' },
  { label: '分钟', value: 'MINUTES' },
  { label: '小时', value: 'HOURS' }
]

/** 聚合函数选项 */
const aggregations = [
  { label: '计数 (COUNT)', value: 'COUNT(*)' },
  { label: '求和 (SUM)', value: 'SUM(amount)' },
  { label: '平均 (AVG)', value: 'AVG(amount)' },
  { label: '最大 (MAX)', value: 'MAX(amount)' },
  { label: '最小 (MIN)', value: 'MIN(amount)' }
]

/** 模式预览文本 */
const previewText = computed(() => {
  const p = localPattern.value
  const events = p.events.map(e => `${e.name}(${e.eventType})`).join(' → ')
  const window = p.windowType === 'TIME'
    ? `${p.windowSize} ${p.windowUnit}`
    : `${p.windowSize} 条`
  return `CEP 模式 "${p.name}": ${events} 在 ${window} 窗口内 ${p.aggregation} ${p.havingCondition ? 'HAVING ' + p.havingCondition : ''}`
})
</script>

<template>
  <div class="cep-editor">
    <el-form :model="localPattern" label-width="120px" @input="update">
      <!-- 基本信息 -->
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="模式名称" required>
            <el-input v-model="localPattern.name" placeholder="如：高频交易检测" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="描述">
            <el-input v-model="localPattern.description" placeholder="模式描述" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 窗口配置 -->
      <el-divider content-position="left">窗口配置</el-divider>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="窗口类型">
            <el-select v-model="localPattern.windowType" @change="update" style="width: 100%">
              <el-option v-for="wt in windowTypes" :key="wt.value" :label="wt.label" :value="wt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="窗口大小">
            <el-input-number v-model="localPattern.windowSize" :min="1" style="width: 100%" @change="update" />
          </el-form-item>
        </el-col>
        <el-col :span="8" v-if="localPattern.windowType === 'TIME'">
          <el-form-item label="时间单位">
            <el-select v-model="localPattern.windowUnit" @change="update" style="width: 100%">
              <el-option v-for="u in timeUnits" :key="u.value" :label="u.label" :value="u.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 事件流配置 -->
      <el-divider content-position="left">
        <span>事件序列</span>
        <el-button :icon="Plus" link type="primary" @click="addEvent">添加事件</el-button>
      </el-divider>

      <div v-for="(evt, idx) in localPattern.events" :key="idx" class="event-row">
        <el-input
          v-model="evt.name"
          placeholder="事件名称"
          style="width: 120px"
          @input="update"
        />
        <el-input
          v-model="evt.eventType"
          placeholder="事件类型"
          style="width: 180px"
          @input="update"
        />
        <el-input
          v-model="evt.condition"
          placeholder="条件表达式（可选）"
          style="flex: 1"
          @input="update"
        />
        <el-button :icon="Delete" link type="danger" @click="removeEvent(idx)" />
      </div>

      <el-empty v-if="localPattern.events.length === 0" description="请添加事件" :image-size="60" />

      <!-- 聚合与过滤 -->
      <el-divider content-position="left">聚合与过滤</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="聚合函数">
            <el-select v-model="localPattern.aggregation" @change="update" style="width: 100%">
              <el-option v-for="a in aggregations" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="HAVING 条件">
            <el-input
              v-model="localPattern.havingCondition"
              placeholder="如: COUNT(*) >= 3"
              @input="update"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 预览 -->
      <el-divider content-position="left">模式预览</el-divider>
      <el-alert :title="previewText" type="info" :closable="false" show-icon>
        <template #default>
          <code class="preview-code">{{ previewText }}</code>
        </template>
      </el-alert>
    </el-form>
  </div>
</template>

<style scoped>
.cep-editor {
  padding: 16px;
}

.event-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.preview-code {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  color: var(--el-color-info);
}
</style>
