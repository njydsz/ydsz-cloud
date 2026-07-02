<script setup lang="ts">
/**
 * @file 统一审批中心
 * @module views/approval-center
 * @description 4 个 Tab：我的待办 / 我的已办 / 我发起的 / 抄送我的
 * P0-6: 统一审批中心（对标钉钉/飞书的"审批中心"，用户日常工作主入口）。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  pageTodoTasks,
  pageDoneTasks,
  pageMyInstances,
  pageCc,
  passTask,
  rejectTask,
  transferTask,
  delegateTask,
  urgeTask,
  claimTask,
  ccMarkRead,
  ccMarkAllRead,
} from '@/api/workflow'
import type {
  FlowTaskDTO,
  FlowTaskQuery,
  FlowInstanceDTO,
  FlowCcDTO,
  FlowCcQuery,
  FlowTaskOperateDTO,
} from '@/api/workflow/types'

const router = useRouter()
const activeTab = ref<'todo' | 'done' | 'mine' | 'cc'>('todo')

// ===========================================
// 我的待办
// ===========================================
const todoQuery = reactive<FlowTaskQuery>({
  pageNum: 1,
  pageSize: 20,
  flowCode: undefined,
})
const todoList = ref<FlowTaskDTO[]>([])
const todoTotal = ref(0)
const todoLoading = ref(false)

async function loadTodo() {
  todoLoading.value = true
  try {
    const res = await pageTodoTasks(todoQuery)
    if (res.data?.code === 0) {
      todoList.value = res.data.data?.records || []
      todoTotal.value = res.data.data?.total || 0
    }
  } finally {
    todoLoading.value = false
  }
}

// ===========================================
// 我的已办
// ===========================================
const doneQuery = reactive<FlowTaskQuery>({
  pageNum: 1,
  pageSize: 20,
})
const doneList = ref<FlowTaskDTO[]>([])
const doneTotal = ref(0)
const doneLoading = ref(false)

async function loadDone() {
  doneLoading.value = true
  try {
    const res = await pageDoneTasks(doneQuery)
    if (res.data?.code === 0) {
      doneList.value = res.data.data?.records || []
      doneTotal.value = res.data.data?.total || 0
    }
  } finally {
    doneLoading.value = false
  }
}

// ===========================================
// 我发起的
// ===========================================
const myQuery = reactive({
  pageNum: 1,
  pageSize: 20,
  flowCode: undefined,
  status: undefined as string | undefined,
})
const myList = ref<FlowInstanceDTO[]>([])
const myTotal = ref(0)
const myLoading = ref(false)

async function loadMy() {
  myLoading.value = true
  try {
    const res = await pageMyInstances(myQuery)
    if (res.data?.code === 0) {
      myList.value = res.data.data?.records || []
      myTotal.value = res.data.data?.total || 0
    }
  } finally {
    myLoading.value = false
  }
}

// ===========================================
// 抄送我的
// ===========================================
const ccQuery = reactive<FlowCcQuery>({
  readStatus: undefined,
  pageNum: 1,
  pageSize: 20,
})
const ccList = ref<FlowCcDTO[]>([])
const ccTotal = ref(0)
const ccLoading = ref(false)
const ccUnread = ref(0)

async function loadCc() {
  ccLoading.value = true
  try {
    const res = await pageCc(ccQuery)
    if (res.data?.code === 0) {
      ccList.value = res.data.data?.records || []
      ccTotal.value = res.data.data?.total || 0
    }
  } finally {
    ccLoading.value = false
  }
}

async function loadCcUnread() {
  const res = await ccUnreadCount()
  if (res.data?.code === 0) {
    ccUnread.value = res.data.data || 0
  }
}

// ===========================================
// 任务操作弹窗
// ===========================================
const opDialog = ref(false)
const opType = ref<'pass' | 'reject' | 'transfer' | 'delegate' | 'urge'>('pass')
const opTask = ref<FlowTaskDTO | null>(null)
const opForm = reactive({
  comment: '',
  targetUserId: undefined as number | undefined,
  targetUserName: '',
  targetNodeCode: '',
  /** 驳回目标节点列表（任意历史节点） */
  rejectTargets: [] as string[],
})

function openOpDialog(type: typeof opType.value, task: FlowTaskDTO) {
  opType.value = type
  opTask.value = task
  opForm.comment = ''
  opForm.targetUserId = undefined
  opForm.targetUserName = ''
  opForm.targetNodeCode = ''
  opForm.rejectTargets = []
  opDialog.value = true
}

