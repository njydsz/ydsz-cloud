<!--
  @file CEP 模式可视化编辑器（P2-7）
  @description 提供复杂事件处理（CEP）模式的可视化编辑能力：
  1. 左侧模式列表：展示已注册的全部 CEP 模式，支持新增 / 选中 / 删除
  2. 右侧模式编辑器：按 4 种模式类型（TIME_WINDOW / SEQUENCE / AGGREGATE / ABSENCE）
     渲染差异化的表单字段
  3. 时间轴可视化预览：按时间顺序展示测试事件流，命中节点高亮显示
  4. 操作：保存 / 校验 / 删除 / 测试（投递模拟事件即时查看命中）
  @module views/execution/rule-engine/cep-pattern-editor
-->
<script setup lang="ts">
/**
 * CEP 模式可视化编辑器（P2-7）
 */
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Plus,
  Delete,
  Check,
  VideoPlay,
  Cpu,
  Timer,
  List as ListIcon,
  DataAnalysis,
} from '@element-plus/icons-vue'
import * as ruleApi from '@/api/execution/rule-engine'
import type {
  CEPPattern,
  CEPPatternType,
  CEPAggregateFunction,
  CEPTestEvent,
  CEPTestResult,
  CEPHit,
} from '@/api/execution/rule-engine'

const router = useRouter()

/** 模式类型选项 */
const patternTypeOptions: { value: CEPPatternType; label: string; desc: string }[] = [
  { value: 'TIME_WINDOW', label: '时间窗口', desc: '窗口内匹配事件数达到阈值时触发' },
  { value: 'SEQUENCE', label: '序列模式', desc: '按步骤顺序匹配 A → B → C，全部在窗口内匹配则触发' },
  { value: 'AGGREGATE', label: '聚合模式', desc: '窗口内对数值属性做 SUM/AVG/COUNT/MIN/MAX，达到阈值触发' },
  { value: 'ABSENCE', label: '缺失模式', desc: '期望某类型事件在窗口内出现，否则触发（告警）' },
]

/** 聚合函数选项 */
const aggregateFunctionOptions: { value: CEPAggregateFunction; label: string }[] = [
  { value: 'COUNT', label: 'COUNT（计数）' },
  { value: 'SUM', label: 'SUM（求和）' },
  { value: 'AVG', label: 'AVG（平均）' },
  { value: 'MIN', label: 'MIN（最小）' },
  { value: 'MAX', label: 'MAX（最大）' },
]

// ==================== 状态 ====================

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

/** 已注册的模式列表 */
const patternList = ref<CEPPattern[]>([])
/** 当前选中的模式 ID */
const selectedPatternId = ref<string>('')
/** 当前编辑的模式（响应式副本，编辑不直接污染列表） */
const editingPattern = reactive<CEPPattern>(createEmptyPattern())

/** 测试事件流（JSON 文本） */
const testEventsText = ref<string>(
  JSON.stringify(
    [
      { type: 'LOGIN_FAILED', partitionKey: 'user-001', attributes: { ip: '1.2.3.4' } },
      { type: 'LOGIN_FAILED', partitionKey: 'user-001', attributes: { ip: '1.2.3.4' } },
      { type: 'LOGIN_FAILED', partitionKey: 'user-001', attributes: { ip: '1.2.3.4' } },
    ],
    null,
    2,
  ),
)
/** 最近一次测试结果 */
const testResult = ref<CEPTestResult | null>(null)

// ==================== 工具函数 ====================

/** 创建空模式 */
function createEmptyPattern(): CEPPattern {
  return {
    id: '',
    type: 'TIME_WINDOW',
    name: '',
    ruleCode: '',
    windowMs: 60000,
    threshold: 3,
    eventType: '',
    eventTypes: [],
    filter: '',
    aggregateFunction: 'COUNT',
    aggregateField: '',
    sequence: [],
    description: '',
  }
}

/** 重置编辑器到空模式 */
function resetEditor() {
  Object.assign(editingPattern, createEmptyPattern())
  selectedPatternId.value = ''
  testResult.value = null
}

/** 当前模式类型描述 */
const currentTypeDesc = computed(() => {
  const opt = patternTypeOptions.find((o) => o.value === editingPattern.type)
  return opt?.desc || ''
})

/** 当前模式是否为 SEQUENCE（需要序列步骤编辑器） */
const isSequence = computed(() => editingPattern.type === 'SEQUENCE')

