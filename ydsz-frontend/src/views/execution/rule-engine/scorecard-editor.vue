<!--
  @file 评分卡可视化编辑器（P1-4）
  @description 表格化编辑器：评分因子（条件 + 分值 + 权重）增删改查、风险等级阈值/评级映射配置、
               实时总分预览与 dry-run 仿真、JSON 导出。
  @module views/execution/rule-engine/scorecard-editor
  @author ydsz-team
  @since 1.5.0
-->
<template>
  <div class="scorecard-editor">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">评分卡编辑器 · {{ cardData.ruleName || ruleCode }}</span>
          <div class="actions">
            <el-button :icon="Refresh" @click="loadScorecard" :loading="loading">{{ $t('common.refresh') }}</el-button>
            <el-button :icon="CircleCheck" @click="validateScorecard" type="warning" plain>{{ $t('common.validate') }}</el-button>
            <el-button :icon="VideoPlay" @click="openPreview" type="success" plain>{{ $t('execution.ruleEngine.hitPreview') }}</el-button>
            <el-button :icon="Download" @click="exportJson">{{ $t('execution.ruleEngine.exportJson') }}</el-button>
            <el-button :icon="Check" @click="save" type="primary" :loading="saving">{{ $t('common.save') }}</el-button>
            <el-button :icon="Close" @click="goBack">{{ $t('common.back') }}</el-button>
          </div>
        </div>
      </template>

      <!-- 元信息 -->
      <el-form :inline="true" class="meta-form">
        <el-form-item label="规则编码">
          <el-input v-model="cardData.ruleCode" :disabled="!!ruleCode" style="width: 180px" />
        </el-form-item>
        <el-form-item label="规则名称">
          <el-input v-model="cardData.ruleName" style="width: 200px" />
        </el-form-item>
        <el-form-item label="类别">
          <el-input v-model="cardData.category" style="width: 140px" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="cardData.priority" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="作用域">
          <el-input v-model="cardData.scope" style="width: 140px" placeholder="可选" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="cardData.enabled" />
        </el-form-item>
      </el-form>

      <!-- 评分参数 -->
      <el-divider content-position="left">评分参数</el-divider>
      <el-form :inline="true" class="meta-form">
        <el-form-item label="基础分">
          <el-input-number v-model="cardData.baseScore" :min="0" :max="9999" :precision="2" />
        </el-form-item>
        <el-form-item label="评分方向">
          <el-select v-model="cardData.scoreDirection" style="width: 220px">
            <el-option label="DESCENDING 分数越低风险越高（扣分制）" value="DESCENDING" />
            <el-option label="ASCENDING 分数越高风险越高（加分制）" value="ASCENDING" />
          </el-select>
        </el-form-item>
        <el-form-item label="最低分">
          <el-input-number v-model="cardData.minScore" :min="-9999" :max="9999" :precision="2" />
        </el-form-item>
        <el-form-item label="最高分">
          <el-input-number v-model="cardData.maxScore" :min="-9999" :max="9999" :precision="2" />
        </el-form-item>
        <el-form-item label="红色阈值">
          <el-input-number v-model="cardData.redThreshold" :precision="2" />
        </el-form-item>
        <el-form-item label="黄色阈值">
          <el-input-number v-model="cardData.yellowThreshold" :precision="2" />
        </el-form-item>
      </el-form>
      <div class="param-hint">
        <el-icon><InfoFilled /></el-icon>
        <span v-if="cardData.scoreDirection === 'DESCENDING'">
          DESCENDING：总分低于红色阈值=RED，低于黄色阈值=YELLOW，否则=INFO
        </span>
        <span v-else>
          ASCENDING：总分高于红色阈值=RED，高于黄色阈值=YELLOW，否则=INFO
        </span>
      </div>

      <!-- 评分因子表 -->
      <el-divider content-position="left">评分因子（条件 → 分值 × 权重）</el-divider>
      <el-table :data="cardData.factors" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="条件表达式" min-width="240">
          <template #default="{ row }">
            <el-input
              v-model="row.conditionExpression"
              type="textarea"
              :rows="2"
              size="small"
              placeholder="如 overdueCount > 3"
            />
          </template>
        </el-table-column>
        <el-table-column label="固定分值" width="120" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.score"
              size="small"
              :precision="2"
              placeholder="如 -30"
              style="width: 110px"
            />
          </template>
        </el-table-column>
        <el-table-column label="动态分值表达式" min-width="180">
          <template #default="{ row }">
            <el-input
              v-model="row.scoreExpression"
              size="small"
              placeholder="如 contractAmount * 0.001（与固定分值二选一）"
            />
          </template>
        </el-table-column>
        <el-table-column label="权重" width="110" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.weight"
              size="small"
              :min="0"
              :max="10"
              :step="0.1"
              :precision="2"
              style="width: 100px"
            />
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.description" size="small" placeholder="因子说明" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeFactor($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="row-actions">
        <el-button :icon="Plus" type="primary" plain @click="addFactor">添加因子</el-button>
      </div>

      <!-- 评级映射 -->
      <el-divider content-position="left">评级映射（可选；配置后覆盖红/黄阈值的三级映射）</el-divider>
      <el-table :data="cardData.grades" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="评级名称" width="140">
          <template #default="{ row }">
            <el-input v-model="row.label" size="small" placeholder="如 A / B / C / D" />
          </template>
        </el-table-column>
        <el-table-column label="区间下界（含）" width="160" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.minScore" size="small" :precision="2" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="区间上界（不含）" width="160" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.maxScore" size="small" :precision="2" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="严重度" width="140" align="center">
          <template #default="{ row }">
            <el-select v-model="row.severity" size="small" clearable style="width: 120px">
              <el-option v-for="opt in severityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeGrade($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="row-actions">
        <el-button :icon="Plus" type="primary" plain @click="addGrade">添加评级</el-button>
      </div>

      <!-- 阈值可视化 -->
      <el-divider content-position="left">风险等级阈值可视化</el-divider>
      <div class="threshold-viz">
        <div class="threshold-bar">
          <div class="threshold-segment seg-info" :style="{ width: infoPct + '%' }">
            <span>INFO</span>
          </div>
          <div class="threshold-segment seg-yellow" :style="{ width: yellowPct + '%' }">
            <span>YELLOW</span>
          </div>
          <div class="threshold-segment seg-red" :style="{ width: redPct + '%' }">
            <span>RED</span>
          </div>
        </div>
        <div class="threshold-labels">
          <span>{{ cardData.minScore ?? 0 }}</span>
          <span>黄色阈值 {{ cardData.yellowThreshold ?? '-' }}</span>
          <span>红色阈值 {{ cardData.redThreshold ?? '-' }}</span>
          <span>{{ cardData.maxScore ?? 100 }}</span>
        </div>
      </div>
    </el-card>

    <!-- 校验结果对话框 -->
    <el-dialog v-model="validateResultVisible" title="评分卡校验结果" width="640px">
      <el-alert
        v-if="validateResult"
        :title="validateResult.valid ? '校验通过' : '校验未通过'"
        :type="validateResult.valid ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-table v-if="validateResult?.issues.length" :data="validateResult.issues" border stripe size="small">
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.level === 'ERROR' ? 'danger' : 'warning'" size="small">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="160" show-overflow-tooltip />
        <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="因子序号" width="90" align="center">
          <template #default="{ row }">
            {{ row.factorIndex != null ? '#' + row.factorIndex : '-' }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="validateResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 预览试算对话框 -->
    <el-dialog v-model="previewVisible" title="评分卡预览试算" width="780px" :close-on-click-modal="false">
      <el-form label-width="120px">
        <el-form-item label="事实数据">
          <el-input
            v-model="previewFactsText"
            type="textarea"
            :rows="8"
            placeholder='如 {"overdueCount": 5, "contractAmount": 2000000}'
            class="json-input"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="previewLoading" @click="runPreview">
            <el-icon><VideoPlay /></el-icon>执行试算
          </el-button>
        </el-form-item>
      </el-form>
      <el-divider content-position="left">试算结果</el-divider>
      <pre class="json-view">{{ formatJson(previewResult) }}</pre>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Plus, Delete, Refresh, Check, Close, VideoPlay, Download, CircleCheck, InfoFilled,
} from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type {
  ScorecardDefinition, ScoreFactor, ScoreGrade, ScoreDirection,
  ScorecardValidateResult, RuleResult,
} from '@/api/rule-engine'

