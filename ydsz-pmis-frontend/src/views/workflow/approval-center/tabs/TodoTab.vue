<script setup lang="ts">
/**
 * @file 待办任务 Tab
 * @module views/workflow/approval-center/tabs/TodoTab
 * @description
 *   从原 index.vue 拆分而来，负责"我的待办"完整功能：
 *     1. 快捷筛选栏：紧急程度 / 流程类型 / 发起时间范围
 *     2. 待办置顶/标记：localStorage 持久化
 *     3. 自定义列显隐
 *     4. 列表快捷操作：一键通过、批量通过、超时高亮
 *     5. 任务操作弹窗：通过/驳回/转办/委派/加签/暂存/沟通/催办等
 *   审批操作逻辑通过 useApprovalActions（策略模式）注入。
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useResponsive } from '@/composables/useResponsive'
import { useWebSocket } from '@/composables/useWebSocket'
import { pageTodoTasks, pageDefinitions } from '@/api/workflow'
import type {
  FlowTaskDTO,
  FlowTaskQuery,
  FlowDefinitionDTO,
} from '@/api/workflow/types'
import { UserPicker, CommentEditor, BatchToolbar } from '@/components/common'
import type { BatchAction } from '@/components/common'
import {
  useApprovalActions,
  isOverdue,
  isNearlyOverdue,
  taskStatusLabel,
  formatTime,
} from '../composables/useApprovalActions'

const emit = defineEmits<{
  /** 数据变更后通知父组件刷新角标 */
  (e: 'refresh-badge'): void
}>()

const router = useRouter()
const { t } = useI18n()
const { isMobile } = useResponsive()

// ===========================================
// 筛选状态
// ===========================================
const urgencyFilter = ref<'all' | 'nearly-overdue' | 'overdue'>('all')
const flowTypeFilter = ref<string | undefined>(undefined)
const flowDefinitions = ref<FlowDefinitionDTO[]>([])
const dateRange = ref<[string, string] | null>(null)

// P1-3: 快捷标签 chips（今日到期 / 本周到期 / 超期 / 紧急）
type QuickTag = 'today' | 'thisWeek' | 'overdue' | 'urgent' | null
const activeQuickTag = ref<QuickTag>(null)

const quickTagOptions: { value: QuickTag; label: string; type: 'danger' | 'warning' | 'info' }[] = [
  { value: 'overdue', label: '已超期', type: 'danger' },
  { value: 'today', label: '今日到期', type: 'warning' },
  { value: 'thisWeek', label: '本周到期', type: 'info' },
  { value: 'urgent', label: '紧急', type: 'danger' },
]

function onQuickTagClick(tag: QuickTag) {
  activeQuickTag.value = activeQuickTag.value === tag ? null : tag
  applyQuickTag()
  onFilterChange()
}

function applyQuickTag() {
  const tag = activeQuickTag.value
  if (!tag) {
    urgencyFilter.value = 'all'
    return
  }
  const now = new Date()
  const todayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  const weekEnd = new Date(now)
  weekEnd.setDate(now.getDate() + (7 - now.getDay()))
  weekEnd.setHours(23, 59, 59)
  switch (tag) {
    case 'overdue':
      urgencyFilter.value = 'overdue'
      dateRange.value = null
      break
    case 'today':
      urgencyFilter.value = 'all'
      dateRange.value = [
        new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString().replace('T', ' ').slice(0, 19),
        todayEnd.toISOString().replace('T', ' ').slice(0, 19),
      ]
      break
    case 'thisWeek':
      urgencyFilter.value = 'all'
      dateRange.value = [
        new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString().replace('T', ' ').slice(0, 19),
        weekEnd.toISOString().replace('T', ' ').slice(0, 19),
      ]
      break
    case 'urgent':
      urgencyFilter.value = 'all'
      dateRange.value = null
      break
  }
}

// P1-3: 持久化筛选条件（urgencyFilter / flowTypeFilter / dateRange）
const FILTER_STORAGE_KEY = 'approval_center_filters'

function saveFilters() {
  try {
    localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify({
      urgencyFilter: urgencyFilter.value,
      flowTypeFilter: flowTypeFilter.value,
      activeQuickTag: activeQuickTag.value,
    }))
  } catch {
    // 静默失败
  }
}