/** 时间轴事件列表（解析测试事件 + 命中标记） */
const timelineEvents = computed<{ event: CEPTestEvent; index: number; hit: boolean }[]>(() => {
  const events = parseTestEvents()
  if (!testResult.value || !testResult.value.hits?.length) {
    return events.map((event, index) => ({ event, index, hit: false }))
  }
  // 简化策略：若触发了命中，则标记后半区事件为命中（实际命中关联由后端计算）
  const hitCount = testResult.value.triggeredHits
  return events.map((event, index) => ({
    event,
    index,
    hit: hitCount > 0 && index >= events.length - Math.max(1, hitCount),
  }))
})

/** 解析测试事件文本 */
function parseTestEvents(): CEPTestEvent[] {
  try {
    const parsed = JSON.parse(testEventsText.value)
    if (!Array.isArray(parsed)) return []
    return parsed as CEPTestEvent[]
  } catch {
    return []
  }
}

// ==================== 数据加载 ====================

/** 加载已注册模式列表 */
async function loadPatterns() {
  loading.value = true
  try {
    const { data } = await ruleApi.listCepPatterns()
    patternList.value = data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载 CEP 模式失败')
    patternList.value = []
  } finally {
    loading.value = false
  }
}

/** 选中模式进行编辑 */
function selectPattern(pattern: CEPPattern) {
  selectedPatternId.value = pattern.id
  // 深拷贝避免直接污染列表
  Object.assign(editingPattern, JSON.parse(JSON.stringify(pattern)))
  testResult.value = null
}

/** 新建模式 */
function handleCreate() {
  resetEditor()
  // 生成临时 ID（保存时后端会按 id 注册/更新）
  editingPattern.id = 'PATTERN_' + Date.now()
  selectedPatternId.value = ''
}

// ==================== 序列步骤管理 ====================

/** 添加序列步骤 */
function addSequenceStep() {
  if (!editingPattern.sequence) editingPattern.sequence = []
  const nextOrder = (editingPattern.sequence?.length || 0) + 1
  editingPattern.sequence?.push({
    order: nextOrder,
    eventType: '',
    filter: '',
    minGapMs: undefined,
    maxGapMs: undefined,
  })
}

/** 删除序列步骤 */
function removeSequenceStep(index: number) {
  if (!editingPattern.sequence) return
  editingPattern.sequence.splice(index, 1)
  // 重排 order
  editingPattern.sequence.forEach((step, i) => (step.order = i + 1))
}

// ==================== 操作：保存 / 校验 / 删除 / 测试 ====================

/** 校验当前模式（前端基础校验） */
function validatePattern(): string | null {
  if (!editingPattern.id?.trim()) return '模式 ID 不能为空'
  if (!editingPattern.type) return '模式类型不能为空'
  if (!editingPattern.windowMs || editingPattern.windowMs <= 0) {
    return '时间窗口必须大于 0'
  }
  switch (editingPattern.type) {
    case 'TIME_WINDOW':
    case 'ABSENCE':
      if (!editingPattern.eventType?.trim() && !(editingPattern.eventTypes?.length || 0)) {
        return '事件类型不能为空（eventType 或 eventTypes 至少填一项）'
      }
      if (editingPattern.type === 'TIME_WINDOW' && (!editingPattern.threshold || editingPattern.threshold <= 0)) {
        return '触发阈值必须大于 0'
      }
      break
    case 'AGGREGATE':
      if (!editingPattern.eventType?.trim()) return '事件类型不能为空'
      if (!editingPattern.aggregateFunction) return '聚合函数不能为空'
      if (editingPattern.aggregateFunction !== 'COUNT' && !editingPattern.aggregateField?.trim()) {
        return '非 COUNT 聚合必须指定聚合字段'
      }
      if (!editingPattern.threshold || editingPattern.threshold <= 0) return '触发阈值必须大于 0'
      break
    case 'SEQUENCE':
      if (!editingPattern.sequence?.length) return '序列模式至少需要一个步骤'
      for (const step of editingPattern.sequence) {
        if (!step.eventType?.trim()) return `步骤 ${step.order} 的事件类型不能为空`
      }
      break
  }
  return null
}

