/**
 * @file 审批操作策略模式
 * @module views/workflow/approval-center/composables/useApprovalActions
 * @description
 *   用策略模式替代原 index.vue 中 submitOp 的 12 个 if-else 分支：
 *     1. 每种审批操作（通过/驳回/转办/委派/签收/催办/暂存/追加处理人/加签/减签/已阅/沟通）
 *        封装为独立的 Action 类，实现统一的 ApprovalAction 接口；
 *     2. 通过 actionMap 注册表按类型获取策略实例，消除 if-else 链；
 *     3. useApprovalActions composable 封装操作弹窗状态与提交逻辑；
 *     4. 无弹窗快捷操作（一键通过/签收/暂存/已阅/沟通/催办/批量通过）复用同一套策略。
 *   同时导出审批中心共享的状态映射与格式化辅助函数。
 */
import { ref, reactive, computed, markRaw } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  passTask,
  rejectTask,
  rejectableNodes,
  transferTask,
  delegateTask,
  urgeTask,
  claimTask,
  saveDraft,
  addApprover,
  markReadTask,
  communicateTask,
  countersignBefore,
  countersignAfter,
  countersignRemove,
  batchPass,
} from '@/api/workflow'
import type { FlowTaskDTO, FlowTaskOperateDTO } from '@/api/workflow/types'
import type { ApiResponse } from '@/utils/request'

// ===========================================
// 一、策略接口定义
// ===========================================

/** 审批操作类型（大写常量，作为 actionMap 的 key） */
export type ApprovalActionType =
  | 'PASS'
  | 'REJECT'
  | 'TRANSFER'
  | 'DELEGATE'
  | 'CLAIM'
  | 'URGE'
  | 'SAVE_DRAFT'
  | 'ADD_APPROVER'
  | 'COUNTERSIGN_BEFORE'
  | 'COUNTERSIGN_AFTER'
  | 'COUNTERSIGN_REMOVE'
  | 'MARK_READ'
  | 'COMMUNICATE'

/** 操作执行上下文（弹窗表单数据 + 任务信息） */
export interface ApprovalActionContext {
  taskId: number
  instanceId?: number
  comment?: string
  targetUserId?: number
  targetUserName?: string
  targetNodeCode?: string
  variables?: Record<string, unknown>
}

/** 操作执行结果 */
export interface ActionResult {
  success: boolean
  message?: string
}

/** 审批操作策略接口 */
export interface ApprovalAction {
  /** 操作类型标识 */
  readonly type: ApprovalActionType
  /** 弹窗标题 */
  readonly title: string
  /** 是否需要审批意见输入 */
  readonly needComment: boolean
  /** 是否需要目标用户选择 */
  readonly needTargetUser: boolean
  /** 是否需要驳回目标节点 */
  readonly needRejectNode: boolean
  /** 审批意见输入框标签 */
  readonly commentLabel?: string
  /** 审批意见输入框占位符 */
  readonly commentPlaceholder?: string
  /** 目标用户选择项标签 */
  readonly targetUserLabel?: string
  /** 目标用户选择弹窗标题 */
  readonly targetUserDialogTitle?: string
  /** 执行操作 */
  execute(ctx: ApprovalActionContext): Promise<ActionResult>
}

// ===========================================
// 二、策略基类与具体实现
// ===========================================

/**
 * 策略基类：封装通用的 DTO 转换与结果处理。
 * 子类只需实现 call 方法指定调用哪个 API，并按需覆盖表单字段开关。
 */
abstract class BaseAction implements ApprovalAction {
  abstract readonly type: ApprovalActionType
  abstract readonly title: string
  readonly needComment = false
  readonly needTargetUser = false
  readonly needRejectNode = false
  readonly commentLabel?: string
  readonly commentPlaceholder?: string
  readonly targetUserLabel?: string
  readonly targetUserDialogTitle?: string

  /** 子类实现：调用对应的任务操作 API */
  protected abstract call(ctx: ApprovalActionContext): Promise<ApiResponse<unknown>>

  /** 通用：上下文 → FlowTaskOperateDTO */
  protected toDto(ctx: ApprovalActionContext): FlowTaskOperateDTO {
    return {
      taskId: ctx.taskId,
      comment: ctx.comment,
      targetUserId: ctx.targetUserId,
      targetUserName: ctx.targetUserName,
      targetNodeCode: ctx.targetNodeCode || undefined,
      variables: ctx.variables,
    }
  }