defineOptions({ name: 'ScorecardEditor' })

const route = useRoute()
const router = useRouter()

const ruleCode = computed(() => route.params.ruleCode as string)
const loading = ref(false)
const saving = ref(false)

// ==================== 严重度选项 ====================

const severityOptions = [
  { label: '红色 RED', value: 'RED' },
  { label: '黄色 YELLOW', value: 'YELLOW' },
  { label: '通知 INFO', value: 'INFO' },
]

// ==================== 评分卡数据 ====================

const cardData = reactive<ScorecardDefinition>({
  ruleCode: '',
  ruleName: '',
  category: '通用',
  description: '',
  baseScore: 100,
  redThreshold: 60,
  yellowThreshold: 80,
  scoreDirection: 'DESCENDING',
  minScore: 0,
  maxScore: 100,
  factors: [],
  grades: [],
  enabled: true,
  priority: 50,
  scope: '',
  version: 0,
})

// ==================== 因子操作 ====================

function addFactor() {
  cardData.factors!.push({
    conditionExpression: '',
    score: 0,
    scoreExpression: '',
    weight: 1.0,
    description: '',
  })
}

function removeFactor(idx: number) {
  cardData.factors!.splice(idx, 1)
}

// ==================== 评级操作 ====================

function addGrade() {
  cardData.grades!.push({
    label: '',
    minScore: 0,
    maxScore: 100,
    severity: 'INFO',
  })
}