function loadFilters() {
  try {
    const raw = localStorage.getItem(FILTER_STORAGE_KEY)
    if (raw) {
      const saved = JSON.parse(raw)
      if (saved.urgencyFilter) urgencyFilter.value = saved.urgencyFilter
      if (saved.flowTypeFilter !== undefined) flowTypeFilter.value = saved.flowTypeFilter
      if (saved.activeQuickTag) activeQuickTag.value = saved.activeQuickTag
    }
  } catch {
    // 使用默认值
  }
}

// 监听筛选变化自动保存
watch([urgencyFilter, flowTypeFilter, dateRange, activeQuickTag], saveFilters)

// P1-3: 按流程类型聚合展示模式（平铺 / 分组）— 声明 groupByFlow，分组逻辑在 todoList 声明后
const groupByFlow = ref(false)

// P1-3: 紧急标签筛选（urgent = priority >= 76）
function isUrgentTask(task: FlowTaskDTO): boolean {
  return (task.priority ?? 50) >= 76
}

async function loadFlowDefinitions() {
  try {
    const res = await pageDefinitions({ status: 'PUBLISHED', pageNum: 1, pageSize: 200 })
    if (res.data?.code === 0) {
      // 后端分页返回 records 字段（MyBatis-Plus Page），与前端 PageResult.list 类型不一致，用类型断言收敛
      const pageData = res.data?.data as unknown as { records?: FlowDefinitionDTO[] } | undefined
      flowDefinitions.value = pageData?.records || []
    }
  } catch {
    // 静默失败
  }
}

// ===========================================
// 待办置顶（localStorage）
// ===========================================
const PINNED_STORAGE_KEY = 'approval_center_pinned_tasks'
const pinnedTaskIds = ref<Set<number>>(new Set())

function loadPinnedTasks() {
  try {
    const raw = localStorage.getItem(PINNED_STORAGE_KEY)
    if (raw) {
      const arr: number[] = JSON.parse(raw)
      pinnedTaskIds.value = new Set(arr)
    }
  } catch {
    pinnedTaskIds.value = new Set()
  }
}

function savePinnedTasks() {
  try {
    localStorage.setItem(PINNED_STORAGE_KEY, JSON.stringify([...pinnedTaskIds.value]))
  } catch {
    // 静默失败
  }
}

function togglePin(taskId: number) {
  if (pinnedTaskIds.value.has(taskId)) {
    pinnedTaskIds.value.delete(taskId)
  } else {
    pinnedTaskIds.value.add(taskId)
  }
  savePinnedTasks()
  sortTodoList()
}

function isPinned(taskId: number): boolean {
  return pinnedTaskIds.value.has(taskId)
}

// ===========================================
// 自定义列显隐
// ===========================================
interface ColumnOption {
  key: string
  label: string
  fixed?: boolean
}
const columnOptions = computed<ColumnOption[]>(() => [
  { key: 'selection', label: t('workflow.approval.columns.selection'), fixed: true },
  { key: 'pin', label: t('workflow.approval.columns.pin'), fixed: true },
  { key: 'title', label: t('workflow.approval.columns.title') },
  { key: 'flowName', label: t('workflow.approval.columns.flowName') },
  { key: 'nodeName', label: t('workflow.approval.columns.nodeName') },
  { key: 'assignorName', label: t('workflow.approval.columns.assignorName') },
  { key: 'priority', label: '优先级' },
  { key: 'createTime', label: t('workflow.approval.columns.createTime') },
  { key: 'status', label: t('workflow.approval.columns.status') },
  { key: 'operation', label: t('workflow.approval.columns.operation'), fixed: true },
])
const visibleColumns = ref<string[]>(['selection', 'pin', 'title', 'flowName', 'nodeName', 'assignorName', 'priority', 'createTime', 'status', 'operation'])

function loadColumnPrefs() {
  try {
    const raw = localStorage.getItem('approval_center_columns')
    if (raw) {
      const arr: string[] = JSON.parse(raw)
      if (arr.length > 0) visibleColumns.value = arr
    }
  } catch {
    // 使用默认值
  }
}

function saveColumnPrefs() {
  try {
    localStorage.setItem('approval_center_columns', JSON.stringify(visibleColumns.value))
  } catch {
    // 静默失败
  }
}

function isColumnVisible(key: string): boolean {
  return visibleColumns.value.includes(key)
}

// P1-1: 优先级标签
function priorityTag(priority?: number): { label: string; type: 'danger' | 'warning' | 'info' | 'success' } {
  const p = priority ?? 50
  if (p >= 76) return { label: '紧急', type: 'danger' }
  if (p >= 51) return { label: '高', type: 'warning' }
  if (p >= 26) return { label: '中', type: 'info' }
  return { label: '低', type: 'success' }
}