  async execute(ctx: ApprovalActionContext): Promise<ActionResult> {
    try {
      const res = await this.call(ctx)
      if (res?.data?.code === 0) {
        return { success: true }
      }
      return { success: false, message: res?.data?.message || '操作失败' }
    } catch (e) {
      return { success: false, message: '操作失败：' + (e as Error).message }
    }
  }
}

/** 通过 */
class PassAction extends BaseAction {
  readonly type = 'PASS' as const
  readonly title = '通过审批'
  readonly needComment = true
  readonly commentPlaceholder = '请输入审批意见（支持常用语 / @人 / 图片）'
  protected call(ctx: ApprovalActionContext) {
    return passTask(this.toDto(ctx))
  }
}

/** 驳回 */
class RejectAction extends BaseAction {
  readonly type = 'REJECT' as const
  readonly title = '驳回审批'
  readonly needComment = true
  readonly needRejectNode = true
  readonly commentPlaceholder = '请输入驳回意见'
  protected call(ctx: ApprovalActionContext) {
    return rejectTask(this.toDto(ctx))
  }
}

/** 转办 */
class TransferAction extends BaseAction {
  readonly type = 'TRANSFER' as const
  readonly title = '转办'
  readonly needComment = true
  readonly needTargetUser = true
  readonly targetUserLabel = '目标用户'
  readonly targetUserDialogTitle = '选择转办人'
  readonly commentPlaceholder = '请输入转办说明'
  protected call(ctx: ApprovalActionContext) {
    return transferTask(this.toDto(ctx))
  }
}

/** 委派 */
class DelegateAction extends BaseAction {
  readonly type = 'DELEGATE' as const
  readonly title = '委派'
  readonly needComment = true
  readonly needTargetUser = true
  readonly targetUserLabel = '目标用户'
  readonly targetUserDialogTitle = '选择委派人'
  readonly commentPlaceholder = '请输入委派说明'
  protected call(ctx: ApprovalActionContext) {
    return delegateTask(this.toDto(ctx))
  }
}

/** 签收 */
class ClaimAction extends BaseAction {
  readonly type = 'CLAIM' as const
  readonly title = '签收'
  protected call(ctx: ApprovalActionContext) {
    return claimTask({ taskId: ctx.taskId })
  }
}

/** 催办（依赖 instanceId） */
class UrgeAction extends BaseAction {
  readonly type = 'URGE' as const
  readonly title = '催办'
  readonly needComment = true
  readonly commentLabel = '催办意见'
  readonly commentPlaceholder = '请输入催办意见'
  protected call(ctx: ApprovalActionContext) {
    return urgeTask({ instanceId: ctx.instanceId as number, comment: ctx.comment })
  }
}

/** 暂存待审 */
class SaveDraftAction extends BaseAction {
  readonly type = 'SAVE_DRAFT' as const
  readonly title = '暂存待审'
  readonly needComment = true
  readonly commentLabel = '暂存备注'
  readonly commentPlaceholder = '请输入暂存备注'
  protected call(ctx: ApprovalActionContext) {
    return saveDraft(this.toDto(ctx))
  }
}

/** 追加处理人 */
class AddApproverAction extends BaseAction {
  readonly type = 'ADD_APPROVER' as const
  readonly title = '追加处理人'
  readonly needTargetUser = true
  readonly targetUserLabel = '追加处理人'
  readonly targetUserDialogTitle = '选择追加处理人'
  protected call(ctx: ApprovalActionContext) {
    return addApprover(this.toDto(ctx))
  }
}

/** 前加签 */
class CountersignBeforeAction extends BaseAction {
  readonly type = 'COUNTERSIGN_BEFORE' as const
  readonly title = '前加签'
  readonly needComment = true
  readonly needTargetUser = true
  readonly targetUserLabel = '前加签人'
  readonly targetUserDialogTitle = '选择前加签人'
  readonly commentPlaceholder = '请输入加签说明'
  protected call(ctx: ApprovalActionContext) {
    return countersignBefore(this.toDto(ctx))
  }
}

/** 后加签 */
class CountersignAfterAction extends BaseAction {
  readonly type = 'COUNTERSIGN_AFTER' as const
  readonly title = '后加签'
  readonly needComment = true
  readonly needTargetUser = true
  readonly targetUserLabel = '后加签人'
  readonly targetUserDialogTitle = '选择后加签人'
  readonly commentPlaceholder = '请输入加签说明'
  protected call(ctx: ApprovalActionContext) {
    return countersignAfter(this.toDto(ctx))
  }
}

