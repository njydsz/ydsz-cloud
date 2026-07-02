<script setup lang="ts">
/**
 * @file 统一审批中心（增强版）
 * @module views/approval-center
 * @description 4 个 Tab：我的待办 / 我的已办 / 我发起的 / 抄送我的
 *   增强功能：
 *     1. 快捷筛选栏：紧急程度 / 流程类型 / 发起时间范围
 *     2. 待办置顶/标记：localStorage 持久化
 *     3. 实时待办角标：60 秒轮询
 *     4. 列表快捷操作：一键通过、超时高亮、自定义列显隐
 *     5. 快捷操作菜单增强：暂存/追加处理人/已阅/沟通
 */
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { UserPicker, CommentEditor } from '@/components/common'
import {
  pageTodoTasks,
  pageDoneTasks,
  pageMyInstances,
  pageCc,
  passTask,
  rejectTask,
  rejectableNodes,
  transferTask,
  delegateTask,
  urgeTask,
  claimTask,
  batchPass,
  saveDraft,
  addApprover,
  markReadTask,
  communicateTask,
  countersignBefore,
  countersignAfter,
  countersignRemove,
  ccMarkRead,
  ccMarkAllRead,
  ccUnreadCount,
  pageDefinitions,
} from '@/api/workflow'
import type {
  FlowTaskDTO,
  FlowTaskQuery,
  FlowInstanceDTO,
  FlowCcDTO,
  FlowCcQuery,
  FlowTaskOperateDTO,
  FlowDefinitionDTO,
} from '@/api/workflow/types'

const router = useRouter()
const activeTab = ref<'todo' | 'done' | 'mine' | 'cc'>('todo')

// ===========================================
// 筛选增强状态
// ===========================================
/** 紧急程度筛选 */
const urgencyFilter = ref<'all' | 'nearly-overdue' | 'overdue'>('all')
/** 流程类型筛选 */
const flowTypeFilter = ref<string | undefined>(undefined)
const flowDefinitions = ref<FlowDefinitionDTO[]>([])
/** 发起时间范围筛选 */
const dateRange = ref<[string, string] | null>(null)

/** 加载流程定义列表（用于流程类型筛选下拉） */
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
// 待办置顶/标记（localStorage 实现）
// ===========================================
const PINNED_STORAGE_KEY = 'approval_center_pinned_tasks'
const pinnedTaskIds = ref<Set<number>>(new Set())

/** 从 localStorage 加载置顶列表 */
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

/** 持久化置顶列表到 localStorage */
function savePinnedTasks() {
  try {
    localStorage.setItem(PINNED_STORAGE_KEY, JSON.stringify([...pinnedTaskIds.value]))
  } catch {
    // 静默失败
  }
}

/** 切换置顶状态 */
function togglePin(taskId: number) {
  if (pinnedTaskIds.value.has(taskId)) {
    pinnedTaskIds.value.delete(taskId)
  } else {
    pinnedTaskIds.value.add(taskId)
  }
  savePinnedTasks()
  // 重新排序列表
  sortTodoList()
}

/** 是否已置顶 */
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
const columnOptions: ColumnOption[] = [
  { key: 'selection', label: '选择', fixed: true },
  { key: 'pin', label: '置顶', fixed: true },
  { key: 'title', label: '审批事项' },
  { key: 'flowName', label: '流程' },
  { key: 'nodeName', label: '当前节点' },
  { key: 'assignorName', label: '委托人' },
  { key: 'createTime', label: '到达时间' },
  { key: 'status', label: '状态' },
  { key: 'operation', label: '操作', fixed: true },
]
const visibleColumns = ref<string[]>(columnOptions.map((c) => c.key))

/** 加载列显隐偏好 */
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

/** 保存列显隐偏好 */
function saveColumnPrefs() {
  try {
    localStorage.setItem('approval_center_columns', JSON.stringify(visibleColumns.value))
  } catch {
    // 静默失败
  }
}

/** 切换列显隐 */
function toggleColumn(key: string) {
  const idx = visibleColumns.value.indexOf(key)
  if (idx >= 0) {
    visibleColumns.value.splice(idx, 1)
  } else {
    visibleColumns.value.push(key)
  }
  saveColumnPrefs()
}

