<!--
  @file 规则引擎可视化管理
  @description 规则引擎管理页面：支持规则列表查看、新建/编辑、启停切换、版本历史与回滚、
               Dry-run 仿真、表达式校验、模板市场导入及 AI 辅助生成，
               对应路由 /execution/rule-engine。
  @module views/execution/rule-engine
-->
<script setup lang="ts">
/**
 * 规则引擎可视化管理
 *
 * 功能区域：
 *  1. 规则列表区：表格展示 + 新建/模板导入/AI 生成入口 + 编辑/启停/版本/删除操作
 *  2. 规则编辑对话框：表单录入 + 表达式校验 + 保存
 *  3. Dry-run 仿真面板：JSON 事实输入 + 执行 + 结果展示
 *  4. 模板市场对话框：模板列表 + 一键导入
 *  5. AI 生成对话框：自然语言描述 + 可用字段 + 生成/生成并保存 + 结果预览
 *  6. 版本历史对话框：版本列表 + 回滚
 *  7. 执行统计概览
 */
import { ref, reactive, computed, onMounted, watch, nextTick, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as echarts from 'echarts'
import * as ruleApi from '@/api/execution/rule-engine'
import type {
  RuleDefinition,
  RuleResult,
  RuleTemplate,
  RuleVersion,
  RuleEngineStats,
  ExecutionTrace,
  ReplayResult,
  RegressionReport,
} from '@/api/execution/rule-engine'
import ExpressionEditor from '@/components/common/ExpressionEditor.vue'

// ==================== 严重度映射 ====================

/** 严重度 → 标签/样式映射 */
const severityMap: Record<string, { label: string; type: 'danger' | 'warning' | 'info' | 'success' | 'primary' }> = {
  RED: { label: '红色', type: 'danger' },
  YELLOW: { label: '黄色', type: 'warning' },
  NORMAL: { label: '通知', type: 'info' },
}

/** 严重度选项 */
const severityOptions = [
  { label: '红色 RED', value: 'RED' },
  { label: '黄色 YELLOW', value: 'YELLOW' },
  { label: '通知 NORMAL', value: 'NORMAL' },
]

/** 获取严重度标签配置，缺省返回 info */
function severityOf(severity?: string) {
  if (!severity) return { label: '-', type: 'info' as const }
  return severityMap[severity] || { label: severity, type: 'info' as const }
}

// ==================== 可用字段 ====================

/** 常用可用字段列表（用于表达式编辑器自动补全） */
const availableFields = [
  'budgetUsedRatio', 'budgetTotal', 'budgetUsed', 'budgetRemaining',
  'spi', 'cpi', 'ev', 'pv', 'ac', 'sv', 'cv',
  'progress', 'daysRemaining', 'daysElapsed',
  'riskScore', 'utilizationRate', 'avgBillableUtilization',
  'overdueDays', 'activeProjects', 'evmRedCount',
  'confirmedRevenue', 'grossMargin', 'benchIdleCost',
  'budgetUsageRatio', 'slaBreachedCount',
  'actualCost', 'plannedCost', 'costVariance',
  'scheduleVariance', 'estimateAtCompletion',
  'resourceCount', 'allocatedHours', 'actualHours',
  'milestoneCount', 'completedMilestoneCount',
  'changeRequestCount', 'openIssueCount',
]

// ==================== 冲突检测 ====================

/** 冲突检测结果 */
interface RuleConflict {
  ruleA: string
  ruleAName: string
  ruleB: string
  ruleBName: string
  overlapFields: string[]
  severity: 'high' | 'medium' | 'low'
}

/** 冲突列表 */
const conflicts = ref<RuleConflict[]>([])
/** 冲突检测 loading */
const conflictLoading = ref(false)
/** 冲突对话框可见性 */
const conflictDialogVisible = ref(false)

/** 执行冲突检测 */
async function detectConflicts() {
  conflictLoading.value = true
  try {
    const { data } = await ruleApi.detectConflicts()
    conflicts.value = data || []
    if (conflicts.value.length > 0) {
      conflictDialogVisible.value = true
    } else {
      ElMessage.success('未检测到规则冲突')
    }
  } catch {
    ElMessage.warning('冲突检测失败，请稍后重试')
  } finally {
    conflictLoading.value = false
  }
}

// ==================== 统计图表 ====================

/** 统计图表容器 ref */
const statsChartRef = shallowRef<HTMLDivElement>()
let statsChartInstance: echarts.ECharts | null = null

/** 初始化统计图表 */
function initStatsChart() {
  if (!statsChartRef.value) return
  if (statsChartInstance) statsChartInstance.dispose()
  statsChartInstance = echarts.init(statsChartRef.value, null, { renderer: 'svg' })
  updateStatsChart()
}

/** 更新统计图表数据 */
function updateStatsChart() {
  if (!statsChartInstance || !stats.value) return
  const perRule = stats.value.perRuleStats || {}
  const ruleNames = Object.keys(perRule)
  const triggerData = ruleNames.map((k) => perRule[k].triggered || 0)
  const execData = ruleNames.map((k) => perRule[k].executions || 0)
  const errorData = ruleNames.map((k) => perRule[k].errors || 0)

  statsChartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      appendToBody: true,
    },
    legend: {
      data: ['执行次数', '触发次数', '错误次数'],
      bottom: 0,
      textStyle: { color: '#6b7280', fontSize: 11 },
    },
    grid: { left: 10, right: 10, top: 10, bottom: 30 },
    xAxis: {
      type: 'category',
      data: ruleNames,
      axisLabel: { rotate: 30, fontSize: 10, color: '#6b7280' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { fontSize: 10, color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f0f0f0' } },
    },
    series: [
      {
        name: '执行次数',
        type: 'bar',
        data: execData,
        itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 20,
      },
      {
        name: '触发次数',
        type: 'bar',
        data: triggerData,
        itemStyle: { color: '#d97706', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 20,
      },
      {
        name: '错误次数',
        type: 'bar',
        data: errorData,
        itemStyle: { color: '#dc2626', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 20,
      },
    ],
    animation: false,
  }, true)
}

// 监听 stats 变化更新图表
watch(
  () => stats.value,
  () => {
    nextTick(() => {
      if (statsChartRef.value && !statsChartInstance) {
        initStatsChart()
      } else {
        updateStatsChart()
      }
    })
  },
  { deep: true },
)

// ==================== 规则列表 ====================

/** 列表加载状态 */
const loading = ref(false)
/** 规则列表 */
const rules = ref<RuleDefinition[]>([])
/** 类别筛选 */
const categoryFilter = ref('')
/** 关键字筛选（编码/名称） */
const keyword = ref('')

/** 按类别/关键字过滤后的规则列表 */
const filteredRules = computed(() => {
  let list = rules.value
  if (categoryFilter.value) {
    list = list.filter((r) => r.category === categoryFilter.value)
  }
  if (keyword.value.trim()) {
    const kw = keyword.value.trim().toLowerCase()
    list = list.filter(
      (r) => r.code.toLowerCase().includes(kw) || r.name.toLowerCase().includes(kw),
    )
  }
  return list
})

/** 已存在的类别集合（用于筛选下拉） */
const categoryOptions = computed(() => {
  const set = new Set<string>()
  rules.value.forEach((r) => set.add(r.category))
  return Array.from(set)
})

/** 拉取规则列表 */
async function fetchRules() {
  loading.value = true
  try {
    const { data } = await ruleApi.listRules()
    rules.value = data || []
  } catch {
    rules.value = []
  } finally {
    loading.value = false
  }
}

// ==================== 执行统计 ====================

/** 执行统计 */
const stats = ref<RuleEngineStats | null>(null)

/** 拉取执行统计 */
async function fetchStats() {
  try {
    const { data } = await ruleApi.getStats()
    stats.value = data
  } catch {
    stats.value = null
  }
}

// ==================== 规则编辑对话框 ====================

/** 编辑对话框可见性 */
const editDialogVisible = ref(false)
/** 编辑模式：create / edit */
const editMode = ref<'create' | 'edit'>('create')
/** 编辑表单引用 */
const editFormRef = ref<FormInstance>()
/** 变更说明（编辑时填写） */
const changeDesc = ref('')
/** 表达式校验结果 */
const conditionValid = ref<null | boolean>(null)
const severityValid = ref<null | boolean>(null)
/** 校验按钮 loading */
const validating = ref(false)

/** 编辑表单数据 */
const editForm = reactive<RuleDefinition>({
  code: '',
  name: '',
  category: '',
  description: '',
  conditionExpression: '',
  severityExpression: '',
  defaultSeverity: 'YELLOW',
  titleTemplate: '',
  descriptionTemplate: '',
  priority: 100,
  enabled: true,
  scope: '',
  version: 0,
})

/** 表单校验规则 */
const editRules: FormRules = {
  code: [
    { required: true, message: '请输入规则编码', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_]*$/, message: '编码须以字母开头，仅含字母数字下划线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  category: [{ required: true, message: '请输入规则类别', trigger: 'blur' }],
  conditionExpression: [{ required: true, message: '请输入条件表达式', trigger: 'blur' }],
  defaultSeverity: [{ required: true, message: '请选择默认严重度', trigger: 'change' }],
  priority: [{ required: true, message: '请输入优先级', trigger: 'blur' }],
}

/** 重置编辑表单为默认值 */
function resetEditForm() {
  Object.assign(editForm, {
    code: '',
    name: '',
    category: '',
    description: '',
    conditionExpression: '',
    severityExpression: '',
    defaultSeverity: 'YELLOW',
    titleTemplate: '',
    descriptionTemplate: '',
    priority: 100,
    enabled: true,
    scope: '',
    version: 0,
  })
  changeDesc.value = ''
  conditionValid.value = null
  severityValid.value = null
}

/** 打开新建对话框 */
function openCreate() {
  editMode.value = 'create'
  resetEditForm()
  editDialogVisible.value = true
}

/** 打开编辑对话框 */
function openEdit(row: RuleDefinition) {
  editMode.value = 'edit'
  resetEditForm()
  Object.assign(editForm, JSON.parse(JSON.stringify(row)))
  editDialogVisible.value = true
}

/**
 * 校验表达式
 * @param expression 待校验表达式
 * @param target 校验目标：condition / severity
 */
async function handleValidate(expression: string, target: 'condition' | 'severity') {
  if (!expression) {
    ElMessage.warning('请先输入表达式')
    return
  }
  validating.value = true
  try {
    const { data } = await ruleApi.validateExpression(expression)
    if (target === 'condition') {
      conditionValid.value = data
    } else {
      severityValid.value = data
    }
    ElMessage[data ? 'success' : 'error'](data ? '表达式语法合法' : '表达式语法不合法')
  } finally {
    validating.value = false
  }
}

/** 保存规则 */
async function handleSave() {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    await ruleApi.saveRule(editForm, changeDesc.value || undefined)
    ElMessage.success(editMode.value === 'create' ? '规则创建成功' : '规则更新成功')
    editDialogVisible.value = false
    fetchRules()
  })
}

// ==================== 启停切换 / 删除 ====================

/**
 * 切换规则启停状态
 * @param row 规则定义
 * @param enabled 切换后的状态
 */
async function handleToggle(row: RuleDefinition, enabled: boolean) {
  try {
    await ruleApi.toggleRule(row.code, enabled)
    row.enabled = enabled
    ElMessage.success(`规则已${enabled ? '启用' : '停用'}`)
  } catch {
    // 失败时恢复状态（el-switch 已变更 model-value，需手动回滚）
    row.enabled = !enabled
  }
}

/**
 * 删除规则（二次确认）
 * @param row 规则定义
 */
async function handleDelete(row: RuleDefinition) {
  await ElMessageBox.confirm(
    `确认删除规则「${row.name}」(${row.code})？删除后不可恢复，建议使用停用代替。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  // 后端暂未提供独立删除接口，此处通过停用 + 提示占位
  // 若后端补充 DELETE 接口可替换为 ruleApi 调用
  await ruleApi.toggleRule(row.code, false)
  ElMessage.success('规则已停用（删除接口待后端补充）')
  fetchRules()
}

// ==================== Dry-run 仿真 ====================

/** Dry-run 对话框可见性 */
const dryRunDialogVisible = ref(false)
/** Dry-run 目标规则编码（空表示全部启用规则） */
const dryRunRuleCode = ref<string>('')
/** Dry-run 目标规则名称（用于标题展示） */
const dryRunRuleName = ref<string>('')
/** 事实数据 JSON 文本 */
const dryRunFactsText = ref('{\n  "budgetUsedRatio": 0.95,\n  "spi": 0.85,\n  "cpi": 0.9\n}')
/** Dry-run 结果 */
const dryRunResults = ref<RuleResult[]>([])
/** Dry-run 执行 loading */
const dryRunLoading = ref(false)

/**
 * 打开 Dry-run 对话框
 * @param row 规则定义（可选，未传则对全部启用规则求值）
 */
function openDryRun(row?: RuleDefinition) {
  if (row) {
    dryRunRuleCode.value = row.code
    dryRunRuleName.value = row.name
  } else {
    dryRunRuleCode.value = ''
    dryRunRuleName.value = '全部启用规则'
  }
  dryRunResults.value = []
  dryRunFactsText.value = '{\n  "budgetUsedRatio": 0.95,\n  "spi": 0.85,\n  "cpi": 0.9\n}'
  dryRunDialogVisible.value = true
}

/** 执行 Dry-run 仿真 */
async function handleDryRun() {
  let facts: Record<string, unknown>
  try {
    facts = JSON.parse(dryRunFactsText.value) as Record<string, unknown>
  } catch {
    ElMessage.error('事实数据 JSON 格式不正确，请检查')
    return
  }
  dryRunLoading.value = true
  try {
    const { data } = await ruleApi.dryRun(dryRunRuleCode.value || null, facts)
    dryRunResults.value = data || []
    const triggered = dryRunResults.value.filter((r) => r.triggered).length
    ElMessage.success(`仿真完成，共触发 ${triggered} 条规则`)
  } finally {
    dryRunLoading.value = false
  }
}

// ==================== 版本历史 ====================

/** 版本历史对话框可见性 */
const versionDialogVisible = ref(false)
/** 版本历史列表 */
const versions = ref<RuleVersion[]>([])
/** 版本加载状态 */
const versionLoading = ref(false)
/** 当前查看版本历史的规则编码 */
const versionRuleCode = ref('')

/**
 * 打开版本历史对话框
 * @param row 规则定义
 */
async function openVersions(row: RuleDefinition) {
  versionRuleCode.value = row.code
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    const { data } = await ruleApi.listVersions(row.code)
    versions.value = data || []
  } catch {
    versions.value = []
  } finally {
    versionLoading.value = false
  }
}

/**
 * 回滚到指定版本
 * @param version 版本号
 */
async function handleRollback(version: number) {
  await ElMessageBox.confirm(
    `确认将规则回滚到版本 v${version}？回滚后将生成新版本。`,
    '版本回滚确认',
    { type: 'warning' },
  )
  await ruleApi.rollbackRule(versionRuleCode.value, version)
  ElMessage.success('回滚成功')
  versionDialogVisible.value = false
  fetchRules()
}

// ==================== 模板市场 ====================

/** 模板市场对话框可见性 */
const templateDialogVisible = ref(false)
/** 模板列表 */
const templates = ref<RuleTemplate[]>([])
/** 模板加载状态 */
const templateLoading = ref(false)
/** 模板类别筛选 */
const templateCategoryFilter = ref('')
/** 模板导入中编码集合 */
const importingCodes = ref<Set<string>>(new Set())

/** 按类别过滤后的模板 */
const filteredTemplates = computed(() => {
  if (!templateCategoryFilter.value) return templates.value
  return templates.value.filter((t) => t.category === templateCategoryFilter.value)
})

/** 模板类别选项 */
const templateCategoryOptions = computed(() => {
  const set = new Set<string>()
  templates.value.forEach((t) => set.add(t.category))
  return Array.from(set)
})

/** 打开模板市场对话框 */
async function openTemplateMarket() {
  templateDialogVisible.value = true
  templateCategoryFilter.value = ''
  templateLoading.value = true
  try {
    const { data } = await ruleApi.listTemplates()
    templates.value = data || []
  } catch {
    templates.value = []
  } finally {
    templateLoading.value = false
  }
}

/**
 * 一键导入模板
 * @param tpl 模板
 */
async function handleImportTemplate(tpl: RuleTemplate) {
  importingCodes.value.add(tpl.templateCode)
  try {
    await ruleApi.importTemplate(tpl.templateCode)
    ElMessage.success(`模板「${tpl.templateName}」导入成功`)
    templateDialogVisible.value = false
    fetchRules()
  } finally {
    importingCodes.value.delete(tpl.templateCode)
  }
}

// ==================== AI 辅助生成 ====================

/** AI 生成对话框可见性 */
const aiDialogVisible = ref(false)
/** 自然语言描述 */
const aiDescription = ref('')
/** 可用字段（多选） */
const aiFields = ref<string[]>([])
/** 生成中 loading */
const aiLoading = ref(false)
/** AI 生成结果预览 */
const aiResult = ref<RuleDefinition | null>(null)

/** 常用可用字段建议 */
const fieldSuggestions = [
  'budgetUsedRatio',
  'budgetTotal',
  'budgetUsed',
  'spi',
  'cpi',
  'ev',
  'pv',
  'ac',
  'progress',
  'daysRemaining',
  'riskScore',
  'utilizationRate',
  'overdueDays',
]

/**
 * 打开 AI 生成对话框
 */
function openAiGenerate() {
  aiDescription.value = ''
  aiFields.value = []
  aiResult.value = null
  aiDialogVisible.value = true
}

/** AI 生成（仅预览） */
async function handleAiGenerate() {
  if (!aiDescription.value.trim()) {
    ElMessage.warning('请输入规则描述')
    return
  }
  aiLoading.value = true
  try {
    const { data } = await ruleApi.aiGenerate(aiDescription.value, aiFields.value)
    aiResult.value = data
    ElMessage.success('生成成功，请预览')
  } finally {
    aiLoading.value = false
  }
}

/** AI 生成并保存 */
async function handleAiGenerateAndSave() {
  if (!aiDescription.value.trim()) {
    ElMessage.warning('请输入规则描述')
    return
  }
  aiLoading.value = true
  try {
    await ruleApi.aiGenerateAndSave(aiDescription.value, aiFields.value)
    ElMessage.success('生成并保存成功')
    aiDialogVisible.value = false
    fetchRules()
  } finally {
    aiLoading.value = false
  }
}

/** 将 AI 生成结果载入编辑对话框 */
function loadAiResultToEdit() {
  if (!aiResult.value) return
  editMode.value = 'create'
  resetEditForm()
  Object.assign(editForm, JSON.parse(JSON.stringify(aiResult.value)))
  aiDialogVisible.value = false
  editDialogVisible.value = true
}

// ==================== 执行链路回放 ====================

/** 执行回放对话框可见性 */
const traceDialogVisible = ref(false)
/** 最近链路列表 */
const recentTraces = ref<ExecutionTrace[]>([])
/** 链路加载状态 */
const traceLoading = ref(false)
/** 回放结果 */
const replayResult = ref<ReplayResult | null>(null)
/** 回放加载状态 */
const replayLoading = ref(false)
/** 当前选中的 traceId */
const selectedTraceId = ref('')

/** 打开执行回放对话框 */
async function openTraceReplay() {
  traceDialogVisible.value = true
  replayResult.value = null
  selectedTraceId.value = ''
  traceLoading.value = true
  try {
    const { data } = await ruleApi.listRecentTraces(50)
    recentTraces.value = data || []
  } catch {
    recentTraces.value = []
  } finally {
    traceLoading.value = false
  }
}

/**
 * 执行回放
 * @param traceId 追踪 ID
 */
async function handleReplay(traceId: string) {
  selectedTraceId.value = traceId
  replayLoading.value = true
  try {
    const { data } = await ruleApi.replayTrace(traceId)
    replayResult.value = data
  } catch {
    ElMessage.error('回放失败，请检查 traceId 是否有效')
    replayResult.value = null
  } finally {
    replayLoading.value = false
  }
}

// ==================== 回归测试 ====================

/** 回归测试对话框可见性 */
const regressionDialogVisible = ref(false)
/** 回归测试报告 */
const regressionReport = ref<RegressionReport | null>(null)
/** 回归测试加载状态 */
const regressionLoading = ref(false)

/** 执行回归测试（全部测试用例） */
async function openRegressionTest() {
  regressionDialogVisible.value = true
  regressionReport.value = null
  regressionLoading.value = true
  try {
    const { data } = await ruleApi.batchRunTestCases([])
    regressionReport.value = data
    if (data.allPassed) {
      ElMessage.success(`回归测试全部通过（${data.total} 个用例）`)
    } else {
      ElMessage.warning(`回归测试通过率 ${data.passRate}（${data.failed} 个失败）`)
    }
  } catch {
    ElMessage.error('回归测试执行失败')
  } finally {
    regressionLoading.value = false
  }
}

// ==================== 页面初始化 ====================

onMounted(() => {
  fetchRules()
  fetchStats().then(() => {
    nextTick(() => initStatsChart())
  })
})
</script>

<template>
  <div class="rule-engine-page">
    <!-- 执行统计概览 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">总评估次数</div>
          <div class="stat-value">{{ stats?.totalEvaluations ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">总触发次数</div>
          <div class="stat-value highlight-warning">{{ stats?.totalTriggered ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">总错误次数</div>
          <div class="stat-value highlight-danger">{{ stats?.totalErrors ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">总耗时(ms)</div>
          <div class="stat-value">{{ stats?.totalElapsedMs ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 统计图表 -->
    <el-card
      v-if="stats?.perRuleStats && Object.keys(stats.perRuleStats).length > 0"
      shadow="hover"
      class="stats-chart-card"
    >
      <div ref="statsChartRef" style="width:100%;min-height:300px"></div>
    </el-card>

    <!-- 主卡片：工具栏 + 规则列表 -->
    <el-card shadow="never" class="main-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon>新建规则
          </el-button>
          <el-button type="success" @click="openTemplateMarket">
            <el-icon><Files /></el-icon>从模板导入
          </el-button>
          <el-button type="warning" @click="openAiGenerate">
            <el-icon><MagicStick /></el-icon>AI 生成
          </el-button>
          <el-button @click="openDryRun()">
            <el-icon><VideoPlay /></el-icon>Dry-run 仿真
          </el-button>
          <el-button :loading="conflictLoading" @click="detectConflicts">
            <el-icon><WarningFilled /></el-icon>冲突检测
          </el-button>
          <el-button type="info" @click="openTraceReplay">
            <el-icon><View /></el-icon>执行回放
          </el-button>
          <el-button type="primary" plain @click="openRegressionTest">
            <el-icon><CircleCheck /></el-icon>回归测试
          </el-button>
        </div>
        <div class="toolbar-right">
          <el-select
            v-model="categoryFilter"
            placeholder="按类别筛选"
            clearable
            style="width: 160px"
            @change="() => {}"
          >
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <el-input
            v-model="keyword"
            placeholder="编码 / 名称"
            clearable
            style="width: 200px"
            :prefix-icon="'Search'"
          />
          <el-button :icon="'Refresh'" circle @click="fetchRules" />
        </div>
      </div>

      <!-- 规则列表表格 -->
      <el-table v-loading="loading" :data="filteredRules" border stripe style="width: 100%">
        <el-table-column prop="code" label="规则编码" width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="规则名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类别" width="120">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90" sortable />
        <el-table-column label="默认严重度" width="110">
          <template #default="{ row }">
            <el-tag :type="severityOf(row.defaultSeverity).type" size="small">
              {{ severityOf(row.defaultSeverity).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              @change="(val: boolean) => handleToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button link type="primary" size="small" @click="openDryRun(row)">
              <el-icon><VideoPlay /></el-icon>仿真
            </el-button>
            <el-button link type="info" size="small" @click="openVersions(row)">
              <el-icon><Clock /></el-icon>版本
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ==================== 规则编辑对话框 ==================== -->
    <el-dialog
      v-model="editDialogVisible"
      :title="editMode === 'create' ? '新建规则' : '编辑规则'"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="120px"
        label-position="right"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则编码" prop="code">
              <el-input
                v-model="editForm.code"
                :disabled="editMode === 'edit'"
                placeholder="如 BUDGET_OVERRUN"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则名称" prop="name">
              <el-input v-model="editForm.name" placeholder="如 预算超支预警" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-input v-model="editForm.category" placeholder="如 BUDGET / RISK / EVM" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认严重度" prop="defaultSeverity">
              <el-select v-model="editForm.defaultSeverity" style="width: 100%">
                <el-option
                  v-for="opt in severityOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="条件表达式" prop="conditionExpression">
          <div class="expr-block">
            <ExpressionEditor
              v-model="editForm.conditionExpression"
              :fields="availableFields"
              placeholder="如: budgetUsageRatio >= 0.80 &amp;&amp; spi < 0.90"
              :validate-on-input="true"
              @validate="(v: boolean | null) => conditionValid = v"
            />
            <div class="expr-actions">
              <el-button size="small" :loading="validating" @click="handleValidate(editForm.conditionExpression, 'condition')">
                <el-icon><Check /></el-icon>后端校验
              </el-button>
              <el-tag
                v-if="conditionValid === true"
                type="success"
                size="small"
              >语法合法</el-tag>
              <el-tag
                v-else-if="conditionValid === false"
                type="danger"
                size="small"
              >语法不合法</el-tag>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="严重度表达式">
          <div class="expr-block">
            <ExpressionEditor
              v-model="editForm.severityExpression"
              :fields="availableFields"
              placeholder="如: budgetUsageRatio >= 0.95 ? 'RED' : 'YELLOW'"
              :validate-on-input="true"
              @validate="(v: boolean | null) => severityValid = v"
            />
            <div class="expr-actions">
              <el-button size="small" :loading="validating" @click="handleValidate(editForm.severityExpression || '', 'severity')">
                <el-icon><Check /></el-icon>后端校验
              </el-button>
              <el-tag v-if="severityValid === true" type="success" size="small">语法合法</el-tag>
              <el-tag v-else-if="severityValid === false" type="danger" size="small">语法不合法</el-tag>
            </div>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标题模板">
              <el-input v-model="editForm.titleTemplate" placeholder="预算超支预警：{projectName}" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="editForm.priority" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述模板">
          <el-input
            v-model="editForm.descriptionTemplate"
            type="textarea"
            :rows="2"
            placeholder="项目 {projectName} 预算已使用 {budgetUsedRatio}"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作用域">
              <el-input v-model="editForm.scope" placeholder="可选，如 PROJECT / TASK" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用">
              <el-switch v-model="editForm.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item v-if="editMode === 'edit'" label="变更说明">
          <el-input
            v-model="changeDesc"
            type="textarea"
            :rows="2"
            placeholder="本次变更内容说明（记录到版本历史）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">
          <el-icon><Check /></el-icon>保存
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== Dry-run 仿真对话框 ==================== -->
    <el-dialog v-model="dryRunDialogVisible" title="Dry-run 仿真" width="860px" :close-on-click-modal="false">
      <el-alert
        :title="`当前仿真目标：${dryRunRuleName}${dryRunRuleCode ? '（' + dryRunRuleCode + '）' : '（全部启用规则）'}`"
        type="info"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-form label-width="100px">
        <el-form-item label="事实数据">
          <el-input
            v-model="dryRunFactsText"
            type="textarea"
            :rows="10"
            placeholder='请输入 JSON 格式的事实数据，如 {"budgetUsedRatio": 0.95}'
            class="json-input"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="dryRunLoading" @click="handleDryRun">
            <el-icon><VideoPlay /></el-icon>执行仿真
          </el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">仿真结果</el-divider>
      <el-table :data="dryRunResults" border stripe size="small" empty-text="暂无仿真结果">
        <el-table-column prop="ruleCode" label="规则编码" width="160" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="category" label="类别" width="100" />
        <el-table-column label="是否触发" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
              {{ row.triggered ? '触发' : '未触发' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="严重度" width="90">
          <template #default="{ row }">
            <el-tag :type="severityOf(row.severity).type" size="small">
              {{ severityOf(row.severity).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="dryRunDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 模板市场对话框 ==================== -->
    <el-dialog v-model="templateDialogVisible" title="模板市场" width="920px">
      <div class="toolbar" style="margin-bottom: 12px">
        <div class="toolbar-left">
          <el-select
            v-model="templateCategoryFilter"
            placeholder="按类别筛选"
            clearable
            style="width: 180px"
          >
            <el-option v-for="c in templateCategoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
      </div>
      <el-table v-loading="templateLoading" :data="filteredTemplates" border stripe size="small">
        <el-table-column prop="templateCode" label="模板编码" width="180" show-overflow-tooltip />
        <el-table-column prop="templateName" label="模板名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类别" width="110">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="industry" label="行业" width="100" />
        <el-table-column label="默认严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityOf(row.defaultSeverity).type" size="small">
              {{ severityOf(row.defaultSeverity).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :loading="importingCodes.has(row.templateCode)"
              @click="handleImportTemplate(row)"
            >
              <el-icon><Download /></el-icon>导入
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="templateDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== AI 生成对话框 ==================== -->
    <el-dialog v-model="aiDialogVisible" title="AI 辅助生成规则" width="780px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="规则描述">
          <el-input
            v-model="aiDescription"
            type="textarea"
            :rows="4"
            placeholder="用自然语言描述规则，如：当预算使用率超过 90% 且进度偏差（SPI）低于 0.9 时触发红色预警"
          />
        </el-form-item>
        <el-form-item label="可用字段">
          <el-select
            v-model="aiFields"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入可用字段，辅助 AI 生成合法表达式"
            style="width: 100%"
          >
            <el-option v-for="f in fieldSuggestions" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="aiLoading" @click="handleAiGenerate">
            <el-icon><MagicStick /></el-icon>生成预览
          </el-button>
          <el-button type="success" :loading="aiLoading" @click="handleAiGenerateAndSave">
            <el-icon><Check /></el-icon>生成并保存
          </el-button>
        </el-form-item>
      </el-form>

      <template v-if="aiResult">
        <el-divider content-position="left">生成结果预览</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="规则编码">{{ aiResult.code }}</el-descriptions-item>
          <el-descriptions-item label="规则名称">{{ aiResult.name }}</el-descriptions-item>
          <el-descriptions-item label="类别">{{ aiResult.category }}</el-descriptions-item>
          <el-descriptions-item label="默认严重度">
            <el-tag :type="severityOf(aiResult.defaultSeverity).type" size="small">
              {{ severityOf(aiResult.defaultSeverity).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="条件表达式" :span="2">
            <code class="expr-code">{{ aiResult.conditionExpression }}</code>
          </el-descriptions-item>
          <el-descriptions-item v-if="aiResult.severityExpression" label="严重度表达式" :span="2">
            <code class="expr-code">{{ aiResult.severityExpression }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="标题模板" :span="2">
            {{ aiResult.titleTemplate || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="ai-result-actions">
          <el-button type="primary" @click="loadAiResultToEdit">
            <el-icon><Edit /></el-icon>载入编辑
          </el-button>
        </div>
      </template>
      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 版本历史对话框 ==================== -->
    <el-dialog v-model="versionDialogVisible" title="版本历史" width="780px">
      <el-table v-loading="versionLoading" :data="versions" border stripe size="small">
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeDesc" label="变更说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click="handleRollback(row.version)">
              <el-icon><RefreshLeft /></el-icon>回滚
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="versionDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 冲突检测对话框 ==================== -->
    <el-dialog v-model="conflictDialogVisible" title="规则冲突检测" width="780px">
      <el-alert
        v-if="conflicts.length > 0"
        :title="`检测到 ${conflicts.length} 个潜在冲突`"
        type="warning"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-table :data="conflicts" border stripe size="small">
        <el-table-column label="严重程度" width="90">
          <template #default="{ row }">
            <el-tag
              :type="row.severity === 'high' ? 'danger' : row.severity === 'medium' ? 'warning' : 'info'"
              size="small"
            >
              {{ row.severity === 'high' ? '高' : row.severity === 'medium' ? '中' : '低' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则 A" width="160">
          <template #default="{ row }">
            <div>{{ row.ruleAName }}</div>
            <div style="font-size:11px;color:#999">{{ row.ruleA }}</div>
          </template>
        </el-table-column>
        <el-table-column label="规则 B" width="160">
          <template #default="{ row }">
            <div>{{ row.ruleBName }}</div>
            <div style="font-size:11px;color:#999">{{ row.ruleB }}</div>
          </template>
        </el-table-column>
        <el-table-column label="重叠字段" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="f in row.overlapFields"
              :key="f"
              size="small"
              type="info"
              effect="plain"
              style="margin-right:4px;margin-bottom:2px"
            >{{ f }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="conflictDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 执行回放对话框 ==================== -->
    <el-dialog v-model="traceDialogVisible" title="执行链路回放" width="960px" :close-on-click-modal="false">
      <el-row :gutter="16">
        <!-- 左侧：链路列表 -->
        <el-col :span="10">
          <div class="trace-list-header">最近执行链路</div>
          <el-table
            v-loading="traceLoading"
            :data="recentTraces"
            border
            stripe
            size="small"
            height="400"
            highlight-current-row
            @row-click="(row: ExecutionTrace) => handleReplay(row.traceId)"
          >
            <el-table-column prop="traceId" label="Trace ID" width="140" show-overflow-tooltip />
            <el-table-column prop="ruleCode" label="规则编码" width="140" show-overflow-tooltip />
            <el-table-column label="触发" width="60" align="center">
              <template #default="{ row }">
                <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
                  {{ row.triggered ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="160" />
          </el-table>
        </el-col>

        <!-- 右侧：回放结果 -->
        <el-col :span="14">
          <div v-loading="replayLoading">
            <template v-if="replayResult">
              <!-- 差异摘要 -->
              <el-alert :title="replayResult.diff.summary" type="info" :closable="false" show-icon class="mb-3" />

              <!-- 差异详情 -->
              <el-row :gutter="8" class="diff-row">
                <el-col :span="8">
                  <el-card shadow="hover" class="diff-card diff-added">
                    <div class="diff-card-title">新增触发</div>
                    <div class="diff-card-count">{{ replayResult.diff.added.length }}</div>
                    <div class="diff-card-list">
                      <el-tag v-for="code in replayResult.diff.added" :key="code" type="danger" size="small" style="margin:2px">
                        {{ code }}
                      </el-tag>
                      <span v-if="replayResult.diff.added.length === 0" class="diff-empty">无</span>
                    </div>
                  </el-card>
                </el-col>
                <el-col :span="8">
                  <el-card shadow="hover" class="diff-card diff-removed">
                    <div class="diff-card-title">移除触发</div>
                    <div class="diff-card-count">{{ replayResult.diff.removed.length }}</div>
                    <div class="diff-card-list">
                      <el-tag v-for="code in replayResult.diff.removed" :key="code" type="warning" size="small" style="margin:2px">
                        {{ code }}
                      </el-tag>
                      <span v-if="replayResult.diff.removed.length === 0" class="diff-empty">无</span>
                    </div>
                  </el-card>
                </el-col>
                <el-col :span="8">
                  <el-card shadow="hover" class="diff-card diff-unchanged">
                    <div class="diff-card-title">保持不变</div>
                    <div class="diff-card-count">{{ replayResult.diff.unchanged.length }}</div>
                    <div class="diff-card-list">
                      <el-tag v-for="code in replayResult.diff.unchanged" :key="code" type="success" size="small" style="margin:2px">
                        {{ code }}
                      </el-tag>
                      <span v-if="replayResult.diff.unchanged.length === 0" class="diff-empty">无</span>
                    </div>
                  </el-card>
                </el-col>
              </el-row>

              <!-- 当前评估结果 -->
              <el-divider content-position="left">当前规则评估结果</el-divider>
              <el-table :data="replayResult.currentResults" border stripe size="small" max-height="200">
                <el-table-column prop="ruleCode" label="规则编码" width="140" show-overflow-tooltip />
                <el-table-column prop="ruleName" label="规则名称" min-width="140" show-overflow-tooltip />
                <el-table-column label="触发" width="60" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
                      {{ row.triggered ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="严重度" width="80">
                  <template #default="{ row }">
                    <el-tag :type="severityOf(row.severity).type" size="small">
                      {{ severityOf(row.severity).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="elapsedMs" label="耗时(ms)" width="80" />
              </el-table>
            </template>
            <el-empty v-else description="选择左侧链路进行回放" />
          </div>
        </el-col>
      </el-row>
      <template #footer>
        <el-button @click="traceDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 回归测试对话框 ==================== -->
    <el-dialog v-model="regressionDialogVisible" title="回归测试报告" width="900px" :close-on-click-modal="false">
      <div v-loading="regressionLoading">
        <template v-if="regressionReport">
          <!-- 通过率概览 -->
          <el-row :gutter="12" class="stats-row">
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-label">总用例数</div>
                <div class="stat-value">{{ regressionReport.total }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-label">通过</div>
                <div class="stat-value" style="color:#67c23a">{{ regressionReport.passed }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-label">失败</div>
                <div class="stat-value" style="color:#f56c6c">{{ regressionReport.failed }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-label">通过率</div>
                <div class="stat-value" :style="{ color: regressionReport.allPassed ? '#67c23a' : '#f56c6c' }">
                  {{ regressionReport.passRate }}
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 用例详情 -->
          <el-table :data="regressionReport.caseResults" border stripe size="small" max-height="400">
            <el-table-column label="结果" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.pass ? 'success' : 'danger'" size="small">
                  {{ row.pass ? '通过' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="testCaseName" label="用例名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="ruleCode" label="关联规则" width="140" show-overflow-tooltip />
            <el-table-column label="缺失触发" min-width="120">
              <template #default="{ row }">
                <el-tag v-for="code in row.missing" :key="code" type="warning" size="small" style="margin:1px">
                  {{ code }}
                </el-tag>
                <span v-if="row.missing.length === 0" style="color:#999">-</span>
              </template>
            </el-table-column>
            <el-table-column label="意外触发" min-width="120">
              <template #default="{ row }">
                <el-tag v-for="code in row.unexpected" :key="code" type="danger" size="small" style="margin:1px">
                  {{ code }}
                </el-tag>
                <span v-if="row.unexpected.length === 0" style="color:#999">-</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="正在执行回归测试..." />
      </div>
      <template #footer>
        <el-button @click="regressionDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.rule-engine-page {
  .stats-row {
    margin-bottom: $spacing-md;

    .stat-card {
      text-align: center;
      .stat-label {
        font-size: $font-size-sm;
        color: $text-secondary;
      }
      .stat-value {
        font-size: $font-size-2xl;
        font-weight: 700;
        margin-top: $spacing-xs;
        color: $text-primary;

        &.highlight-warning {
          color: $warning-color;
        }
        &.highlight-danger {
          color: $danger-color;
        }
      }
    }
  }

  .stats-chart-card {
    margin-bottom: $spacing-md;
  }

  .main-card {
    .toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: $spacing-md;
      gap: $spacing-sm;
      flex-wrap: wrap;

      .toolbar-left {
        display: flex;
        align-items: center;
        gap: $spacing-sm;
        flex-wrap: wrap;
      }
      .toolbar-right {
        display: flex;
        align-items: center;
        gap: $spacing-sm;
        flex-wrap: wrap;
      }
    }
  }

  .expr-block {
    width: 100%;

    .expr-actions {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      margin-top: $spacing-xs;
    }
  }

  .json-input {
    :deep(textarea) {
      font-family: 'Courier New', Consolas, monospace;
      font-size: $font-size-sm;
    }
  }

  .expr-code {
    display: block;
    padding: $spacing-xs $spacing-sm;
    background: $bg-page;
    border-radius: $border-radius-base;
    font-family: 'Courier New', Consolas, monospace;
    font-size: $font-size-sm;
    word-break: break-all;
  }

  .ai-result-actions {
    margin-top: $spacing-md;
    text-align: right;
  }

  .mb-3 {
    margin-bottom: $spacing-md;
  }

  .trace-list-header {
    font-size: $font-size-sm;
    font-weight: 600;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
  }

  .diff-row {
    margin-bottom: $spacing-md;

    .diff-card {
      text-align: center;

      .diff-card-title {
        font-size: $font-size-sm;
        color: $text-secondary;
      }

      .diff-card-count {
        font-size: $font-size-2xl;
        font-weight: 700;
        margin: $spacing-xs 0;
      }

      .diff-card-list {
        min-height: 30px;
      }

      .diff-empty {
        color: $text-placeholder;
        font-size: $font-size-sm;
      }

      &.diff-added .diff-card-count {
        color: $danger-color;
      }

      &.diff-removed .diff-card-count {
        color: $warning-color;
      }

      &.diff-unchanged .diff-card-count {
        color: $success-color;
      }
    }
  }
}
</style>