/** 减签 */
class CountersignRemoveAction extends BaseAction {
  readonly type = 'COUNTERSIGN_REMOVE' as const
  readonly title = '减签'
  readonly needTargetUser = true
  readonly targetUserLabel = '减签人'
  readonly targetUserDialogTitle = '选择减签人'
  protected call(ctx: ApprovalActionContext) {
    return countersignRemove(this.toDto(ctx))
  }
}

/** 标记已阅 */
class MarkReadAction extends BaseAction {
  readonly type = 'MARK_READ' as const
  readonly title = '标记已阅'
  protected call(ctx: ApprovalActionContext) {
    return markReadTask({ taskId: ctx.taskId, userId: 0 })
  }
}

/** 沟通 */
class CommunicateAction extends BaseAction {
  readonly type = 'COMMUNICATE' as const
  readonly title = '沟通'
  readonly needComment = true
  readonly commentLabel = '沟通内容'
  readonly commentPlaceholder = '请输入沟通内容'
  protected call(ctx: ApprovalActionContext) {
    return communicateTask(this.toDto(ctx))
  }
}

/**
 * 操作策略注册表。
 * 新增操作只需在此注册一个 Action 实例，无需修改 submitOp 逻辑。
 */
export const actionMap: Record<ApprovalActionType, ApprovalAction> = {
  PASS: markRaw(new PassAction()),
  REJECT: markRaw(new RejectAction()),
  TRANSFER: markRaw(new TransferAction()),
  DELEGATE: markRaw(new DelegateAction()),
  CLAIM: markRaw(new ClaimAction()),
  URGE: markRaw(new UrgeAction()),
  SAVE_DRAFT: markRaw(new SaveDraftAction()),
  ADD_APPROVER: markRaw(new AddApproverAction()),
  COUNTERSIGN_BEFORE: markRaw(new CountersignBeforeAction()),
  COUNTERSIGN_AFTER: markRaw(new CountersignAfterAction()),
  COUNTERSIGN_REMOVE: markRaw(new CountersignRemoveAction()),
  MARK_READ: markRaw(new MarkReadAction()),
  COMMUNICATE: markRaw(new CommunicateAction()),
}

// ===========================================
// 三、操作弹窗状态与提交逻辑（composable）
// ===========================================

/** 审批常用语 */
export const commentPhrases = [
  '同意',
  '同意，请按计划推进',
  '同意，注意控制风险',
  '请补充资料后再议',
  '请修改后重新提交',
  '驳回，理由不充分',
  '已了解',
]

export interface UseApprovalActionsOptions {
  /** 操作成功后的回调（通常用于刷新列表） */
  onSuccess?: () => void
}

/**
 * 审批操作 composable。
 * 封装操作弹窗的显示/隐藏、表单状态、校验、提交，以及无弹窗的快捷操作。
 *
 * @example
 * ```ts
 * const {
 *   opDialog, opType, opForm, openOpDialog, submitOp,
 *   quickPass, quickBatchPass, commentPhrases,
 * } = useApprovalActions({ onSuccess: loadTodo })
 * ```
 */