// ===========================================
// 待办列表数据
// ===========================================
const todoQuery = reactive<FlowTaskQuery>({
  pageNum: 1,
  pageSize: 20,
  flowCode: undefined,
})
const todoList = ref<FlowTaskDTO[]>([])
const todoTotal = ref(0)
const todoLoading = ref(false)
const todoSelection = ref<FlowTaskDTO[]>([])

// P1-3: 分组模式逻辑（在 todoList 声明后定义）
interface FlowGroup {
  flowCode: string
  flowName: string
  tasks: FlowTaskDTO[]
  _expanded: boolean
  _selected: FlowTaskDTO[]
}
const todoGroupedByFlow = ref<FlowGroup[]>([])

watch([todoList, groupByFlow], () => {
  if (!groupByFlow.value) {
    todoGroupedByFlow.value = []
    return
  }
  const map = new Map<string, FlowGroup>()
  for (const task of todoList.value) {
    const code = task.flowCode || '_unknown'
    if (!map.has(code)) {
      map.set(code, {
        flowCode: code,
        flowName: task.flowName || task.flowCode || '未知流程',
        tasks: [],
        _expanded: true,
        _selected: [],
      })
    }
    map.get(code)!.tasks.push(task)
  }
  todoGroupedByFlow.value = Array.from(map.values()).sort((a, b) => b.tasks.length - a.tasks.length)
}, { immediate: true })

/** 分组模式下同步选中状态到 todoSelection */
function onGroupSelectionChange(flowCode: string, rows: FlowTaskDTO[]) {
  const group = todoGroupedByFlow.value.find((g) => g.flowCode === flowCode)
  if (group) {
    group._selected = rows
  }
  const all: FlowTaskDTO[] = []
  for (const g of todoGroupedByFlow.value) {
    all.push(...g._selected)
  }
  todoSelection.value = all
}

/** 表格行样式 */
function tableRowClassName({ row }: { row: FlowTaskDTO }): string {
  if (isOverdue(row)) return 'row-overdue'
  if (isNearlyOverdue(row)) return 'row-nearly-overdue'
  if (pinnedTaskIds.value.has(row.id)) return 'row-pinned'
  return ''
}

/** 对当前列表排序（置顶优先 → priority DESC → createTime ASC） */
function sortTodoList() {
  todoList.value = [...todoList.value].sort((a, b) => {
    const aPinned = pinnedTaskIds.value.has(a.id) ? 1 : 0
    const bPinned = pinnedTaskIds.value.has(b.id) ? 1 : 0
    if (aPinned !== bPinned) return bPinned - aPinned
    // P1-1: priority DESC（高优先级在前）
    const bPri = b.priority ?? 50
    const aPri = a.priority ?? 50
    if (bPri !== aPri) return bPri - aPri
    return new Date(a.createTime || 0).getTime() - new Date(b.createTime || 0).getTime()
  })
}

async function loadTodo() {
  todoLoading.value = true
  try {
    const params: FlowTaskQuery = {
      ...todoQuery,
      flowCode: flowTypeFilter.value || todoQuery.flowCode || undefined,
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }

    const res = await pageTodoTasks(params)
    if (res.data?.code === 0) {
      // 后端分页返回 records/total 字段（MyBatis-Plus Page），与前端 PageResult 类型不一致，用类型断言收敛
      const pageData = res.data?.data as unknown as { records?: FlowTaskDTO[]; total?: number } | undefined
      let records = pageData?.records || []
      const total = pageData?.total || 0

      // 紧急程度筛选（客户端过滤）
      if (urgencyFilter.value === 'nearly-overdue') {
        records = records.filter((t) => isNearlyOverdue(t))
      } else if (urgencyFilter.value === 'overdue') {
        records = records.filter((t) => isOverdue(t))
      }
      // P1-3: 紧急标签客户端过滤（priority >= 76）
      if (activeQuickTag.value === 'urgent') {
        records = records.filter((t) => isUrgentTask(t))
      }

      // 排序：置顶优先 → priority DESC → createTime ASC
      records.sort((a, b) => {
        const aPinned = pinnedTaskIds.value.has(a.id) ? 1 : 0
        const bPinned = pinnedTaskIds.value.has(b.id) ? 1 : 0
        if (aPinned !== bPinned) return bPinned - aPinned
        // P1-1: priority DESC（高优先级在前）
        const bPri = b.priority ?? 50
        const aPri = a.priority ?? 50
        if (bPri !== aPri) return bPri - aPri
        return new Date(a.createTime || 0).getTime() - new Date(b.createTime || 0).getTime()
      })

      todoList.value = records
      todoTotal.value = urgencyFilter.value !== 'all' ? records.length : total
      emit('refresh-badge')
    }
  } finally {
    todoLoading.value = false
  }
}