function removeGrade(idx: number) {
  cardData.grades!.splice(idx, 1)
}

// ==================== 阈值可视化 ====================

/** INFO 区间占比 */
const infoPct = computed(() => {
  const min = cardData.minScore ?? 0
  const max = cardData.maxScore ?? 100
  const yellow = cardData.yellowThreshold ?? max
  const desc = cardData.scoreDirection === 'DESCENDING'
  if (max <= min) return 0
  return desc
    ? Math.max(0, Math.min(100, ((max - yellow) / (max - min)) * 100))
    : Math.max(0, Math.min(100, ((yellow - min) / (max - min)) * 100))
})

/** YELLOW 区间占比 */
const yellowPct = computed(() => {
  const min = cardData.minScore ?? 0
  const max = cardData.maxScore ?? 100
  const yellow = cardData.yellowThreshold ?? max
  const red = cardData.redThreshold ?? min
  const desc = cardData.scoreDirection === 'DESCENDING'
  if (max <= min) return 0
  return desc
    ? Math.max(0, Math.min(100, ((yellow - red) / (max - min)) * 100))
    : Math.max(0, Math.min(100, ((red - yellow) / (max - min)) * 100))
})

/** RED 区间占比 */
const redPct = computed(() => {
  const min = cardData.minScore ?? 0
  const max = cardData.maxScore ?? 100
  const red = cardData.redThreshold ?? min
  const desc = cardData.scoreDirection === 'DESCENDING'
  if (max <= min) return 0
  return desc
    ? Math.max(0, Math.min(100, ((red - min) / (max - min)) * 100))
    : Math.max(0, Math.min(100, ((max - red) / (max - min)) * 100))
})

// ==================== 校验 ====================

const validateResultVisible = ref(false)
const validateResult = ref<ScorecardValidateResult | null>(null)

async function validateScorecard() {
  const payload = buildPayload()
  try {
    const { data } = await ruleApi.validateScorecard(payload)
    validateResult.value = data
    validateResultVisible.value = true
    if (data.valid) ElMessage.success('评分卡校验通过')
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '校验失败')
  }
}

// ==================== 预览 ====================

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewFactsText = ref('{\n  "overdueCount": 5,\n  "contractAmount": 2000000\n}')
const previewResult = ref<RuleResult[] | null>(null)

function openPreview() {
  previewResult.value = null
  previewVisible.value = true
}

async function runPreview() {
  let facts: Record<string, unknown>
  try {
    facts = JSON.parse(previewFactsText.value)
  } catch {
    ElMessage.error('事实数据 JSON 格式不正确')
    return
  }
  previewLoading.value = true
  try {
    const { data } = await ruleApi.dryRun(ruleCode.value, facts)
    previewResult.value = data
    ElMessage.success('试算完成')
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '试算失败')
  } finally {
    previewLoading.value = false
  }
}

// ==================== 导出 ====================