export function useApprovalActions(options: UseApprovalActionsOptions = {}) {
  const { onSuccess } = options

  const opDialog = ref(false)
  const opType = ref<ApprovalActionType>('PASS')
  const opTask = ref<FlowTaskDTO | null>(null)

  const opForm = reactive({
    comment: '',
    /** 目标用户对象（UserPicker 选择） */
    targetUser: null as { id: number; realName?: string; username?: string } | null,
    targetUserId: undefined as number | undefined,
    targetUserName: '' as string,
    targetNodeCode: '',
    /** 驳回目标节点列表（任意历史节点） */
    rejectTargets: [] as Array<{ nodeCode: string; nodeName?: string }>,
    /** 附件列表（CommentEditor 附件） */
    attachments: [] as Array<{ uid: string; fileId?: string | number; name: string; url: string }>,
    /** 提及列表（CommentEditor @人） */
    mentions: [] as Array<{ userId: number; name: string }>,
  })

  /** 当前策略实例 */
  const currentAction = computed(() => actionMap[opType.value])

  const opDialogTitle = computed(() => currentAction.value?.title || '操作')
  const showCommentInput = computed(() => currentAction.value?.needComment ?? false)
  const showTargetUser = computed(() => currentAction.value?.needTargetUser ?? false)
  const showRejectNode = computed(() => currentAction.value?.needRejectNode ?? false)
  const commentLabel = computed(() => currentAction.value?.commentLabel || '审批意见')
  const commentPlaceholder = computed(
    () => currentAction.value?.commentPlaceholder || '请输入审批意见',
  )
  const targetUserLabel = computed(
    () => currentAction.value?.targetUserLabel || '目标用户',
  )
  const targetUserDialogTitle = computed(
    () => currentAction.value?.targetUserDialogTitle || '选择目标用户',
  )

  /** 重置表单 */
  function resetOpForm() {
    opForm.comment = ''
    opForm.targetUser = null
    opForm.targetUserId = undefined
    opForm.targetUserName = ''
    opForm.targetNodeCode = ''
    opForm.rejectTargets = []
    opForm.attachments = []
    opForm.mentions = []
  }

  /** 打开操作弹窗 */
  async function openOpDialog(type: ApprovalActionType, task: FlowTaskDTO) {
    opType.value = type
    opTask.value = task
    resetOpForm()
    opDialog.value = true
    // 驳回时异步加载可驳回节点列表
    if (type === 'REJECT') {
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

  /** UserPicker 变更同步 id/name */
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

  /** @人 提及（占位，留待后端实现通知） */
  function onOpMention(m: { userId: number; name: string }) {
    // eslint-disable-next-line no-console
    console.debug('[ApprovalCenter] @ 提及用户:', m)
  }

  /**
   * 提交操作（策略模式：根据 opType 获取策略并执行）。
   * 原来此处为 12 个 if-else 分支，现统一委托给 actionMap[type].execute。
   */
  async function submitOp() {
    if (!opTask.value) return
    const action = currentAction.value
    if (!action) return

    // 校验：驳回必须填写意见
    if (action.needComment && opType.value === 'REJECT' && !opForm.comment.trim()) {
      ElMessage.warning('请填写驳回意见')
      return
    }
    // 校验：需要目标用户时必填
    if (action.needTargetUser && !opForm.targetUserId) {
      ElMessage.warning('请选择目标用户')
      return
    }

    const ctx: ApprovalActionContext = {
      taskId: opTask.value.id,
      instanceId: opTask.value.instanceId,
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

    const result = await action.execute(ctx)
    if (result.success) {
      ElMessage.success('操作成功')
      opDialog.value = false
      onSuccess?.()
    } else {
      ElMessage.error(result.message || '操作失败')
    }
  }

  // ===========================================
  // 无弹窗快捷操作（复用 actionMap 策略）
  // ===========================================

  /** 一键通过（不弹窗，直接通过） */
  async function quickPass(task: FlowTaskDTO) {
    try {
      await ElMessageBox.confirm(
        `确认直接通过审批事项「${task.title || '未命名'}」？`,
        '一键通过',
        { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'info' },
      )
      const result = await actionMap.PASS.execute({ taskId: task.id, comment: '同意' })
      if (result.success) {
        ElMessage.success('已通过')
        onSuccess?.()
      } else {
        ElMessage.error(result.message || '操作失败')
      }
    } catch {
      // 用户取消
    }
  }

  /** 快捷签收 */
  async function quickClaim(task: FlowTaskDTO) {
    const result = await actionMap.CLAIM.execute({ taskId: task.id })
    if (result.success) {
      ElMessage.success('签收成功')
      onSuccess?.()
    } else {
      ElMessage.error(result.message || '签收失败')
    }
  }

  /** 快捷暂存 */
  async function quickSaveDraft(task: FlowTaskDTO) {
    const result = await actionMap.SAVE_DRAFT.execute({ taskId: task.id, comment: '' })
    if (result.success) {
      ElMessage.success('已暂存')
    } else {
      ElMessage.error(result.message || '暂存失败')
    }
  }

  /** 快捷已阅 */
  async function quickMarkRead(task: FlowTaskDTO) {
    const result = await actionMap.MARK_READ.execute({ taskId: task.id })
    if (result.success) {
      ElMessage.success('已标记为已阅')
    } else {
      ElMessage.error(result.message || '操作失败')
    }
  }

  /** 快捷沟通（prompt 输入） */
  async function quickCommunicate(task: FlowTaskDTO) {
    try {
      const { value } = await ElMessageBox.prompt('请输入沟通内容', '沟通', {
        confirmButtonText: '发送',
        cancelButtonText: '取消',
        inputType: 'textarea',
      })
      const result = await actionMap.COMMUNICATE.execute({
        taskId: task.id,
        comment: value,
      })
      if (result.success) {
        ElMessage.success('沟通消息已发送')
      } else {
        ElMessage.error(result.message || '沟通失败')
      }
    } catch {
      // 用户取消
    }
  }

  /** 快捷催办（prompt 输入） */
  async function quickUrge(task: FlowTaskDTO) {
    try {
      const { value } = await ElMessageBox.prompt('请输入催办意见', '催办', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      const result = await actionMap.URGE.execute({
        instanceId: task.instanceId,
        comment: value,
      })
      if (result.success) {
        ElMessage.success('催办成功')
      } else {
        ElMessage.error(result.message || '催办失败')
      }
    } catch {
      // 用户取消
    }
  }

  /** 批量通过 */
  async function quickBatchPass(taskIds: number[]) {
    if (taskIds.length === 0) {
      ElMessage.warning('请先勾选待审批项')
      return
    }
    try {
      const { value } = await ElMessageBox.prompt('请输入批量审批意见', '批量审批', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      const res = await batchPass({ taskIds, comment: value || undefined })
      if (res.data?.code === 0) {
        ElMessage.success(`批量审批成功，共处理 ${res.data.data || taskIds.length} 项`)
        onSuccess?.()
      } else {
        ElMessage.error(res.data?.message || '批量审批失败')
      }
    } catch {
      // 用户取消
    }
  }

  return {
    // 弹窗状态
    opDialog,
    opType,
    opTask,
    opForm,
    currentAction,
    // 弹窗派生状态
    opDialogTitle,
    showCommentInput,
    showTargetUser,
    showRejectNode,
    commentLabel,
    commentPlaceholder,
    targetUserLabel,
    targetUserDialogTitle,
    // 弹窗方法
    openOpDialog,
    submitOp,
    onTargetUserChange,
    onOpMention,
    // 快捷操作
    quickPass,
    quickClaim,
    quickSaveDraft,
    quickMarkRead,
    quickCommunicate,
    quickUrge,
    quickBatchPass,
    // 常量
    commentPhrases,
  }
}

// ===========================================
// 四、审批中心共享辅助函数（状态映射 / 格式化）
// ===========================================

/** 流程实例状态映射 */
const instanceStatusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '审批中', type: 'warning' },
  SUSPENDED: { label: '已挂起', type: 'info' },
  COMPLETED: { label: '已完成', type: 'success' },
  TERMINATED: { label: '已终止', type: 'danger' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

/** 任务状态映射 */
const taskStatusMap: Record<string, { label: string; type: string }> = {
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

/** 流程实例状态标签 */
export function instanceStatusLabel(s: string): string {
  return instanceStatusMap[s]?.label || s
}

/** 流程实例状态类型 */
export function instanceStatusType(s: string): string {
  return instanceStatusMap[s]?.type || 'info'
}

/** 任务状态标签与类型 */
export function taskStatusLabel(s: string): { label: string; type: string } {
  return taskStatusMap[s] || { label: s, type: 'info' }
}

/** 格式化时间（YYYY-MM-DD HH:mm） */
export function formatTime(s?: string): string {
  if (!s) return '-'
  return dayjs(s).format('YYYY-MM-DD HH:mm')
}

/** 耗时格式化 */
export function durationLabel(ms?: number): string {
  if (!ms || ms <= 0) return '-'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}分`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}时${m % 60}分`
  return `${Math.floor(h / 24)}天`
}

/** 是否已超时 */
export function isOverdue(task: FlowTaskDTO): boolean {
  return (
    !!task.dueAt &&
    new Date(task.dueAt).getTime() < Date.now() &&
    (task.taskStatus === 'PENDING' || task.taskStatus === 'CLAIMED')
  )
}

/** 是否即将超时（2 小时内） */
export function isNearlyOverdue(task: FlowTaskDTO): boolean {
  if (!task.dueAt) return false
  const now = Date.now()
  const due = new Date(task.dueAt).getTime()
  return (
    due > now &&
    due <= now + 2 * 60 * 60 * 1000 &&
    (task.taskStatus === 'PENDING' || task.taskStatus === 'CLAIMED')
  )
}