/** 重置筛选条件 */
function resetTodoFilters() {
  urgencyFilter.value = 'all'
  flowTypeFilter.value = undefined
  dateRange.value = null
  activeQuickTag.value = null
  todoQuery.flowCode = undefined
  todoQuery.pageNum = 1
  loadTodo()
}

/** 筛选条件变化时重置页码并查询 */
function onFilterChange() {
  todoQuery.pageNum = 1
  loadTodo()
}

// ===========================================
// 审批操作（策略模式）
// ===========================================
const {
  opDialog,
  opForm,
  opDialogTitle,
  showCommentInput,
  showTargetUser,
  showRejectNode,
  commentLabel,
  commentPlaceholder,
  targetUserLabel,
  targetUserDialogTitle,
  commentPhrases,
  openOpDialog,
  submitOp,
  onTargetUserChange,
  onOpMention,
  quickPass,
  quickClaim,
  quickSaveDraft,
  quickMarkRead,
  quickCommunicate,
  quickUrge,
  quickBatchPass,
  quickBatchClaim,
  quickBatchMarkRead,
  quickPassAll,
  freeJump,
} = useApprovalActions({
  onSuccess: () => {
    loadTodo()
    emit('refresh-badge')
  },
})

// ===========================================
// P1-3: 批量操作工具栏配置
// ===========================================
const batchActions = computed<BatchAction[]>(() => [
  {
    label: `批量通过 (${todoSelection.value.length})`,
    type: 'primary',
    handler: () => quickBatchPass(todoSelection.value.map((t) => t.id)),
  },
  {
    label: '批量签收',
    type: 'success',
    handler: () => quickBatchClaim(todoSelection.value),
  },
  {
    label: '批量已阅',
    type: 'info',
    handler: () => quickBatchMarkRead(todoSelection.value),
  },
])

function clearSelection() {
  todoSelection.value = []
}

// ===========================================
// 跳转
// ===========================================
function goInstance(instanceId: number) {
  router.push({ path: '/workflow/instance', query: { id: String(instanceId) } })
}

// ===========================================
// 生命周期
// ===========================================
// P0-1: WebSocket 实时推送 — 任务变更时自动刷新待办列表
const { on: onWs } = useWebSocket()
let wsRefreshTimer: ReturnType<typeof setTimeout> | null = null

/** 防抖刷新：短时间内多次 WS 推送只触发一次列表刷新 */
function debouncedRefresh() {
  if (wsRefreshTimer) clearTimeout(wsRefreshTimer)
  wsRefreshTimer = setTimeout(() => {
    loadTodo()
  }, 500)
}

onMounted(() => {
  loadPinnedTasks()
  loadColumnPrefs()
  loadFilters()
  loadFlowDefinitions()
  loadTodo()
  // WebSocket 监听任务变更
  onWs('TASK_ASSIGNED', () => debouncedRefresh())
  onWs('TASK_COMPLETED', () => debouncedRefresh())
  onWs('TASK_REJECTED', () => debouncedRefresh())
})
</script>