async function submitOp() {
  if (!opTask.value) return
  if (opType.value === 'reject' && !opForm.comment.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  if ((opType.value === 'transfer' || opType.value === 'delegate') && !opForm.targetUserId) {
    ElMessage.warning('请选择目标用户')
    return
  }
  const dto: FlowTaskOperateDTO = {
    taskId: opTask.value.id,
    comment: opForm.comment,
    targetUserId: opForm.targetUserId,
    targetUserName: opForm.targetUserName,
    targetNodeCode: opForm.targetNodeCode || undefined,
  }
  try {
    let res
    if (opType.value === 'pass') res = await passTask(dto)
    else if (opType.value === 'reject') res = await rejectTask(dto)
    else if (opType.value === 'transfer') res = await transferTask(dto)
    else if (opType.value === 'delegate') res = await delegateTask(dto)
    if (res?.data?.code === 0) {
      ElMessage.success('操作成功')
      opDialog.value = false
      loadTodo()
    } else {
      ElMessage.error(res?.data?.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败：' + (e as Error).message)
  }
}

async function quickClaim(task: FlowTaskDTO) {
  try {
    const res = await claimTask({ taskId: task.id })
    if (res.data?.code === 0) {
      ElMessage.success('签收成功')
      loadTodo()
    } else {
      ElMessage.error(res.data?.message || '签收失败')
    }
  } catch (e) {
    ElMessage.error('签收失败：' + (e as Error).message)
  }
}

async function quickUrge(task: FlowTaskDTO) {
  try {
    await ElMessageBox.prompt('请输入催办意见', '催办', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    }).then(async ({ value }) => {
      const res = await urgeTask({ instanceId: task.instanceId, comment: value })
      if (res.data?.code === 0) {
        ElMessage.success('催办成功')
      } else {
        ElMessage.error(res.data?.message || '催办失败')
      }
    }).catch(() => {})
  } catch (e) {
    // user cancel
  }
}

async function quickCcRead(row: FlowCcDTO) {
  if (row.readStatus === 'READ') return
  const res = await ccMarkRead(row.id)
  if (res.data?.code === 0) {
    row.readStatus = 'READ'
    row.readAt = new Date().toISOString()
    ccUnread.value = Math.max(0, ccUnread.value - 1)
  }
}

async function markAllCcRead() {
  const res = await ccMarkAllRead()
  if (res.data?.code === 0) {
    ElMessage.success(`已全部标记为已读（${res.data.data} 条）`)
    loadCc()
    loadCcUnread()
  }
}

// ===========================================
// 跳转
// ===========================================
function goInstance(instanceId: number) {
  router.push({ path: '/workflow/instance', query: { id: String(instanceId) } })
}

// ===========================================
// 通用
// ===========================================
const tabBadge = computed(() => ({
  todo: todoTotal.value > 0 ? todoTotal.value : undefined,
  cc: ccUnread.value > 0 ? ccUnread.value : undefined,
}))

const statusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '审批中', type: 'warning' },
  SUSPENDED: { label: '已挂起', type: 'info' },
  COMPLETED: { label: '已完成', type: 'success' },
  TERMINATED: { label: '已终止', type: 'danger' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

function statusLabel(s: string) {
  return statusMap[s]?.label || s
}
function statusType(s: string) {
  return statusMap[s]?.type || 'info'
}

function taskStatusLabel(s: string) {
  const m: Record<string, { label: string; type: string }> = {
    PENDING: { label: '待办', type: 'warning' },
    CLAIMED: { label: '已签收', type: 'primary' },
    COMPLETED: { label: '已完成', type: 'success' },
    REJECTED: { label: '已驳回', type: 'danger' },
    SKIPPED: { label: '已跳过', type: 'info' },
    CANCELLED: { label: '已取消', type: 'info' },
    TIMEOUT: { label: '已超时', type: 'danger' },
    DELEGATED: { label: '已委派', type: 'primary' },
    FROZEN: { label: '已冻结', type: 'info' },
  }
  return m[s] || { label: s, type: 'info' }
}

function formatTime(s?: string) {
  if (!s) return '-'
  return dayjs(s).format('YYYY-MM-DD HH:mm')
}

function durationLabel(ms?: number) {
  if (!ms || ms <= 0) return '-'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}分`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}时${m % 60}分`
  return `${Math.floor(h / 24)}天`
}

function isOverdue(task: FlowTaskDTO) {
  return (
    task.dueAt &&
    new Date(task.dueAt).getTime() < Date.now() &&
    (task.taskStatus === 'PENDING' || task.taskStatus === 'CLAIMED')
  )
}

onMounted(() => {
  loadTodo()
  loadDone()
  loadMy()
  loadCc()
  loadCcUnread()
})

function onTabChange(tab: string) {
  if (tab === 'todo') loadTodo()
  else if (tab === 'done') loadDone()
  else if (tab === 'mine') loadMy()
  else if (tab === 'cc') {
    loadCc()
    loadCcUnread()
  }
}
</script>

<template>
  <div class="approval-center">
    <div class="page-header">
      <h2>审批中心</h2>
      <p class="page-header__sub">统一处理待办、已办、发起、抄送（对标钉钉/飞书审批）</p>
    </div>
    <el-tabs v-model="activeTab" class="approval-tabs" @tab-change="onTabChange">
      <!-- 我的待办 -->
      <el-tab-pane name="todo">
        <template #label>
          <span class="tab-label">
            <el-icon><Bell /></el-icon>
            我的待办
            <el-badge v-if="tabBadge.todo" :value="tabBadge.todo" :max="99" class="tab-badge" />
          </span>
        </template>
        <div class="filter-bar">
          <el-input
            v-model="todoQuery.flowCode"
            placeholder="流程编码"
            clearable
            style="width: 200px"
            @keyup.enter="loadTodo"
          />
          <el-button type="primary" @click="loadTodo">查询</el-button>
          <el-button @click="() => { todoQuery.flowCode = undefined; todoQuery.pageNum = 1; loadTodo() }">重置</el-button>
        </div>
        <el-table :data="todoList" v-loading="todoLoading" stripe>
          <el-table-column prop="title" label="审批事项" min-width="220" show-overflow-tooltip />
          <el-table-column prop="flowName" label="流程" width="160" show-overflow-tooltip />
          <el-table-column prop="nodeName" label="当前节点" width="120" />
          <el-table-column prop="assignorName" label="委托人" width="100" />
          <el-table-column prop="createTime" label="到达时间" width="160">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag
                v-if="isOverdue(row)"
                type="danger"
                size="small"
                effect="dark"
              >已超期</el-tag>
              <el-tag
                v-else
                :type="taskStatusLabel(row.taskStatus).type as any"
                size="small"
              >
                {{ taskStatusLabel(row.taskStatus).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.taskStatus === 'PENDING'"
                type="success"
                size="small"
                @click="openOpDialog('pass', row)"
              >通过</el-button>
              <el-button
                v-if="row.taskStatus === 'PENDING'"
                type="danger"
                size="small"
                @click="openOpDialog('reject', row)"
              >驳回</el-button>
              <el-dropdown size="small" v-if="row.taskStatus === 'PENDING' || row.taskStatus === 'CLAIMED'">
                <el-button size="small">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="row.taskStatus === 'PENDING'" @click="quickClaim(row)">签收</el-dropdown-item>
                    <el-dropdown-item @click="openOpDialog('transfer', row)">转办</el-dropdown-item>
                    <el-dropdown-item @click="openOpDialog('delegate', row)">委派</el-dropdown-item>
                    <el-dropdown-item @click="quickUrge(row)">催办</el-dropdown-item>
                    <el-dropdown-item @click="goInstance(row.instanceId)">查看流程</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
      </el-tab-pane>

      <!-- 我的已办 -->
      <el-tab-pane name="done">
        <template #label>
          <span class="tab-label">
            <el-icon><Select /></el-icon>
            我的已办
          </span>
        </template>
        <div class="filter-bar">
          <el-input
            v-model="doneQuery.flowCode"
            placeholder="流程编码"
            clearable
            style="width: 200px"
            @keyup.enter="loadDone"
          />
          <el-button type="primary" @click="loadDone">查询</el-button>
        </div>
        <el-table :data="doneList" v-loading="doneLoading" stripe>
          <el-table-column prop="title" label="审批事项" min-width="220" show-overflow-tooltip />
          <el-table-column prop="flowName" label="流程" width="160" />
          <el-table-column prop="nodeName" label="节点" width="120" />
          <el-table-column prop="comment" label="审批意见" min-width="180" show-overflow-tooltip />
          <el-table-column label="耗时" width="100">
            <template #default="{ row }">{{ durationLabel(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column label="完成时间" width="160">
            <template #default="{ row }">{{ formatTime(row.finishAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="goInstance(row.instanceId)">查看流程</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="doneQuery.pageNum"
          v-model:page-size="doneQuery.pageSize"
          :total="doneTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @current-change="loadDone"
          @size-change="loadDone"
        />
      </el-tab-pane>

      <!-- 我发起的 -->
      <el-tab-pane name="mine">
        <template #label>
          <span class="tab-label">
            <el-icon><Promotion /></el-icon>
            我发起的
          </span>
        </template>
        <div class="filter-bar">
          <el-input
            v-model="myQuery.flowCode"
            placeholder="流程编码"
            clearable
            style="width: 200px"
          />
          <el-select
            v-model="myQuery.status"
            placeholder="状态"
            clearable
            style="width: 140px"
          >
            <el-option label="审批中" value="RUNNING" />
            <el-option label="已挂起" value="SUSPENDED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
          <el-button type="primary" @click="loadMy">查询</el-button>
        </div>
        <el-table :data="myList" v-loading="myLoading" stripe>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="flowName" label="流程" width="160" />
          <el-table-column prop="businessNo" label="业务单号" width="160" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status) as any" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentNodeName" label="当前节点" width="120" />
          <el-table-column label="发起时间" width="160">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="goInstance(row.id)">查看流程</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="myQuery.pageNum"
          v-model:page-size="myQuery.pageSize"
          :total="myTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @current-change="loadMy"
          @size-change="loadMy"
        />
      </el-tab-pane>

      <!-- 抄送我的 -->
      <el-tab-pane name="cc">
        <template #label>
          <span class="tab-label">
            <el-icon><Share /></el-icon>
            抄送我的
            <el-badge v-if="tabBadge.cc" :value="tabBadge.cc" :max="99" class="tab-badge" type="danger" />
          </span>
        </template>
        <div class="filter-bar">
          <el-select
            v-model="ccQuery.readStatus"
            placeholder="已读状态"
            clearable
            style="width: 140px"
            @change="loadCc"
          >
            <el-option label="未读" value="UNREAD" />
            <el-option label="已读" value="READ" />
          </el-select>
          <el-button type="primary" @click="loadCc">查询</el-button>
          <el-button type="warning" @click="markAllCcRead">全部标为已读</el-button>
        </div>
        <el-table :data="ccList" v-loading="ccLoading" stripe>
          <el-table-column prop="title" label="抄送标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="flowName" label="流程" width="160" />
          <el-table-column prop="nodeName" label="触发节点" width="120" />
          <el-table-column prop="triggerUserName" label="发起人" width="100" />
          <el-table-column prop="content" label="意见/内容" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="row.readStatus === 'READ' ? 'info' : 'danger'"
                size="small"
              >{{ row.readStatus === 'READ' ? '已读' : '未读' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="抄送时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.readStatus === 'UNREAD'"
                size="small"
                text
                type="primary"
                @click="quickCcRead(row)"
              >标为已读</el-button>
              <el-button size="small" text @click="goInstance(row.instanceId)">查看流程</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="ccQuery.pageNum"
          v-model:page-size="ccQuery.pageSize"
          :total="ccTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @current-change="loadCc"
          @size-change="loadCc"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 任务操作弹窗 -->
    <el-dialog
      v-model="opDialog"
      :title="
        opType === 'pass' ? '通过审批' :
        opType === 'reject' ? '驳回审批' :
        opType === 'transfer' ? '转办' :
        opType === 'delegate' ? '委派' :
        '操作'
      "
      width="500px"
    >
      <el-form label-position="top">
        <el-form-item label="审批意见" v-if="opType === 'pass' || opType === 'reject' || opType === 'transfer' || opType === 'delegate'">
          <el-input
            v-model="opForm.comment"
            type="textarea"
            :rows="3"
            placeholder="请输入审批意见"
          />
        </el-form-item>
        <el-form-item label="目标用户 ID" v-if="opType === 'transfer' || opType === 'delegate'">
          <el-input v-model.number="opForm.targetUserId" placeholder="请输入用户 ID" />
        </el-form-item>
        <el-form-item label="目标用户姓名" v-if="opType === 'transfer' || opType === 'delegate'">
          <el-input v-model="opForm.targetUserName" placeholder="可选，便于显示" />
        </el-form-item>
        <el-form-item label="驳回到节点" v-if="opType === 'reject'">
          <el-input
            v-model="opForm.targetNodeCode"
            placeholder="可选：留空则驳回到上一节点；填写则驳回到指定节点"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="opDialog = false">取消</el-button>
        <el-button type="primary" @click="submitOp">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.approval-center {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 20px;
    color: #1e293b;
  }

  &__sub {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}

.approval-tabs {
  background: #fff;
  border-radius: 6px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);

  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.tab-badge {
  margin-left: 4px;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