function exportJson() {
  const payload = buildPayload()
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `scorecard-${cardData.ruleCode || 'untitled'}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('JSON 已导出')
}

// ==================== 数据加载 / 保存 ====================

function buildPayload(): ScorecardDefinition {
  return {
    ruleCode: cardData.ruleCode,
    ruleName: cardData.ruleName,
    category: cardData.category,
    description: cardData.description,
    baseScore: cardData.baseScore,
    redThreshold: cardData.redThreshold,
    yellowThreshold: cardData.yellowThreshold,
    scoreDirection: cardData.scoreDirection as ScoreDirection,
    minScore: cardData.minScore,
    maxScore: cardData.maxScore,
    factors: cardData.factors?.map((f) => ({
      conditionExpression: f.conditionExpression,
      score: f.score,
      scoreExpression: f.scoreExpression || undefined,
      weight: f.weight,
      description: f.description,
    })),
    grades: cardData.grades?.length ? cardData.grades.map((g) => ({
      label: g.label,
      minScore: g.minScore,
      maxScore: g.maxScore,
      severity: g.severity,
    })) : undefined,
    enabled: cardData.enabled,
    priority: cardData.priority,
    scope: cardData.scope,
    version: cardData.version,
  }
}

async function loadScorecard() {
  if (!ruleCode.value) return
  loading.value = true
  try {
    const res = await ruleApi.getScorecard(ruleCode.value)
    if (res.code === 0 && res.data) {
      const def = res.data
      cardData.ruleCode = def.ruleCode || ruleCode.value
      cardData.ruleName = def.ruleName || ''
      cardData.category = def.category || '通用'
      cardData.description = def.description || ''
      cardData.baseScore = def.baseScore ?? 100
      cardData.redThreshold = def.redThreshold ?? 60
      cardData.yellowThreshold = def.yellowThreshold ?? 80
      cardData.scoreDirection = def.scoreDirection || 'DESCENDING'
      cardData.minScore = def.minScore ?? 0
      cardData.maxScore = def.maxScore ?? 100
      cardData.enabled = def.enabled ?? true
      cardData.priority = def.priority ?? 50
      cardData.scope = def.scope || ''
      cardData.version = def.version ?? 0
      cardData.factors = (def.factors || []).map((f: ScoreFactor) => ({
        conditionExpression: f.conditionExpression || '',
        score: f.score ?? 0,
        scoreExpression: f.scoreExpression || '',
        weight: f.weight ?? 1.0,
        description: f.description || '',
      }))
      cardData.grades = (def.grades || []).map((g: ScoreGrade) => ({
        label: g.label || '',
        minScore: g.minScore ?? 0,
        maxScore: g.maxScore ?? 100,
        severity: g.severity || 'INFO',
      }))
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载评分卡失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!cardData.ruleCode) {
    ElMessage.warning('请输入规则编码')
    return
  }
  if (!cardData.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }
  // 校验因子表达式非空
  for (let i = 0; i < (cardData.factors?.length || 0); i++) {
    if (!cardData.factors![i].conditionExpression?.trim()) {
      ElMessage.warning(`第 ${i + 1} 个因子条件表达式不能为空`)
      return
    }
  }
  saving.value = true
  try {
    const payload = buildPayload()
    const res = await ruleApi.saveScorecard(payload)
    if (res.code === 0) {
      ElMessage.success('保存成功')
      cardData.version = res.data?.version ?? cardData.version
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/rule-engine')
}

function formatJson(obj: unknown): string {
  if (!obj) return '（空）'
  return JSON.stringify(obj, null, 2)
}

onMounted(() => {
  if (ruleCode.value) loadScorecard()
})
</script>

<style scoped lang="scss">
.scorecard-editor { padding: 16px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-weight: 600; font-size: 16px; }
  .actions { display: flex; gap: 8px; }
}
.meta-form { margin-bottom: 8px; }
.row-actions { margin: 8px 0; }
.param-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
  padding: 6px 10px;
  background: #f4f4f5;
  border-radius: 4px;
}
.threshold-viz {
  margin-top: 8px;
  .threshold-bar {
    display: flex;
    height: 36px;
    border-radius: 4px;
    overflow: hidden;
    border: 1px solid #e4e7ed;
    .threshold-segment {
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      font-size: 12px;
      font-weight: 600;
      transition: width 0.3s ease;
      &.seg-info { background: #909399; }
      &.seg-yellow { background: #e6a23c; }
      &.seg-red { background: #f56c6c; }
    }
  }
  .threshold-labels {
    display: flex;
    justify-content: space-between;
    margin-top: 4px;
    font-size: 11px;
    color: #909399;
  }
}
.json-view {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
}
.json-input {
  :deep(textarea) {
    font-family: 'Courier New', Consolas, monospace;
    font-size: 13px;
  }
}
.mb-3 { margin-bottom: 12px; }
</style>