/** 是否显示某列 */
function isColumnVisible(key: string): boolean {
  return visibleColumns.value.includes(key)
}

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

/** 紧急程度判断 */
function isOverdue(task: FlowTaskDTO) {
  return (
    task.dueAt &&
    new Date(task.dueAt).getTime() < Date.now() &&
    (task.taskStatus === 'PENDING' || task.taskStatus === 'CLAIMED')
  )
}

/** 是否即将超时（2 小时内） */
function isNearlyOverdue(task: FlowTaskDTO): boolean {
  if (!task.dueAt) return false
  const now = Date.now()
  const due = new Date(task.dueAt).getTime()
  return due > now && due <= now + 2 * 60 * 60 * 1000
    && (task.taskStatus === 'PENDING' || task.taskStatus === 'CLAIMED')
}

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
    // 构建查询参数
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
// 实时角标：60 秒轮询
// ===========================================
let pollingTimer: ReturnType<typeof setInterval> | null = null

/** 刷新待办总数（轻量请求，仅获取总数） */
async function refreshTodoBadge() {
  try {
    const res = await pageTodoTasks({ pageNum: 1, pageSize: 1 })
    if (res.data?.code === 0) {
      todoTotal.value = res.data.data?.total || 0
    }
  } catch {
    // 静默失败
  }
}

