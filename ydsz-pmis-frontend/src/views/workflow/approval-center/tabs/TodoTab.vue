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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { pageTodoTasks, pageDefinitions } from '@/api/workflow'
import type {
  FlowTaskDTO,
  FlowTaskQuery,
  FlowDefinitionDTO,
} from '@/api/workflow/types'
import { UserPicker, CommentEditor } from '@/components/common'
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

// ===========================================
// 筛选状态
// ===========================================
const urgencyFilter = ref<'all' | 'nearly-overdue' | 'overdue'>('all')
const flowTypeFilter = ref<string | undefined>(undefined)
const flowDefinitions = ref<FlowDefinitionDTO[]>([])
const dateRange = ref<[string, string] | null>(null)

async function loadFlowDefinitions() {
  try {
    const res = await pageDefinitions({ status: 'PUBLISHED', pageNum: 1, pageSize: 200 })
    if (res.data?.code === 0) {
      flowDefinitions.value = res.data.data?.records || []
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
  { key: 'createTime', label: t('workflow.approval.columns.createTime') },
  { key: 'status', label: t('workflow.approval.columns.status') },
  { key: 'operation', label: t('workflow.approval.columns.operation'), fixed: true },
])
const visibleColumns = ref<string[]>(['selection', 'pin', 'title', 'flowName', 'nodeName', 'assignorName', 'createTime', 'status', 'operation'])

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

/** 表格行样式 */
function tableRowClassName({ row }: { row: FlowTaskDTO }): string {
  if (isOverdue(row)) return 'row-overdue'
  if (isNearlyOverdue(row)) return 'row-nearly-overdue'
  if (pinnedTaskIds.value.has(row.id)) return 'row-pinned'
  return ''
}

/** 对当前列表排序（置顶优先，然后按到达时间倒序） */
function sortTodoList() {
  todoList.value = [...todoList.value].sort((a, b) => {
    const aPinned = pinnedTaskIds.value.has(a.id) ? 1 : 0
    const bPinned = pinnedTaskIds.value.has(b.id) ? 1 : 0
    if (aPinned !== bPinned) return bPinned - aPinned
    return new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime()
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
      let records = res.data.data?.records || []
      const total = res.data.data?.total || 0

      // 紧急程度筛选（客户端过滤）
      if (urgencyFilter.value === 'nearly-overdue') {
        records = records.filter((t) => isNearlyOverdue(t))
      } else if (urgencyFilter.value === 'overdue') {
        records = records.filter((t) => isOverdue(t))
      }

      // 排序：置顶优先
      records.sort((a, b) => {
        const aPinned = pinnedTaskIds.value.has(a.id) ? 1 : 0
        const bPinned = pinnedTaskIds.value.has(b.id) ? 1 : 0
        if (aPinned !== bPinned) return bPinned - aPinned
        return new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime()
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
} = useApprovalActions({
  onSuccess: () => {
    loadTodo()
    emit('refresh-badge')
  },
})

// ===========================================
// 跳转
// ===========================================
function goInstance(instanceId: number) {
  router.push({ path: '/workflow/instance', query: { id: String(instanceId) } })
}

// ===========================================
// 生命周期
// ===========================================
onMounted(() => {
  loadPinnedTasks()
  loadColumnPrefs()
  loadFlowDefinitions()
  loadTodo()
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
        <el-button
          type="success"
          size="small"
          :disabled="todoSelection.length === 0"
          @click="quickBatchPass(todoSelection.map((t) => t.id))"
        >
          {{ t('workflow.approval.buttons.batchPass', { n: todoSelection.length }) }}
        </el-button>

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
    </div>

    <!-- 待办表格 -->
    <el-table
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
          <el-tag v-if="isOverdue(row)" type="danger" size="small" effect="dark">
            {{ t('workflow.approval.urgency.overdueTag') }}
          </el-tag>
          <el-tag
            v-else-if="isNearlyOverdue(row)"
            type="warning"
            size="small"
            effect="dark"
          >
            {{ t('workflow.approval.urgency.nearlyOverdueTag') }}
          </el-tag>
          <el-tag
            v-else
            :type="(taskStatusLabel(row.taskStatus).type as any)"
            size="small"
          >
            {{ taskStatusLabel(row.taskStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="isColumnVisible('operation')"
        :label="t('workflow.approval.columns.operation')"
        width="420"
        fixed="right"
      >
        <template #default="{ row }">
          <template v-if="row.taskStatus === 'PENDING' || row.taskStatus === 'CLAIMED'">
            <!-- 一键通过 -->
            <el-button
              v-if="row.taskStatus === 'PENDING'"
              type="success"
              size="small"
              @click="quickPass(row)"
            >
              {{ t('workflow.approval.actions.quickPass') }}
            </el-button>
            <!-- 通过（弹窗） -->
            <el-button
              v-if="row.taskStatus === 'PENDING'"
              type="primary"
              size="small"
              @click="openOpDialog('PASS', row)"
            >
              {{ t('workflow.approval.pass') }}
            </el-button>
            <!-- 驳回 -->
            <el-button
              v-if="row.taskStatus === 'PENDING'"
              type="danger"
              size="small"
              @click="openOpDialog('REJECT', row)"
            >
              {{ t('workflow.approval.reject') }}
            </el-button>
            <!-- 更多操作 -->
            <el-dropdown size="small">
              <el-button size="small">
                {{ t('workflow.approval.actions.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.taskStatus === 'PENDING'" @click="quickClaim(row)">
                    {{ t('workflow.task.claim') }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="quickSaveDraft(row)">{{ t('workflow.approval.actions.saveDraft') }}</el-dropdown-item>
                  <el-dropdown-item @click="quickMarkRead(row)">{{ t('workflow.approval.actions.markRead') }}</el-dropdown-item>
                  <el-dropdown-item @click="quickCommunicate(row)">{{ t('workflow.approval.actions.communicate') }}</el-dropdown-item>
                  <el-dropdown-item divided @click="openOpDialog('TRANSFER', row)">
                    {{ t('workflow.task.transfer') }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="openOpDialog('DELEGATE', row)">{{ t('workflow.task.delegate') }}</el-dropdown-item>
                  <el-dropdown-item @click="openOpDialog('ADD_APPROVER', row)">
                    {{ t('workflow.approval.actions.addApprover') }}
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="openOpDialog('COUNTERSIGN_BEFORE', row)">
                    {{ t('workflow.approval.actions.countersignBefore') }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="openOpDialog('COUNTERSIGN_AFTER', row)">
                    {{ t('workflow.approval.actions.countersignAfter') }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="openOpDialog('COUNTERSIGN_REMOVE', row)">
                    {{ t('workflow.approval.actions.countersignRemove') }}
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="quickUrge(row)">{{ t('workflow.task.urge') }}</el-dropdown-item>
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
      width="520px"
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

        <!-- 驳回到节点 -->
        <el-form-item v-if="showRejectNode" :label="t('workflow.approval.actions.rejectNode')">
          <el-select
            v-model="opForm.targetNodeCode"
            :placeholder="t('workflow.approval.actions.rejectNodePlaceholder')"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="n in opForm.rejectTargets"
              :key="n.nodeCode"
              :label="n.nodeName || n.nodeCode"
              :value="n.nodeCode"
            />
          </el-select>
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
</style>