/** 保存模式（注册到引擎） */
async function handleSave() {
  const err = validatePattern()
  if (err) {
    ElMessage.warning(err)
    return
  }
  saving.value = true
  try {
    await ruleApi.saveCepPattern(editingPattern)
    ElMessage.success(`模式「${editingPattern.name || editingPattern.id}」已保存`)
    await loadPatterns()
    selectedPatternId.value = editingPattern.id
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 删除模式 */
async function handleDelete() {
  if (!selectedPatternId.value) {
    ElMessage.info('请先从左侧列表选择要删除的模式')
    return
  }
  const pattern = patternList.value.find((p) => p.id === selectedPatternId.value)
  try {
    await ElMessageBox.confirm(
      `确认删除模式「${pattern?.name || selectedPatternId.value}」？此操作会从引擎注销。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await ruleApi.deleteCepPattern(selectedPatternId.value)
    ElMessage.success('模式已删除')
    resetEditor()
    await loadPatterns()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

/** 测试模式（投递模拟事件流） */
async function handleTest() {
  const err = validatePattern()
  if (err) {
    ElMessage.warning(err)
    return
  }
  const events = parseTestEvents()
  if (!events.length) {
    ElMessage.warning('测试事件流为空或 JSON 解析失败')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const { data } = await ruleApi.testCepPattern(editingPattern, events)
    testResult.value = data
    if (data.triggeredHits > 0) {
      ElMessage.success(`测试完成：命中 ${data.triggeredHits} 次`)
    } else {
      ElMessage.info('测试完成：未命中（模式条件未满足）')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '测试失败')
  } finally {
    testing.value = false
  }
}

/** 返回规则引擎主页 */
function goBack() {
  router.push('/execution/rule-engine')
}

onMounted(loadPatterns)
</script>

<template>
  <div class="cep-editor" v-loading="loading">
    <!-- 顶部导航 -->
    <div class="cep-header">
      <el-button :icon="ArrowLeft" link @click="goBack">返回规则引擎</el-button>
      <h2 class="cep-title">
        <el-icon><Cpu /></el-icon>
        CEP 模式可视化编辑器
      </h2>
      <div class="cep-header-actions">
        <el-button type="primary" :icon="Plus" @click="handleCreate">新建模式</el-button>
      </div>
    </div>

    <div class="cep-body">
      <!-- 左侧：模式列表 -->
      <div class="cep-sidebar">
        <div class="sidebar-header">
          <el-icon><ListIcon /></el-icon>
          <span>已注册模式</span>
          <el-badge :value="patternList.length" type="info" />
        </div>
        <div class="sidebar-list">
          <div
            v-for="pattern in patternList"
            :key="pattern.id"
            class="pattern-item"
            :class="{ 'pattern-item-active': pattern.id === selectedPatternId }"
            @click="selectPattern(pattern)"
          >
            <div class="pattern-item-name">{{ pattern.name || pattern.id }}</div>
            <div class="pattern-item-meta">
              <el-tag size="small" effect="plain">{{ pattern.type }}</el-tag>
              <span class="pattern-item-id">{{ pattern.id }}</span>
            </div>
          </div>
          <div v-if="!patternList.length && !loading" class="sidebar-empty">
            <el-empty description="暂无已注册模式" :image-size="60" />
          </div>
        </div>
      </div>

      <!-- 右侧：编辑器 + 预览 -->
      <div class="cep-main">
        <!-- 模式编辑器 -->
        <el-card shadow="never" class="editor-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Cpu /></el-icon>
                模式编辑器
                <el-tag v-if="selectedPatternId" size="small" type="success">编辑中</el-tag>
                <el-tag v-else size="small" type="warning">新建</el-tag>
              </span>
              <div class="card-actions">
                <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存</el-button>
                <el-button type="danger" :icon="Delete" plain @click="handleDelete">删除</el-button>
              </div>
            </div>
          </template>

          <el-form :model="editingPattern" label-width="120px" label-position="right">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="模式 ID" required>
                  <el-input v-model="editingPattern.id" placeholder="如 PATTERN_LOGIN_BURST" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="模式名称">
                  <el-input v-model="editingPattern.name" placeholder="如 登录失败频次检测" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="模式类型" required>
                  <el-select v-model="editingPattern.type" placeholder="选择模式类型" style="width: 100%">
                    <el-option
                      v-for="opt in patternTypeOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="关联规则编码">
                  <el-input v-model="editingPattern.ruleCode" placeholder="命中时触发的规则编码" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-alert v-if="currentTypeDesc" :title="currentTypeDesc" type="info" :closable="false" show-icon class="type-desc-alert" />

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="时间窗口(ms)" required>
                  <el-input-number v-model="editingPattern.windowMs" :min="100" :step="1000" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="滑动步长(ms)">
                  <el-input-number v-model="editingPattern.slideMs" :min="0" :step="1000" placeholder="0 表示滚动窗口" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- TIME_WINDOW / ABSENCE 共用：事件类型 + 阈值 -->
            <template v-if="editingPattern.type === 'TIME_WINDOW' || editingPattern.type === 'ABSENCE'">
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="事件类型" required>
                    <el-input v-model="editingPattern.eventType" placeholder="如 LOGIN_FAILED" />
                  </el-form-item>
                </el-col>
                <el-col v-if="editingPattern.type === 'TIME_WINDOW'" :span="12">
                  <el-form-item label="触发阈值" required>
                    <el-input-number v-model="editingPattern.threshold" :min="1" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
            </template>

            <!-- AGGREGATE：聚合函数 + 聚合字段 + 阈值 -->
            <template v-if="editingPattern.type === 'AGGREGATE'">
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="聚合函数" required>
                    <el-select v-model="editingPattern.aggregateFunction" style="width: 100%">
                      <el-option
                        v-for="opt in aggregateFunctionOptions"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="聚合字段" :required="editingPattern.aggregateFunction !== 'COUNT'">
                    <el-input v-model="editingPattern.aggregateField" placeholder="如 amount（COUNT 时可空）" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="触发阈值" required>
                    <el-input-number v-model="editingPattern.threshold" :min="0" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="事件类型" required>
                <el-input v-model="editingPattern.eventType" placeholder="如 TRANSFER" />
              </el-form-item>
            </template>

            <!-- SEQUENCE：序列步骤编辑器 -->
            <template v-if="isSequence">
              <el-form-item label="序列步骤" required>
                <div class="sequence-editor">
                  <div
                    v-for="(step, idx) in editingPattern.sequence"
                    :key="idx"
                    class="sequence-step"
                  >
                    <div class="step-order">{{ step.order }}</div>
                    <div class="step-fields">
                      <el-input v-model="step.eventType" placeholder="事件类型（如 LOGIN）" size="small" />
                      <el-input v-model="step.filter" placeholder="过滤表达式（可选）" size="small" />
                      <div class="step-gaps">
                        <el-input-number v-model="step.minGapMs" :min="0" :step="1000" placeholder="最小间隔(ms)" size="small" />
                        <el-input-number v-model="step.maxGapMs" :min="0" :step="1000" placeholder="最大间隔(ms)" size="small" />
                      </div>
                    </div>
                    <el-button type="danger" :icon="Delete" circle size="small" @click="removeSequenceStep(idx)" />
                  </div>
                  <el-button :icon="Plus" plain size="small" @click="addSequenceStep">添加步骤</el-button>
                </div>
              </el-form-item>
            </template>

            <!-- 通用：过滤表达式 + 描述 -->
            <el-form-item label="过滤表达式">
              <el-input
                v-model="editingPattern.filter"
                type="textarea"
                :rows="2"
                placeholder="Aviator 表达式，可访问 $event.attr('xxx')"
              />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="editingPattern.description" type="textarea" :rows="2" placeholder="模式描述" />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 时间轴可视化预览 + 测试 -->
        <el-card shadow="never" class="preview-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><DataAnalysis /></el-icon>
                时间轴可视化预览
              </span>
              <div class="card-actions">
                <el-button type="primary" :icon="VideoPlay" :loading="testing" @click="handleTest">测试</el-button>
              </div>
            </div>
          </template>

          <!-- 测试事件流输入 -->
          <el-form-item label="测试事件流" label-width="100px">
            <el-input
              v-model="testEventsText"
              type="textarea"
              :rows="6"
              placeholder='JSON 数组，如 [{"type":"LOGIN_FAILED","partitionKey":"u1"}]'
            />
          </el-form-item>

          <!-- 测试结果概要 -->
          <div v-if="testResult" class="test-summary">
            <el-tag :type="testResult.triggeredHits > 0 ? 'success' : 'info'" effect="dark">
              命中 {{ testResult.triggeredHits }} 次
            </el-tag>
            <el-tag type="info" effect="plain">投递事件 {{ testResult.fedEvents }} 条</el-tag>
          </div>

          <!-- 时间轴 -->
          <div class="timeline-container">
            <div v-if="!timelineEvents.length" class="timeline-empty">
              <el-empty description="暂无事件，请填写测试事件流" :image-size="80" />
            </div>
            <div v-else class="timeline">
              <div
                v-for="item in timelineEvents"
                :key="item.index"
                class="timeline-item"
                :class="{ 'timeline-item-hit': item.hit }"
              >
                <div class="timeline-dot" :class="{ 'timeline-dot-hit': item.hit }"></div>
                <div class="timeline-content">
                  <div class="timeline-event-type">
                    <el-icon><Timer /></el-icon>
                    {{ item.event.type }}
                    <el-tag v-if="item.hit" size="small" type="danger" effect="dark">命中</el-tag>
                  </div>
                  <div class="timeline-event-meta">
                    <span v-if="item.event.partitionKey">key: {{ item.event.partitionKey }}</span>
                    <span v-if="item.event.timestamp">ts: {{ item.event.timestamp }}</span>
                  </div>
                  <div v-if="item.event.attributes" class="timeline-event-attrs">
                    <code>{{ JSON.stringify(item.event.attributes) }}</code>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 命中详情 -->
          <div v-if="testResult?.hits?.length" class="hit-details">
            <div class="hit-details-title">命中详情（{{ testResult.hits.length }} 条）</div>
            <el-table :data="testResult.hits as CEPHit[]" size="small" border max-height="200">
              <el-table-column prop="patternId" label="模式 ID" width="180" />
              <el-table-column prop="ruleCode" label="关联规则" width="140" />
              <el-table-column prop="hitAt" label="命中时间" min-width="180" />
            </el-table>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.cep-editor {
  padding: 16px;
  min-height: 100vh;
  background: #f5f7fa;
}

.cep-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  .cep-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 18px;
    color: #303133;
  }
}

.cep-body {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
}

.cep-sidebar {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  height: fit-content;

  .sidebar-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    border-bottom: 1px solid #ebeef5;
    font-weight: 600;
    color: #303133;
  }

  .sidebar-list {
    max-height: 600px;
    overflow-y: auto;
  }

  .pattern-item {
    padding: 12px 16px;
    border-bottom: 1px solid #f5f7fa;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      background: #f5f7fa;
    }

    &.pattern-item-active {
      background: #ecf5ff;
      border-left: 3px solid #409eff;
      padding-left: 13px;
    }

    .pattern-item-name {
      font-size: 14px;
      font-weight: 500;
      color: #303133;
      margin-bottom: 4px;
    }

    .pattern-item-meta {
      display: flex;
      align-items: center;
      gap: 8px;

      .pattern-item-id {
        font-size: 12px;
        color: #909399;
      }
    }
  }

  .sidebar-empty {
    padding: 20px 0;
  }
}

.cep-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor-card,
.preview-card {
  :deep(.el-card__header) {
    padding: 12px 16px;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .card-actions {
    display: flex;
    gap: 8px;
  }
}

.type-desc-alert {
  margin: 8px 0 16px;
}

.sequence-editor {
  width: 100%;

  .sequence-step {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 8px;
    margin-bottom: 8px;
    background: #f5f7fa;
    border-radius: 4px;

    .step-order {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #409eff;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 600;
      flex-shrink: 0;
      margin-top: 4px;
    }

    .step-fields {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 6px;

      .step-gaps {
        display: flex;
        gap: 8px;
      }
    }
  }
}

.test-summary {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}

.timeline-container {
  margin-top: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;
  min-height: 120px;

  .timeline-empty {
    padding: 20px 0;
  }

  .timeline {
    position: relative;
    padding-left: 8px;

    &::before {
      content: '';
      position: absolute;
      left: 14px;
      top: 8px;
      bottom: 8px;
      width: 2px;
      background: #dcdfe6;
    }
  }

  .timeline-item {
    position: relative;
    padding: 8px 0 16px 32px;

    .timeline-dot {
      position: absolute;
      left: 8px;
      top: 12px;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #fff;
      border: 2px solid #c0c4cc;
      z-index: 1;

      &.timeline-dot-hit {
        background: #f56c6c;
        border-color: #f56c6c;
        box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.2);
      }
    }

    &.timeline-item-hit {
      .timeline-content {
        background: #fef0f0;
        border-color: #f56c6c;
      }
    }

    .timeline-content {
      padding: 8px 12px;
      background: #fff;
      border: 1px solid #ebeef5;
      border-radius: 4px;

      .timeline-event-type {
        display: flex;
        align-items: center;
        gap: 6px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 4px;
      }

      .timeline-event-meta {
        font-size: 12px;
        color: #909399;
        display: flex;
        gap: 12px;
      }

      .timeline-event-attrs {
        margin-top: 4px;
        font-size: 12px;

        code {
          background: #f5f7fa;
          padding: 2px 6px;
          border-radius: 2px;
          color: #606266;
        }
      }
    }
  }
}

.hit-details {
  margin-top: 16px;

  .hit-details-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
  }
}
</style>
