<!--
  @file 决策表可视化编辑器（P1-6）
  @description 表格化编辑器：HitPolicy 切换、行列增删、列类型显式声明、命中预览。
  @module views/execution/rule-engine/decision-table-editor
  @author ydsz-pmis-team
  @since 1.5.0
-->
<template>
  <div class="decision-table-editor">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">决策表编辑器 · {{ tableName || ruleCode }}</span>
          <div class="actions">
            <el-button :icon="Refresh" @click="loadTable" :loading="loading">{{ t('common.refresh') }}</el-button>
            <el-button :icon="VideoPlay" @click="dryRun" type="success" plain>{{ t('execution.ruleEngine.hitPreview') }}</el-button>
            <el-button :icon="Check" @click="save" type="primary" :loading="saving">{{ t('common.save') }}</el-button>
            <el-button :icon="Close" @click="goBack">{{ t('common.back') }}</el-button>
          </div>
        </div>
      </template>

      <!-- 元信息 -->
      <el-form :inline="true" class="meta-form">
        <el-form-item label="表编码">
          <el-input v-model="tableData.tableCode" :disabled="!!ruleCode" style="width: 180px" />
        </el-form-item>
        <el-form-item label="表名称">
          <el-input v-model="tableData.tableName" style="width: 200px" />
        </el-form-item>
        <el-form-item label="类别">
          <el-input v-model="tableData.category" style="width: 140px" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="tableData.priority" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="命中策略">
          <el-select v-model="tableData.hitPolicy" style="width: 160px">
            <el-option label="UNIQUE 唯一命中" value="UNIQUE" />
            <el-option label="FIRST 首条命中" value="FIRST" />
            <el-option label="ANY 任一命中" value="ANY" />
            <el-option label="PRIORITY 优先级" value="PRIORITY" />
            <el-option label="RULE_ORDER 规则顺序" value="RULE_ORDER" />
            <el-option label="COLLECT 全部收集" value="COLLECT" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 列定义区域 -->
      <el-divider content-position="left">输入条件列</el-divider>
      <el-table :data="tableData.conditions" border>
        <el-table-column label="列名" width="180">
          <template #default="{ row }">
            <el-input v-model="row.columnName" placeholder="如 amount" />
          </template>
        </el-table-column>
        <el-table-column label="显示名" width="160">
          <template #default="{ row }">
            <el-input v-model="row.displayName" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="数据类型" width="120">
          <template #default="{ row }">
            <el-select v-model="row.dataType" style="width: 100px">
              <el-option label="字符串" value="string" />
              <el-option label="数值" value="number" />
              <el-option label="日期" value="date" />
              <el-option label="布尔" value="boolean" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作符" width="120">
          <template #default="{ row }">
            <el-select v-model="row.operatorHint" style="width: 100px" clearable>
              <el-option label="字面值" value="=" />
              <el-option label="比较" value="cmp" />
              <el-option label="区间" value="range" />
              <el-option label="枚举" value="enum" />
              <el-option label="表达式" value="expr" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="必填" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.required" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeCondition($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="row-actions">
        <el-button :icon="Plus" type="primary" plain @click="addCondition">添加列</el-button>
      </div>

      <!-- 行数据（条件 + 动作） -->
      <el-divider content-position="left">规则行（条件 → 动作）</el-divider>
      <el-table :data="tableData.rows" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.priority" :min="0" :max="100" size="small" />
          </template>
        </el-table-column>
        <el-table-column v-for="col in tableData.conditions" :key="col.columnName || col._key"
          :label="col.displayName || col.columnName" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.conditions[col.columnName]"
              :placeholder="conditionHint(col)" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="动作 (JSON)" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.actionText" type="textarea" :rows="2"
              placeholder='如 {"severity":"RED","title":"超限","description":"..."}' />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeRow($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="row-actions">
        <el-button :icon="Plus" type="primary" plain @click="addRow">添加行</el-button>
      </div>

      <!-- 默认动作 -->
      <el-divider content-position="left">默认动作（无任何行命中时）</el-divider>
      <el-form-item>
        <el-input v-model="defaultActionText" type="textarea" :rows="2"
          placeholder='如 {"severity":"INFO","title":"无匹配"}' />
      </el-form-item>
    </el-card>

    <!-- 命中预览对话框 -->
    <el-dialog v-model="previewVisible" title="命中预览" width="600px">
      <pre class="json-view">{{ formatJson(previewResult) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Refresh, Check, Close, VideoPlay } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type { RuleDefinition } from '@/api/rule-engine'

defineOptions({ name: 'DecisionTableEditor' })

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const ruleCode = computed(() => route.params.ruleCode as string)
const tableName = ref('')
const loading = ref(false)
const saving = ref(false)
const previewVisible = ref(false)
const previewResult = ref<unknown>(null)

interface ColumnDef {
  _key: string
  columnName: string
  displayName: string
  dataType: 'string' | 'number' | 'date' | 'boolean'
  operatorHint: '=' | 'cmp' | 'range' | 'enum' | 'expr' | ''
  required: boolean
}
interface RowDef {
  priority: number
  conditions: Record<string, string>
  actionText: string
  _actions: Record<string, unknown>
}

const tableData = ref<{
  tableCode: string
  tableName: string
  category: string
  priority: number
  hitPolicy: string
  conditions: ColumnDef[]
  rows: RowDef[]
}>({
  tableCode: '',
  tableName: '',
  category: '通用',
  priority: 50,
  hitPolicy: 'FIRST',
  conditions: [],
  rows: [],
})
const defaultActionText = ref('')

function uuid() { return Math.random().toString(36).slice(2, 9) }

function addCondition() {
  tableData.value.conditions.push({
    _key: uuid(),
    columnName: '',
    displayName: '',
    dataType: 'string',
    operatorHint: '=',
    required: false,
  })
}
function removeCondition(idx: number) {
  tableData.value.conditions.splice(idx, 1)
}
function addRow() {
  const cond: Record<string, string> = {}
  tableData.value.conditions.forEach(c => { cond[c.columnName] = '' })
  tableData.value.rows.push({ priority: 50, conditions: cond, actionText: '{}', _actions: {} })
}
function removeRow(idx: number) {
  tableData.value.rows.splice(idx, 1)
}
function conditionHint(col: ColumnDef): string {
  switch (col.operatorHint) {
    case 'cmp': return '如 >=100 或 <0.05'
    case 'range': return '如 [0,100) 或 (0,100]'
    case 'enum': return '如 A|B|C'
    case 'expr': return '如 expr:amount>1000'
    default: return '字面值'
  }
}

function parseRowActions(text: string): Record<string, unknown> {
  try { return JSON.parse(text || '{}') } catch { return {} }
}

function formatJson(obj: unknown): string {
  if (!obj) return '（空）'
  return JSON.stringify(obj, null, 2)
}

async function loadTable() {
  loading.value = true
  try {
    const res = await ruleApi.getRule(ruleCode.value)
    if (res.code === 0 && res.data) {
      const def: RuleDefinition = res.data
      tableName.value = def.name
      tableData.value.tableCode = def.code
      tableData.value.tableName = def.name
      tableData.value.category = def.category || '通用'
      tableData.value.priority = def.priority ?? 50

      // 从 raw 解析 decision-table 数据
      const raw = (def as { decisionTable?: { hitPolicy?: string; conditions?: Array<Record<string, unknown>>; rows?: Array<Record<string, unknown>>; defaultActions?: unknown } }).decisionTable
      if (raw) {
        tableData.value.hitPolicy = raw.hitPolicy || 'FIRST'
        tableData.value.conditions = (raw.conditions || []).map((c) => ({
          _key: uuid(),
          columnName: (c.columnName as string) || (c.name as string) || '',
          displayName: (c.displayName as string) || '',
          dataType: (c.dataType as ColumnDef['dataType']) || 'string',
          operatorHint: (c.operatorHint as ColumnDef['operatorHint']) || '=',
          required: !!c.required,
        }))
        tableData.value.rows = (raw.rows || []).map((r) => ({
          priority: (r.priority as number) ?? 50,
          conditions: (r.conditions as Record<string, string>) || {},
          actionText: JSON.stringify(r.actions || {}),
          _actions: (r.actions as Record<string, unknown>) || {},
        }))
        if (raw.defaultActions) {
          defaultActionText.value = JSON.stringify(raw.defaultActions)
        }
      }
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    // 1. 行 actions JSON 校验
    for (const r of tableData.value.rows) {
      try {
        r._actions = JSON.parse(r.actionText || '{}')
      } catch {
        ElMessage.error('动作 JSON 解析失败: ' + r.actionText)
        return
      }
    }
    // 2. 组装为 DecisionTableDefinition
    const decisionTable = {
      tableCode: tableData.value.tableCode,
      tableName: tableData.value.tableName,
      category: tableData.value.category,
      priority: tableData.value.priority,
      hitPolicy: tableData.value.hitPolicy,
      conditions: tableData.value.conditions.map(c => ({
        columnName: c.columnName,
        displayName: c.displayName,
        dataType: c.dataType,
        operatorHint: c.operatorHint,
        required: c.required,
      })),
      rows: tableData.value.rows.map(r => ({
        priority: r.priority,
        conditions: r.conditions,
        actions: r._actions,
      })),
      defaultActions: parseRowActions(defaultActionText.value),
    }
    // 3. 调 saveRule（决策表数据通过扩展字段一并提交给后端）
    const updateRes = await ruleApi.saveRule({
      ...(await ruleApi.getRule(ruleCode.value)).data,
      decisionTable,
    } as unknown as RuleDefinition)
    if (updateRes.code === 0) {
      ElMessage.success('保存成功')
    } else {
      ElMessage.error(updateRes.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

async function dryRun() {
  // 命中预览：把所有条件 → row 用通配符 '*' 填，跑一次 dryRun
  const input: Record<string, unknown> = {}
  // 用户暂不输入事实，仅展示当前表的结构
  try {
    const res = await ruleApi.dryRun(ruleCode.value, input)
    previewResult.value = res.data
    previewVisible.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '预览失败')
  }
}

function goBack() {
  router.push('/rule-engine')
}

onMounted(() => {
  if (ruleCode.value) loadTable()
})
</script>

<style scoped lang="scss">
.decision-table-editor { padding: 16px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-weight: 600; font-size: 16px; }
  .actions { display: flex; gap: 8px; }
}
.meta-form { margin-bottom: 8px; }
.row-actions { margin: 8px 0; }
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
</style>