<template>
  <div class="todo-tab">
    <!-- 快捷筛选栏 -->
    <div class="filter-bar-enhanced">
      <div class="filter-bar-enhanced__row">
        <!-- 紧急程度 -->
        <el-radio-group v-model="urgencyFilter" size="small" @change="onFilterChange">
          <el-radio-button value="all">{{ t('common.all') }}</el-radio-button>
          <el-radio-button value="nearly-overdue">
            {{ t('workflow.approval.urgency.nearlyOverdue') }}
            <el-tooltip :content="t('workflow.approval.urgency.nearlyOverdueTip')" placement="top">
              <el-icon style="margin-left: 2px"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-radio-button>
          <el-radio-button value="overdue">{{ t('workflow.approval.urgency.overdue') }}</el-radio-button>
        </el-radio-group>

        <!-- 流程类型 -->
        <el-select
          v-model="flowTypeFilter"
          :placeholder="t('workflow.approval.filter.flowType')"
          clearable
          filterable
          size="small"
          style="width: 200px"
          @change="onFilterChange"
        >
          <el-option
            v-for="def in flowDefinitions"
            :key="def.id"
            :label="def.flowName"
            :value="def.flowCode"
          />
        </el-select>

        <!-- 发起时间范围 -->
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          :range-separator="t('workflow.approval.filter.dateSeparator')"
          :start-placeholder="t('workflow.approval.filter.startDate')"
          :end-placeholder="t('workflow.approval.filter.endDate')"
          size="small"
          style="width: 260px"
          value-format="YYYY-MM-DD HH:mm:ss"
          :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
          @change="onFilterChange"
        />

        <el-button size="small" @click="resetTodoFilters">{{ t('workflow.approval.filter.reset') }}</el-button>
      </div>

      <div class="filter-bar-enhanced__row">
        <el-input
          v-model="todoQuery.flowCode"
          :placeholder="t('workflow.approval.filter.flowCodeKeywordPlaceholder')"
          clearable
          size="small"
          style="width: 200px"
          @keyup.enter="loadTodo"
        />
        <el-button type="primary" size="small" @click="loadTodo">{{ t('workflow.approval.buttons.query') }}</el-button>

        <!-- GAP-P0-4: 一键通过所有待办（上限 100 条） -->
        <el-button type="success" size="small" @click="quickPassAll">
          {{ t('workflow.approval.buttons.passAll') }}
        </el-button>

        <!-- P1-3: 分组展示切换 -->
        <el-tooltip content="按流程类型分组展示" placement="top">
          <el-switch
            v-model="groupByFlow"
            inline-prompt
            active-text="分组"
            inactive-text="平铺"
            size="small"
          />
        </el-tooltip>

        <!-- 列显隐控制 -->
        <el-popover placement="bottom-end" :width="200" trigger="click">
          <template #reference>
            <el-button size="small" style="margin-left: auto">
              <el-icon><Setting /></el-icon>
              {{ t('workflow.approval.buttons.columnSettings') }}
            </el-button>
          </template>
          <el-checkbox-group v-model="visibleColumns" @change="saveColumnPrefs">
            <div v-for="col in columnOptions" :key="col.key" style="margin-bottom: 6px">
              <el-checkbox :value="col.key" :disabled="col.fixed" :label="col.key">
                {{ col.label }}
                <el-tag v-if="col.fixed" size="small" type="info" style="margin-left: 4px">
                  {{ t('workflow.approval.columns.fixed') }}
                </el-tag>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </el-popover>
      </div>

      <!-- P1-3: 快捷标签 chips -->
      <div class="filter-bar-enhanced__row filter-bar-enhanced__row--chips">
        <span class="chips-label">快捷筛选：</span>
        <el-tag
          v-for="tag in quickTagOptions"
          :key="tag.value || 'none'"
          :type="activeQuickTag === tag.value ? tag.type : 'info'"
          :effect="activeQuickTag === tag.value ? 'dark' : 'plain'"
          size="small"
          class="chip-tag"
          @click="onQuickTagClick(tag.value)"
        >
          {{ tag.label }}
        </el-tag>
      </div>
    </div>

    <!-- P1-3: 批量操作工具栏（替换原 inline 批量通过按钮） -->
    <BatchToolbar
      :selected-count="todoSelection.length"
      :actions="batchActions"
      @clear="clearSelection"
    />

    <!-- 待办表格（平铺模式） -->
    <el-table
      v-if="!groupByFlow"
      v-loading="todoLoading"
      :data="todoList"
      stripe
      :row-class-name="tableRowClassName"
      @selection-change="(v: FlowTaskDTO[]) => (todoSelection = v)"
    >
      <el-table-column
        v-if="isColumnVisible('selection')"
        type="selection"
        width="50"
      />
      <el-table-column
        v-if="isColumnVisible('pin')"
        :label="t('workflow.approval.columns.pin')"
        width="60"
        align="center"
      >
        <template #default="{ row }">
          <el-button
            :type="isPinned(row.id) ? 'warning' : 'default'"
            :icon="isPinned(row.id) ? 'StarFilled' : 'Star'"
            size="small"
            text
            :title="isPinned(row.id) ? t('workflow.approval.actions.unpin') : t('workflow.approval.actions.pin')"
            @click="togglePin(row.id)"
          />
        </template>
      </el-table-column>
      <el-table-column
        v-if="isColumnVisible('title')"
        prop="title"
        :label="t('workflow.approval.columns.title')"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="isColumnVisible('flowName')"
        prop="flowName"
        :label="t('workflow.approval.columns.flowName')"
        width="160"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="isColumnVisible('nodeName')"
        prop="nodeName"
        :label="t('workflow.approval.columns.nodeName')"
        width="120"
      />
      <el-table-column
        v-if="isColumnVisible('assignorName')"
        prop="assignorName"
        :label="t('workflow.approval.columns.assignorName')"
        width="100"
      />
      <!-- P1-1: 任务优先级 -->
      <el-table-column
        v-if="isColumnVisible('priority')"
        label="优先级"
        width="80"
        align="center"
      >
        <template #default="{ row }">
          <el-tag :type="priorityTag(row.priority).type" size="small" effect="light">
            {{ priorityTag(row.priority).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="isColumnVisible('createTime')"
        :label="t('workflow.approval.columns.createTime')"
        width="160"
      >
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column
        v-if="isColumnVisible('status')"
        :label="t('workflow.approval.columns.status')"
        width="120"
      >
        <template #default="{ row }">
          <el-tag v-if="isOverdue(row as FlowTaskDTO)" type="danger" size="small" effect="dark">
            {{ t('workflow.approval.urgency.overdueTag') }}
          </el-tag>
          <el-tag
            v-else-if="isNearlyOverdue(row as FlowTaskDTO)"
            type="warning"
            size="small"
            effect="dark"
          >
            {{ t('workflow.approval.urgency.nearlyOverdueTag') }}
          </el-tag>
          <el-tag
            v-else
            :type="taskStatusLabel(row.taskStatus).type"
            size="small"
          >
            {{ taskStatusLabel(row.taskStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="isColumnVisible('operation')"
        :label="t('workflow.approval.columns.operation')"
        :width="isMobile ? 110 : 420"
        fixed="right"
      >
        <template #default="{ row }">
          <template v-if="row.taskStatus === 'PENDING' || row.taskStatus === 'CLAIMED'">
            <!-- 一键通过（PC 端显示，移动端收进 dropdown） -->
            <el-button
              v-if="row.taskStatus === 'PENDING' && !isMobile"
              type="success"
              size="small"
              @click="quickPass(row as FlowTaskDTO)"
            >
              {{ t('workflow.approval.actions.quickPass') }}
            </el-button>
            <!-- 通过（弹窗，PC 端显示，移动端收进 dropdown） -->
            <el-button
              v-if="row.taskStatus === 'PENDING' && !isMobile"
              type="primary"
              size="small"
              @click="openOpDialog('PASS', row as FlowTaskDTO)"
            >
              {{ t('workflow.approval.pass') }}
            </el-button>
            <!-- 驳回（PC 端显示，移动端收进 dropdown） -->
            <el-button
              v-if="row.taskStatus === 'PENDING' && !isMobile"
              type="danger"
              size="small"
              @click="openOpDialog('REJECT', row as FlowTaskDTO)"
            >
              {{ t('workflow.approval.reject') }}
            </el-button>
            <!-- 更多操作（移动端承载全部操作） -->
            <el-dropdown size="small">
              <el-button size="small">
                {{ isMobile ? '操作' : t('workflow.approval.actions.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <!-- 移动端独有：一键通过 / 通过 / 驳回 -->
                  <el-dropdown-item v-if="row.taskStatus === 'PENDING' && isMobile" @click="quickPass(row as FlowTaskDTO)">
                    {{ t('workflow.approval.actions.quickPass') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.taskStatus === 'PENDING' && isMobile" divided @click="openOpDialog('PASS', row as FlowTaskDTO)">
                    {{ t('workflow.approval.pass') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.taskStatus === 'PENDING' && isMobile" @click="openOpDialog('REJECT', row as FlowTaskDTO)">
                    {{ t('workflow.approval.reject') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.taskStatus === 'PENDING' && !isMobile" @click="quickClaim(row as FlowTaskDTO)">
                    {{ t('workflow.task.claim') }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="quickSaveDraft(row as FlowTaskDTO)">{{ t('workflow.approval.actions.saveDraft') }}</el-dropdown-item>
                  <el-dropdown-item @click="quickMarkRead(row as FlowTaskDTO)">{{ t('workflow.approval.actions.markRead') }}</el-dropdown-item>
                  <el-dropdown-item @click="quickCommunicate(row as FlowTaskDTO)">{{ t('workflow.approval.actions.communicate') }}</el-dropdown-item>
                  <el-dropdown-item v-if="!isMobile" divided @click="openOpDialog('TRANSFER', row as FlowTaskDTO)">
                    {{ t('workflow.task.transfer') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isMobile" @click="openOpDialog('DELEGATE', row as FlowTaskDTO)">{{ t('workflow.task.delegate') }}</el-dropdown-item>
                  <el-dropdown-item v-if="!isMobile" @click="openOpDialog('ADD_APPROVER', row as FlowTaskDTO)">
                    {{ t('workflow.approval.actions.addApprover') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isMobile" divided @click="openOpDialog('COUNTERSIGN_BEFORE', row as FlowTaskDTO)">
                    {{ t('workflow.approval.actions.countersignBefore') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isMobile" @click="openOpDialog('COUNTERSIGN_AFTER', row as FlowTaskDTO)">
                    {{ t('workflow.approval.actions.countersignAfter') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isMobile" @click="openOpDialog('COUNTERSIGN_PARALLEL', row as FlowTaskDTO)">
                    {{ t('workflow.approval.actions.countersignParallel') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isMobile" @click="openOpDialog('COUNTERSIGN_REMOVE', row as FlowTaskDTO)">
                    {{ t('workflow.approval.actions.countersignRemove') }}
                  </el-dropdown-item>
                  <!-- GAP-P2-9: 自由流跳转 — 仅 PC 端，目标节点需 ext.freeJump=true -->
                  <el-dropdown-item v-if="!isMobile && row.taskStatus === 'PENDING'" divided @click="freeJump(row as FlowTaskDTO)">
                    {{ t('workflow.approval.messages.freeJumpTitle') }}
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="quickUrge(row as FlowTaskDTO)">{{ t('workflow.task.urge') }}</el-dropdown-item>
                  <el-dropdown-item @click="goInstance(row.instanceId)">{{ t('workflow.approval.actions.viewFlow') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button size="small" text @click="goInstance(row.instanceId)">
              {{ t('workflow.approval.actions.viewFlow') }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- P1-3: 分组模式（按流程类型聚合） -->
    <div v-if="groupByFlow" v-loading="todoLoading" class="group-view">
      <el-empty v-if="todoGroupedByFlow.length === 0" description="暂无待办" :image-size="60" />
      <div v-for="group in todoGroupedByFlow" :key="group.flowCode" class="group-card">
        <div class="group-card__header" @click="group._expanded = !group._expanded">
          <el-icon class="group-card__arrow">
            <ArrowDown v-if="group._expanded" />
            <ArrowRight v-else />
          </el-icon>
          <span class="group-card__name">{{ group.flowName }}</span>
          <el-tag size="small" type="info">{{ group.tasks.length }} 个待办</el-tag>
        </div>
        <el-table
          v-if="group._expanded"
          :data="group.tasks"
          stripe
          size="small"
          :row-class-name="tableRowClassName"
          @selection-change="(v: FlowTaskDTO[]) => onGroupSelectionChange(group.flowCode, v)"
        >
          <el-table-column type="selection" width="45" />
          <el-table-column prop="title" label="任务标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="nodeName" label="节点" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.nodeName || row.nodeCode }}</template>
          </el-table-column>
          <el-table-column prop="assigneeName" label="处理人" min-width="90">
            <template #default="{ row }">{{ row.assigneeName || row.assigneeId || '-' }}</template>
          </el-table-column>
          <el-table-column label="优先级" width="80">
            <template #default="{ row }">
              <el-tag :type="priorityTag(row.priority).type" size="small">
                {{ priorityTag(row.priority).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="140">
            <template #default="{ row }">
              {{ row.createTime ? formatTime(row.createTime) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="goInstance(row.instanceId)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-pagination
      v-model:current-page="todoQuery.pageNum"
      v-model:page-size="todoQuery.pageSize"
      :total="todoTotal"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @current-change="loadTodo"
      @size-change="loadTodo"
    />

    <!-- =========================================================== -->
    <!-- 任务操作弹窗（策略模式驱动，表单字段由当前 Action 配置决定） -->
    <!-- =========================================================== -->
    <el-dialog
      v-model="opDialog"
      :title="opDialogTitle"
      :width="isMobile ? '90%' : '520px'"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <!-- 审批意见 -->
        <el-form-item v-if="showCommentInput" :label="commentLabel">
          <CommentEditor
            v-model="opForm.comment"
            :phrases="commentPhrases"
            :rows="3"
            :maxlength="1000"
            :placeholder="commentPlaceholder"
            @mention="onOpMention"
          />
        </el-form-item>

        <!-- 目标用户（转办/委派/追加处理人/加签/减签） -->
        <el-form-item v-if="showTargetUser" :label="targetUserLabel">
          <UserPicker
            v-model="opForm.targetUser"
            :placeholder="t('workflow.approval.actions.userPickerPlaceholder')"
            :show-dialog="true"
            :dialog-title="targetUserDialogTitle"
            @change="onTargetUserChange"
          />
        </el-form-item>

        <!-- GAP-P0-2: 驳回到节点（支持多节点同退） -->
        <el-form-item v-if="showRejectNode" :label="t('workflow.approval.actions.rejectNode')">
          <div style="width: 100%">
            <el-checkbox-group v-model="opForm.targetNodeCodes">
              <el-checkbox
                v-for="n in opForm.rejectTargets"
                :key="n.nodeCode"
                :label="n.nodeName || n.nodeCode"
                :value="n.nodeCode"
                style="display: block; margin-bottom: 4px"
              />
            </el-checkbox-group>
            <div v-if="opForm.rejectTargets.length === 0" style="color: #909399; font-size: 12px">
              {{ t('workflow.approval.actions.rejectNodeEmpty') }}
            </div>
            <div style="color: #909399; font-size: 12px; margin-top: 4px">
              {{ t('workflow.approval.actions.rejectMultiHint') }}
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="opDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitOp">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
// ===========================================
// 增强筛选栏
// ===========================================
.filter-bar-enhanced {
  margin-bottom: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;

  &__row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;

    &:first-child {
      margin-bottom: 8px;
      padding-bottom: 8px;
      border-bottom: 1px dashed #e2e8f0;
    }
  }
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

// ===========================================
// 表格行高亮
// ===========================================
:deep(.row-overdue) {
  background-color: #fef2f2 !important;
  td {
    background-color: #fef2f2 !important;
  }
  &:hover td {
    background-color: #fee2e2 !important;
  }
}

:deep(.row-nearly-overdue) {
  background-color: #fffbeb !important;
  td {
    background-color: #fffbeb !important;
  }
  &:hover td {
    background-color: #fef3c7 !important;
  }
}

:deep(.row-pinned) {
  td:first-child {
    border-left: 3px solid #e6a23c;
  }
}

/* P1-3: 快捷标签 chips */
.filter-bar-enhanced__row--chips {
  align-items: center;
  gap: 6px;
  padding-top: 4px;
}

.chips-label {
  font-size: 12px;
  color: #909399;
}

.chip-tag {
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
}

.chip-tag:hover {
  opacity: 0.8;
  transform: translateY(-1px);
}

/* P1-3: 分组模式样式 */
.group-view {
  min-height: 200px;
}

.group-card {
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.group-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
}

.group-card__header:hover {
  background: #ecf5ff;
}

.group-card__arrow {
  font-size: 12px;
  color: #909399;
}

.group-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}

/* P2-6: 移动端 H5 适配 */
@media (max-width: 768px) {
  /* 筛选栏：每行控件堆叠 */
  .filter-bar-enhanced {
    padding: 8px;

    &__row {
      gap: 6px;

      &:first-child {
        margin-bottom: 6px;
        padding-bottom: 6px;
      }

      /* 紧急程度/流程类型/日期/重置 — 独占一行或两列 */
      .el-radio-group {
        width: 100%;
      }

      /* 固定宽度控件改为 100% */
      :deep(.el-select),
      :deep(.el-date-editor),
      :deep(.el-input) {
        width: 100% !important;
        flex: 1 1 100%;
      }

      .el-button {
        flex-shrink: 0;
      }
    }

    &__row--chips {
      flex-wrap: wrap;

      .chip-tag {
        font-size: 12px;
      }
    }
  }

  /* 表格：移动端单元格紧凑显示（inline 按钮已通过 v-if 收进 dropdown） */
  :deep(.el-table) {
    .el-table__cell {
      padding: 6px 4px;
    }

    /* 字体紧凑 */
    .cell {
      font-size: 13px;
    }
  }

  /* 分页：移动端简化 layout */
  .pagination {
    margin-top: 8px;
    justify-content: center;

    :deep(.el-pagination__total),
    :deep(.el-pagination__sizes),
    :deep(.el-pagination__jump) {
      display: none;
    }

    :deep(.el-pagination__pages) {
      flex-wrap: wrap;
      justify-content: center;
    }
  }

  /* 分组卡片紧凑显示 */
  .group-card {
    margin-bottom: 8px;

    &__header {
      padding: 8px;
    }

    &__name {
      font-size: 13px;
    }
  }
}
</style>