function startPolling() {
  refreshTodoBadge()
  loadCcUnread()
  pollingTimer = setInterval(() => {
    refreshTodoBadge()
    loadCcUnread()
  }, 60000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// ===========================================
// 任务操作弹窗（增强：支持更多操作类型）
// ===========================================
const opDialog = ref(false)
const opType = ref<'pass' | 'reject' | 'transfer' | 'delegate' | 'urge' | 'saveDraft' | 'addApprover' | 'countersignBefore' | 'countersignAfter' | 'countersignRemove' | 'markRead' | 'communicate'>('pass')
const opTask = ref<FlowTaskDTO | null>(null)
const opForm = reactive({
  comment: '',
  /** 目标用户对象（P1-8: UserPicker 选择） */
  targetUser: null as { id: number; realName?: string; username?: string } | null,
  targetUserId: undefined as number | undefined,
  targetUserName: '',
  targetNodeCode: '',
  /** 驳回目标节点列表（任意历史节点） */
  rejectTargets: [] as Array<{ nodeCode: string; nodeName?: string }>,
  /** 附件列表（P1-9: CommentEditor 附件） */
  attachments: [] as Array<{ uid: string; fileId?: string | number; name: string; url: string }>,
  /** 提及列表（P1-9: CommentEditor @人） */
  mentions: [] as Array<{ userId: number; name: string }>,
})

/** P0: 批量审批多选 */
const todoSelection = ref<FlowTaskDTO[]>([])

/** P1-9: 审批常用语 */
const commentPhrases = [
  '同意',
  '同意，请按计划推进',
  '同意，注意控制风险',
  '请补充资料后再议',
  '请修改后重新提交',
  '驳回，理由不充分',
  '已了解',
]

/** 操作弹窗标题映射 */
const opDialogTitle = computed(() => {
  const map: Record<string, string> = {
    pass: '通过审批',
    reject: '驳回审批',
    transfer: '转办',
    delegate: '委派',
    urge: '催办',
    saveDraft: '暂存待审',
    addApprover: '追加处理人',
    countersignBefore: '前加签',
    countersignAfter: '后加签',
    countersignRemove: '减签',
    markRead: '标记已阅',
    communicate: '沟通',
  }
  return map[opType.value] || '操作'
})

/** 是否显示审批意见输入 */
const showCommentInput = computed(() => {
  return ['pass', 'reject', 'transfer', 'delegate', 'urge', 'saveDraft', 'communicate', 'countersignBefore', 'countersignAfter'].includes(opType.value)
})

/** 是否显示目标用户选择 */
const showTargetUser = computed(() => {
  return ['transfer', 'delegate', 'addApprover', 'countersignBefore', 'countersignAfter', 'countersignRemove'].includes(opType.value)
})

/** 是否显示驳回目标节点 */
const showRejectNode = computed(() => opType.value === 'reject')

/** P1-9: 有人被 @ 时，给被 @ 用户发通知（占位，留待后端实现） */
function onOpMention(m: { userId: number; name: string }) {
  // eslint-disable-next-line no-console
  console.debug('[ApprovalCenter] @ 提及用户:', m)
}

/**
 * P1-8: UserPicker 变更同步 id/name
 */
function onTargetUserChange(v: unknown) {
  if (v && typeof v === 'object') {
    opForm.targetUserId = (v as { id: number }).id
    opForm.targetUserName =
      (v as { realName?: string; username?: string }).realName ||
      (v as { username?: string }).username ||
      ''
  } else {
    opForm.targetUserId = undefined
    opForm.targetUserName = ''
  }
}

async function openOpDialog(type: typeof opType.value, task: FlowTaskDTO) {
  opType.value = type
  opTask.value = task
  opForm.comment = ''
  opForm.targetUser = null
  opForm.targetUserId = undefined
  opForm.targetUserName = ''
  opForm.targetNodeCode = ''
  opForm.rejectTargets = []
  opForm.attachments = []
  opForm.mentions = []
  opDialog.value = true
  // P1-1: 驳回时异步加载可驳回节点列表
  if (type === 'reject') {
    try {
      const res = await rejectableNodes(task.id)
      if (res.data?.code === 0) {
        opForm.rejectTargets = (res.data.data || []).map((n) => ({
          nodeCode: n.nodeCode,
          nodeName: n.nodeName,
        }))
      }
    } catch {
      // 静默失败，使用空列表（用户仍可手填）
    }
  }
}

async function submitOp() {
  if (!opTask.value) return
  if (opType.value === 'reject' && !opForm.comment.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  if (showTargetUser.value && !opForm.targetUserId) {
    ElMessage.warning('请选择目标用户')
    return
  }
  const dto: FlowTaskOperateDTO = {
    taskId: opTask.value.id,
    comment: opForm.comment,
    targetUserId: opForm.targetUserId,
    targetUserName: opForm.targetUserName,
    targetNodeCode: opForm.targetNodeCode || undefined,
    variables: {
      attachments: opForm.attachments.map((a) => ({
        fileId: a.fileId,
        name: a.name,
        url: a.url,
      })),
      mentions: opForm.mentions,
    },
  }
  try {
    let res
    if (opType.value === 'pass') res = await passTask(dto)
    else if (opType.value === 'reject') res = await rejectTask(dto)
    else if (opType.value === 'transfer') res = await transferTask(dto)
    else if (opType.value === 'delegate') res = await delegateTask(dto)
    else if (opType.value === 'saveDraft') res = await saveDraft(dto)
    else if (opType.value === 'addApprover') res = await addApprover(dto)
    else if (opType.value === 'countersignBefore') res = await countersignBefore(dto)
    else if (opType.value === 'countersignAfter') res = await countersignAfter(dto)
    else if (opType.value === 'countersignRemove') res = await countersignRemove(dto)
    else if (opType.value === 'markRead') res = await markReadTask({ taskId: opTask.value.id, userId: 0 })
    else if (opType.value === 'communicate') res = await communicateTask(dto)
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

// ===========================================
// 快捷操作（无需弹窗）
// ===========================================
/** 一键通过（不弹窗，直接通过） */
async function quickPass(task: FlowTaskDTO) {
  try {
    await ElMessageBox.confirm(
      `确认直接通过审批事项「${task.title || '未命名'}」？`,
      '一键通过',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'info' },
    )
    const res = await passTask({ taskId: task.id, comment: '同意' })
    if (res.data?.code === 0) {
      ElMessage.success('已通过')
      loadTodo()
    } else {
      ElMessage.error(res.data?.message || '操作失败')
    }
  } catch {
    // 用户取消
  }
}

/** 快捷签收 */
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

/** 快捷暂存 */
async function quickSaveDraft(task: FlowTaskDTO) {
  try {
    const res = await saveDraft({ taskId: task.id, comment: '' })
    if (res.data?.code === 0) {
      ElMessage.success('已暂存')
    } else {
      ElMessage.error(res.data?.message || '暂存失败')
    }
  } catch (e) {
    ElMessage.error('暂存失败：' + (e as Error).message)
  }
}

/** 快捷已阅 */
async function quickMarkRead(task: FlowTaskDTO) {
  try {
    const res = await markReadTask({ taskId: task.id, userId: 0 })
    if (res.data?.code === 0) {
      ElMessage.success('已标记为已阅')
    } else {
      ElMessage.error(res.data?.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败：' + (e as Error).message)
  }
}

/** 快捷沟通 */
async function quickCommunicate(task: FlowTaskDTO) {
  try {
    await ElMessageBox.prompt('请输入沟通内容', '沟通', {
      confirmButtonText: '发送',
      cancelButtonText: '取消',
      inputType: 'textarea',
    }).then(async ({ value }) => {
      const res = await communicateTask({ taskId: task.id, comment: value })
      if (res.data?.code === 0) {
        ElMessage.success('沟通消息已发送')
      } else {
        ElMessage.error(res.data?.message || '沟通失败')
      }
    }).catch(() => {})
  } catch {
    // 用户取消
  }
}

/** 快捷催办 */
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
  } catch {
    // 用户取消
  }
}

/** P0: 批量审批 */
async function quickBatchPass() {
  if (todoSelection.value.length === 0) {
    ElMessage.warning('请先勾选待审批项')
    return
  }
  try {
    await ElMessageBox.prompt('请输入批量审批意见', '批量审批', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    }).then(async ({ value }) => {
      const res = await batchPass({
        taskIds: todoSelection.value.map((t) => t.id),
        comment: value || undefined,
      })
      if (res.data?.code === 0) {
        ElMessage.success(`批量审批成功，共处理 ${res.data.data || todoSelection.value.length} 项`)
        todoSelection.value = []
        loadTodo()
      } else {
        ElMessage.error(res.data?.message || '批量审批失败')
      }
    }).catch(() => {})
  } catch {
    // 用户取消
  }
}

// ===========================================
// 抄送快捷操作
// ===========================================
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

// ===========================================
// 生命周期
// ===========================================
onMounted(() => {
  loadPinnedTasks()
  loadColumnPrefs()
  loadFlowDefinitions()
  loadTodo()
  loadDone()
  loadMy()
  loadCc()
  loadCcUnread()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
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
      <h2>
        审批中心
        <el-badge
          v-if="tabBadge.todo"
          :value="tabBadge.todo"
          :max="999"
          class="header-badge"
        />
      </h2>
      <p class="page-header__sub">统一处理待办、已办、发起、抄送（对标钉钉/飞书审批）</p>
    </div>
    <el-tabs v-model="activeTab" class="approval-tabs" @tab-change="onTabChange">
      <!-- =========================================================== -->
      <!-- 我的待办（增强版） -->
      <!-- =========================================================== -->
      <el-tab-pane name="todo">
        <template #label>
          <span class="tab-label">
            <el-icon><Bell /></el-icon>
            我的待办
            <el-badge v-if="tabBadge.todo" :value="tabBadge.todo" :max="99" class="tab-badge" />
          </span>
        </template>

        <!-- 快捷筛选栏 -->
        <div class="filter-bar-enhanced">
          <div class="filter-bar-enhanced__row">
            <!-- 紧急程度 -->
            <el-radio-group v-model="urgencyFilter" size="small" @change="() => { todoQuery.pageNum = 1; loadTodo() }">
              <el-radio-button value="all">全部</el-radio-button>
              <el-radio-button value="nearly-overdue">
                即将超时
                <el-tooltip content="截止时间在 2 小时内" placement="top">
                  <el-icon style="margin-left:2px"><QuestionFilled /></el-icon>
                </el-tooltip>
              </el-radio-button>
              <el-radio-button value="overdue">已超时</el-radio-button>
            </el-radio-group>

            <!-- 流程类型 -->
            <el-select
              v-model="flowTypeFilter"
              placeholder="流程类型"
              clearable
              filterable
              size="small"
              style="width: 200px"
              @change="() => { todoQuery.pageNum = 1; loadTodo() }"
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
              range-separator="至"
              start-placeholder="发起开始"
              end-placeholder="发起结束"
              size="small"
              style="width: 260px"
              value-format="YYYY-MM-DD HH:mm:ss"
              :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
              @change="() => { todoQuery.pageNum = 1; loadTodo() }"
            />

            <el-button size="small" @click="resetTodoFilters">重置筛选</el-button>
          </div>

          <div class="filter-bar-enhanced__row">
            <el-input
              v-model="todoQuery.flowCode"
              placeholder="流程编码关键词"
              clearable
              size="small"
              style="width: 200px"
              @keyup.enter="loadTodo"
            />
            <el-button type="primary" size="small" @click="loadTodo">查询</el-button>
            <el-button
              type="success"
              size="small"
              :disabled="todoSelection.length === 0"
              @click="quickBatchPass"
            >
              批量通过（{{ todoSelection.length }}）
            </el-button>

            <!-- 列显隐控制 -->
            <el-popover placement="bottom-end" :width="200" trigger="click">
              <template #reference>
                <el-button size="small" style="margin-left: auto">
                  <el-icon><Setting /></el-icon>
                  列设置
                </el-button>
              </template>
              <el-checkbox-group v-model="visibleColumns" @change="saveColumnPrefs">
                <div v-for="col in columnOptions" :key="col.key" style="margin-bottom: 6px">
                  <el-checkbox :value="col.key" :disabled="col.fixed" :label="col.key">
                    {{ col.label }}
                    <el-tag v-if="col.fixed" size="small" type="info" style="margin-left: 4px">固定</el-tag>
                  </el-checkbox>
                </div>
              </el-checkbox-group>
            </el-popover>
          </div>
        </div>

        <!-- 待办表格 -->
        <el-table
          :data="todoList"
          v-loading="todoLoading"
          stripe
          :row-class-name="tableRowClassName"
          @selection-change="(v: FlowTaskDTO[]) => todoSelection = v"
        >
          <el-table-column
            v-if="isColumnVisible('selection')"
            type="selection"
            width="50"
          />
          <el-table-column
            v-if="isColumnVisible('pin')"
            label="置顶"
            width="60"
            align="center"
          >
            <template #default="{ row }">
              <el-button
                :type="isPinned(row.id) ? 'warning' : 'default'"
                :icon="isPinned(row.id) ? 'StarFilled' : 'Star'"
                size="small"
                text
                @click="togglePin(row.id)"
                :title="isPinned(row.id) ? '取消置顶' : '置顶'"
              />
            </template>
          </el-table-column>
          <el-table-column
            v-if="isColumnVisible('title')"
            prop="title"
            label="审批事项"
            min-width="200"
            show-overflow-tooltip
          />
          <el-table-column
            v-if="isColumnVisible('flowName')"
            prop="flowName"
            label="流程"
            width="160"
            show-overflow-tooltip
          />
          <el-table-column
            v-if="isColumnVisible('nodeName')"
            prop="nodeName"
            label="当前节点"
            width="120"
          />
          <el-table-column
            v-if="isColumnVisible('assignorName')"
            prop="assignorName"
            label="委托人"
            width="100"
          />
          <el-table-column
            v-if="isColumnVisible('createTime')"
            label="到达时间"
            width="160"
          >
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column
            v-if="isColumnVisible('status')"
            label="状态"
            width="120"
          >
            <template #default="{ row }">
              <el-tag
                v-if="isOverdue(row)"
                type="danger"
                size="small"
                effect="dark"
              >已超期</el-tag>
              <el-tag
                v-else-if="isNearlyOverdue(row)"
                type="warning"
                size="small"
                effect="dark"
              >即将超期</el-tag>
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
            label="操作"
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
                  一键通过
                </el-button>
                <!-- 通过（弹窗） -->
                <el-button
                  v-if="row.taskStatus === 'PENDING'"
                  type="primary"
                  size="small"
                  @click="openOpDialog('pass', row)"
                >通过</el-button>
                <!-- 驳回 -->
                <el-button
                  v-if="row.taskStatus === 'PENDING'"
                  type="danger"
                  size="small"
                  @click="openOpDialog('reject', row)"
                >驳回</el-button>
                <!-- 更多操作 -->
                <el-dropdown size="small">
                  <el-button size="small">
                    更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="row.taskStatus === 'PENDING'" @click="quickClaim(row)">签收</el-dropdown-item>
                      <el-dropdown-item @click="quickSaveDraft(row)">暂存</el-dropdown-item>
                      <el-dropdown-item @click="quickMarkRead(row)">已阅</el-dropdown-item>
                      <el-dropdown-item @click="quickCommunicate(row)">沟通</el-dropdown-item>
                      <el-dropdown-item divided @click="openOpDialog('transfer', row)">转办</el-dropdown-item>
                      <el-dropdown-item @click="openOpDialog('delegate', row)">委派</el-dropdown-item>
                      <el-dropdown-item @click="openOpDialog('addApprover', row)">追加处理人</el-dropdown-item>
                      <el-dropdown-item divided @click="openOpDialog('countersignBefore', row)">前加签</el-dropdown-item>
                      <el-dropdown-item @click="openOpDialog('countersignAfter', row)">后加签</el-dropdown-item>
                      <el-dropdown-item @click="openOpDialog('countersignRemove', row)">减签</el-dropdown-item>
                      <el-dropdown-item divided @click="quickUrge(row)">催办</el-dropdown-item>
                      <el-dropdown-item @click="goInstance(row.instanceId)">查看流程</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
              <template v-else>
                <el-button size="small" text @click="goInstance(row.instanceId)">查看流程</el-button>
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
      </el-tab-pane>

      <!-- =========================================================== -->
      <!-- 我的已办 -->
      <!-- =========================================================== -->
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

      <!-- =========================================================== -->
      <!-- 我发起的 -->
      <!-- =========================================================== -->
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
              <el-tag :type="(statusType(row.status) as any)" size="small">
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

      <!-- =========================================================== -->
      <!-- 抄送我的 -->
      <!-- =========================================================== -->
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

    <!-- =========================================================== -->
    <!-- 任务操作弹窗（增强：支持所有操作类型） -->
    <!-- =========================================================== -->
    <el-dialog
      v-model="opDialog"
      :title="opDialogTitle"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <!-- 审批意见 -->
        <el-form-item
          v-if="showCommentInput"
          :label="opType === 'communicate' ? '沟通内容' : opType === 'urge' ? '催办意见' : opType === 'saveDraft' ? '暂存备注' : '审批意见'"
        >
          <CommentEditor
            v-model="opForm.comment"
            :phrases="commentPhrases"
            :rows="3"
            :maxlength="1000"
            :placeholder="opType === 'communicate' ? '请输入沟通内容' : opType === 'urge' ? '请输入催办意见' : '请输入审批意见（支持常用语 / @人 / 图片）'"
            @mention="onOpMention"
          />
        </el-form-item>

        <!-- 目标用户（转办/委派/追加处理人/加签/减签） -->
        <el-form-item
          v-if="showTargetUser"
          :label="opType === 'addApprover' ? '追加处理人' : opType === 'countersignBefore' ? '前加签人' : opType === 'countersignAfter' ? '后加签人' : opType === 'countersignRemove' ? '减签人' : '目标用户'"
        >
          <UserPicker
            v-model="opForm.targetUser"
            :placeholder="'请选择目标用户（搜索姓名/用户名）'"
            :show-dialog="true"
            :dialog-title="opType === 'transfer' ? '选择转办人' : opType === 'delegate' ? '选择委派人' : opType === 'addApprover' ? '选择追加处理人' : '选择目标用户'"
            @change="onTargetUserChange"
          />
        </el-form-item>

        <!-- 驳回到节点 -->
        <el-form-item v-if="showRejectNode" label="驳回到节点">
          <el-select
            v-model="opForm.targetNodeCode"
            placeholder="可选：留空则驳回到上一节点；选择则驳回到指定历史节点"
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
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  &__sub {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}

.header-badge {
  :deep(.el-badge__content) {
    font-size: 12px;
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

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
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