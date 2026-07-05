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
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AxiosResponse } from 'axios'
import dayjs from 'dayjs'
import i18n from '@/locales'
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
import type { UserModel } from '@/components/common'

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
  /** 单节点退回目标（向后兼容） */
  targetNodeCode?: string
  /** GAP-P0-2: 多节点同退目标列表（非空时优先于 targetNodeCode） */
  targetNodeCodes?: string[]
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
  readonly needComment: boolean = false
  readonly needTargetUser: boolean = false
  readonly needRejectNode: boolean = false
  readonly commentLabel?: string
  readonly commentPlaceholder?: string
  readonly targetUserLabel?: string
  readonly targetUserDialogTitle?: string

  /** 子类实现：调用对应的任务操作 API */
  protected abstract call(ctx: ApprovalActionContext): Promise<AxiosResponse<ApiResponse<unknown>>>

  /** 通用：上下文 → FlowTaskOperateDTO */
  protected toDto(ctx: ApprovalActionContext): FlowTaskOperateDTO {
    return {
      taskId: ctx.taskId,
      comment: ctx.comment,
      targetUserId: ctx.targetUserId,
      targetUserName: ctx.targetUserName,
      targetNodeCode: ctx.targetNodeCode || undefined,
      // GAP-P0-2: 多节点同退列表（非空时后端优先使用）
      targetNodeCodes: ctx.targetNodeCodes && ctx.targetNodeCodes.length > 0
        ? ctx.targetNodeCodes
        : undefined,
      variables: ctx.variables,
    }
  }

  async execute(ctx: ApprovalActionContext): Promise<ActionResult> {
    try {
      const res = await this.call(ctx)
      if (res?.data?.code === 0) {
        return { success: true }
      }
      return { success: false, message: res?.data?.message || i18n.global.t('workflow.approval.messages.operationFailed') }
    } catch (e) {
      return { success: false, message: i18n.global.t('workflow.approval.messages.operationFailed') + '：' + (e as Error).message }
    }
  }
}

/** 通过 */
class PassAction extends BaseAction {
  readonly type = 'PASS' as const
  readonly title = 'workflow.approval.actions.passTitle'
  override readonly needComment = true
  override readonly commentPlaceholder = 'workflow.approval.actions.passPlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return passTask(this.toDto(ctx))
  }
}

/** 驳回 */
class RejectAction extends BaseAction {
  readonly type = 'REJECT' as const
  readonly title = 'workflow.approval.actions.rejectTitle'
  override readonly needComment = true
  override readonly needRejectNode = true
  override readonly commentPlaceholder = 'workflow.approval.actions.rejectPlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return rejectTask(this.toDto(ctx))
  }
}

/** 转办 */
class TransferAction extends BaseAction {
  readonly type = 'TRANSFER' as const
  readonly title = 'workflow.approval.actions.transferTitle'
  override readonly needComment = true
  override readonly needTargetUser = true
  override readonly targetUserLabel = 'workflow.approval.actions.targetUserLabel'
  override readonly targetUserDialogTitle = 'workflow.approval.actions.transferDialogTitle'
  override readonly commentPlaceholder = 'workflow.approval.actions.transferPlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return transferTask(this.toDto(ctx))
  }
}

/** 委派 */
class DelegateAction extends BaseAction {
  readonly type = 'DELEGATE' as const
  readonly title = 'workflow.approval.actions.delegateTitle'
  override readonly needComment = true
  override readonly needTargetUser = true
  override readonly targetUserLabel = 'workflow.approval.actions.targetUserLabel'
  override readonly targetUserDialogTitle = 'workflow.approval.actions.delegateDialogTitle'
  override readonly commentPlaceholder = 'workflow.approval.actions.delegatePlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return delegateTask(this.toDto(ctx))
  }
}

/** 签收 */
class ClaimAction extends BaseAction {
  readonly type = 'CLAIM' as const
  readonly title = 'workflow.approval.actions.claimTitle'
  protected call(ctx: ApprovalActionContext) {
    return claimTask(ctx.taskId)
  }
}

/** 催办（依赖 instanceId） */
class UrgeAction extends BaseAction {
  readonly type = 'URGE' as const
  readonly title = 'workflow.approval.actions.urgeTitle'
  override readonly needComment = true
  override readonly commentLabel = 'workflow.approval.actions.urgeCommentLabel'
  override readonly commentPlaceholder = 'workflow.approval.actions.urgePlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return urgeTask(ctx.instanceId as number, ctx.comment)
  }
}

/** 暂存待审 */
class SaveDraftAction extends BaseAction {
  readonly type = 'SAVE_DRAFT' as const
  readonly title = 'workflow.approval.actions.saveDraftTitle'
  override readonly needComment = true
  override readonly commentLabel = 'workflow.approval.actions.draftCommentLabel'
  override readonly commentPlaceholder = 'workflow.approval.actions.draftPlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return saveDraft(this.toDto(ctx))
  }
}

/** 追加处理人 */
class AddApproverAction extends BaseAction {
  readonly type = 'ADD_APPROVER' as const
  readonly title = 'workflow.approval.actions.addApproverTitle'
  override readonly needTargetUser = true
  override readonly targetUserLabel = 'workflow.approval.actions.addApproverLabel'
  override readonly targetUserDialogTitle = 'workflow.approval.actions.addApproverDialogTitle'
  protected call(ctx: ApprovalActionContext) {
    return addApprover(this.toDto(ctx))
  }
}

/** 前加签 */
class CountersignBeforeAction extends BaseAction {
  readonly type = 'COUNTERSIGN_BEFORE' as const
  readonly title = 'workflow.approval.actions.countersignBeforeTitle'
  override readonly needComment = true
  override readonly needTargetUser = true
  override readonly targetUserLabel = 'workflow.approval.actions.countersignBeforeLabel'
  override readonly targetUserDialogTitle = 'workflow.approval.actions.countersignBeforeDialogTitle'
  override readonly commentPlaceholder = 'workflow.approval.actions.countersignPlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return countersignBefore(this.toDto(ctx))
  }
}

/** 后加签 */
class CountersignAfterAction extends BaseAction {
  readonly type = 'COUNTERSIGN_AFTER' as const
  readonly title = 'workflow.approval.actions.countersignAfterTitle'
  override readonly needComment = true
  override readonly needTargetUser = true
  override readonly targetUserLabel = 'workflow.approval.actions.countersignAfterLabel'
  override readonly targetUserDialogTitle = 'workflow.approval.actions.countersignAfterDialogTitle'
  override readonly commentPlaceholder = 'workflow.approval.actions.countersignPlaceholder'
  protected call(ctx: ApprovalActionContext) {
    return countersignAfter(this.toDto(ctx))
  }
}

/** 减签 */
class CountersignRemoveAction extends BaseAction {
  readonly type = 'COUNTERSIGN_REMOVE' as const
  readonly title = 'workflow.approval.actions.countersignRemoveTitle'
  override readonly needTargetUser = true
  override readonly targetUserLabel = 'workflow.approval.actions.countersignRemoveLabel'
  override readonly targetUserDialogTitle = 'workflow.approval.actions.countersignRemoveDialogTitle'
  protected call(ctx: ApprovalActionContext) {
    return countersignRemove(this.toDto(ctx))
  }
}

/** 标记已阅 */
class MarkReadAction extends BaseAction {
  readonly type = 'MARK_READ' as const
  readonly title = 'workflow.approval.actions.markReadTitle'
  protected call(ctx: ApprovalActionContext) {
    return markReadTask({ taskId: ctx.taskId, userId: 0 })
  }
}

/** 沟通 */
class CommunicateAction extends BaseAction {
  readonly type = 'COMMUNICATE' as const
  readonly title = 'workflow.approval.actions.communicateTitle'
  override readonly needComment = true
  override readonly commentLabel = 'workflow.approval.actions.communicateCommentLabel'
  override readonly commentPlaceholder = 'workflow.approval.actions.communicatePlaceholder'
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

/** 常用语 i18n keys */
const commentPhraseKeys = [
  'workflow.approval.phrases.agree',
  'workflow.approval.phrases.agreeProceed',
  'workflow.approval.phrases.agreeRisk',
  'workflow.approval.phrases.supplementLater',
  'workflow.approval.phrases.modifyResubmit',
  'workflow.approval.phrases.rejectInsufficient',
  'workflow.approval.phrases.acknowledged',
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
  const { t } = useI18n()

  const opDialog = ref(false)
  const opType = ref<ApprovalActionType>('PASS')
  const opTask = ref<FlowTaskDTO | null>(null)

  const opForm = reactive({
    comment: '',
    /** 目标用户对象（UserPicker 选择） */
    targetUser: null as UserModel,
    targetUserId: undefined as number | undefined,
    targetUserName: '' as string,
    targetNodeCode: '',
    /** GAP-P0-2: 多节点同退勾选的节点编码列表 */
    targetNodeCodes: [] as string[],
    /** 驳回目标节点列表（任意历史节点） */
    rejectTargets: [] as Array<{ nodeCode: string; nodeName?: string }>,
    /** 附件列表（CommentEditor 附件） */
    attachments: [] as Array<{ uid: string; fileId?: string | number; name: string; url: string }>,
    /** 提及列表（CommentEditor @人） */
    mentions: [] as Array<{ userId: number; name: string }>,
  })

  /** 当前策略实例 */
  const currentAction = computed(() => actionMap[opType.value])

  const opDialogTitle = computed(() => t(currentAction.value?.title || 'workflow.approval.actions.operation'))
  const showCommentInput = computed(() => currentAction.value?.needComment ?? false)
  const showTargetUser = computed(() => currentAction.value?.needTargetUser ?? false)
  const showRejectNode = computed(() => currentAction.value?.needRejectNode ?? false)
  const commentLabel = computed(() => t(currentAction.value?.commentLabel || 'workflow.approval.actions.commentLabel'))
  const commentPlaceholder = computed(
    () => t(currentAction.value?.commentPlaceholder || 'workflow.approval.actions.commentPlaceholder'),
  )
  const targetUserLabel = computed(
    () => t(currentAction.value?.targetUserLabel || 'workflow.approval.actions.targetUserLabel'),
  )
  const targetUserDialogTitle = computed(
    () => t(currentAction.value?.targetUserDialogTitle || 'workflow.approval.actions.targetUserDialogTitle'),
  )

  /** 重置表单 */
  function resetOpForm() {
    opForm.comment = ''
    opForm.targetUser = null
    opForm.targetUserId = undefined
    opForm.targetUserName = ''
    opForm.targetNodeCode = ''
    // GAP-P0-2: 重置多节点同退勾选
    opForm.targetNodeCodes = []
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

  /**
   * @人 提及（占位，留待后端实现通知）
   *
   * <p>当前仅记录被提及用户，待后端通知服务支持 @ 提及语义后补全推送逻辑。
   * 保留空实现以避免 UI 入口报错。
   */
  function onOpMention(_m: { userId: number; name: string }) {
    // P2 待实现：后端通知服务支持 @ 提及后，调用 notificationApi.mentionUser(_m.userId, _m.name)
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
      ElMessage.warning(t('workflow.approval.messages.rejectCommentRequired'))
      return
    }
    // 校验：需要目标用户时必填
    if (action.needTargetUser && !opForm.targetUserId) {
      ElMessage.warning(t('workflow.approval.messages.targetUserRequired'))
      return
    }

    const ctx: ApprovalActionContext = {
      taskId: opTask.value.id,
      instanceId: opTask.value.instanceId,
      comment: opForm.comment,
      targetUserId: opForm.targetUserId,
      targetUserName: opForm.targetUserName,
      targetNodeCode: opForm.targetNodeCode || undefined,
      // GAP-P0-2: 多节点同退勾选列表（非空时后端优先使用）
      targetNodeCodes: opForm.targetNodeCodes.length > 0 ? opForm.targetNodeCodes : undefined,
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
      ElMessage.success(t('workflow.approval.messages.operationSuccess'))
      opDialog.value = false
      onSuccess?.()
    } else {
      ElMessage.error(result.message || t('workflow.approval.messages.operationFailed'))
    }
  }

  // ===========================================
  // 无弹窗快捷操作（复用 actionMap 策略）
  // ===========================================

  /** 一键通过（不弹窗，直接通过） */
  async function quickPass(task: FlowTaskDTO) {
    try {
      await ElMessageBox.confirm(
        t('workflow.approval.messages.quickPassConfirm', { name: task.title || t('workflow.approval.messages.quickPassUnnamed') }),
        t('workflow.approval.messages.quickPassTitle'),
        { confirmButtonText: t('workflow.approval.messages.confirmPass'), cancelButtonText: t('common.cancel'), type: 'info' },
      )
      const result = await actionMap.PASS.execute({ taskId: task.id, comment: t('workflow.approval.phrases.agree') })
      if (result.success) {
        ElMessage.success(t('workflow.approval.messages.passedSuccess'))
        onSuccess?.()
      } else {
        ElMessage.error(result.message || t('workflow.approval.messages.operationFailed'))
      }
    } catch {
      // 用户取消
    }
  }

  /** 快捷签收 */
  async function quickClaim(task: FlowTaskDTO) {
    const result = await actionMap.CLAIM.execute({ taskId: task.id })
    if (result.success) {
      ElMessage.success(t('workflow.approval.messages.claimSuccess'))
      onSuccess?.()
    } else {
      ElMessage.error(result.message || t('workflow.approval.messages.claimFailed'))
    }
  }

  /** 快捷暂存 */
  async function quickSaveDraft(task: FlowTaskDTO) {
    const result = await actionMap.SAVE_DRAFT.execute({ taskId: task.id, comment: '' })
    if (result.success) {
      ElMessage.success(t('workflow.approval.messages.draftSaved'))
    } else {
      ElMessage.error(result.message || t('workflow.approval.messages.draftFailed'))
    }
  }

  /** 快捷已阅 */
  async function quickMarkRead(task: FlowTaskDTO) {
    const result = await actionMap.MARK_READ.execute({ taskId: task.id })
    if (result.success) {
      ElMessage.success(t('workflow.approval.messages.markedRead'))
    } else {
      ElMessage.error(result.message || t('workflow.approval.messages.operationFailed'))
    }
  }

  /** 快捷沟通（prompt 输入） */
  async function quickCommunicate(task: FlowTaskDTO) {
    try {
      const { value } = await ElMessageBox.prompt(t('workflow.approval.messages.communicatePrompt'), t('workflow.approval.messages.communicateTitle'), {
        confirmButtonText: t('workflow.approval.messages.send'),
        cancelButtonText: t('common.cancel'),
        inputType: 'textarea',
      })
      const result = await actionMap.COMMUNICATE.execute({
        taskId: task.id,
        comment: value,
      })
      if (result.success) {
        ElMessage.success(t('workflow.approval.messages.communicateSent'))
      } else {
        ElMessage.error(result.message || t('workflow.approval.messages.communicateFailed'))
      }
    } catch {
      // 用户取消
    }
  }

  /** 快捷催办（prompt 输入） */
  async function quickUrge(task: FlowTaskDTO) {
    try {
      const { value } = await ElMessageBox.prompt(t('workflow.approval.messages.urgePrompt'), t('workflow.approval.messages.urgeTitle'), {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
      const result = await actionMap.URGE.execute({
        taskId: task.id,
        instanceId: task.instanceId,
        comment: value,
      })
      if (result.success) {
        ElMessage.success(t('workflow.approval.messages.urgeSuccess'))
      } else {
        ElMessage.error(result.message || t('workflow.approval.messages.urgeFailed'))
      }
    } catch {
      // 用户取消
    }
  }

  /** 批量通过 */
  async function quickBatchPass(taskIds: number[]) {
    if (taskIds.length === 0) {
      ElMessage.warning(t('workflow.approval.messages.batchPassEmpty'))
      return
    }
    try {
      const { value } = await ElMessageBox.prompt(t('workflow.approval.messages.batchPassPrompt'), t('workflow.approval.messages.batchPassTitle'), {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      })
      const res = await batchPass(taskIds, value || undefined)
      if (res.data?.code === 0) {
        ElMessage.success(t('workflow.approval.messages.batchPassSuccess', { count: res.data.data || taskIds.length }))
        onSuccess?.()
      } else {
        ElMessage.error(res.data?.message || t('workflow.approval.messages.batchPassFailed'))
      }
    } catch {
      // 用户取消
    }
  }

  /**
   * P1-3: 批量签收 — 前端循环调单条 claim，收集结果并汇总提示
   *
   * 适用场景：候选任务批量领取。部分失败不影响其他任务。
   */
  async function quickBatchClaim(tasks: FlowTaskDTO[]) {
    if (tasks.length === 0) {
      ElMessage.warning('请先选择要签收的任务')
      return
    }
    try {
      await ElMessageBox.confirm(
        `确认批量签收选中的 ${tasks.length} 个任务？`,
        '批量签收确认',
        { type: 'warning' },
      )
    } catch {
      return // 用户取消
    }
    let success = 0
    let failed = 0
    for (const task of tasks) {
      const result = await actionMap.CLAIM.execute({ taskId: task.id })
      if (result.success) {
        success++
      } else {
        failed++
      }
    }
    if (failed === 0) {
      ElMessage.success(`批量签收成功：${success} 个`)
    } else {
      ElMessage.warning(`签收完成：成功 ${success} 个，失败 ${failed} 个`)
    }
    if (success > 0) {
      onSuccess?.()
    }
  }

  /**
   * P1-3: 批量已阅 — 前端循环调单条 markRead，收集结果并汇总提示
   */
  async function quickBatchMarkRead(tasks: FlowTaskDTO[]) {
    if (tasks.length === 0) {
      ElMessage.warning('请先选择要标记已阅的任务')
      return
    }
    let success = 0
    let failed = 0
    for (const task of tasks) {
      const result = await actionMap.MARK_READ.execute({ taskId: task.id })
      if (result.success) {
        success++
      } else {
        failed++
      }
    }
    if (failed === 0) {
      ElMessage.success(`批量已阅成功：${success} 个`)
    } else {
      ElMessage.warning(`已阅完成：成功 ${success} 个，失败 ${failed} 个`)
    }
    if (success > 0) {
      onSuccess?.()
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
    quickBatchClaim,
    quickBatchMarkRead,
    // 常量
    commentPhrases: commentPhraseKeys.map((k) => t(k)),
  }
}

// ===========================================
// 四、审批中心共享辅助函数（状态映射 / 格式化）
// ===========================================

/** 流程实例状态映射 */
const instanceStatusMap: Record<string, { labelKey: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  RUNNING: { labelKey: 'workflow.instance.status.RUNNING', type: 'warning' },
  SUSPENDED: { labelKey: 'workflow.instance.status.SUSPENDED', type: 'info' },
  COMPLETED: { labelKey: 'workflow.instance.status.COMPLETED', type: 'success' },
  TERMINATED: { labelKey: 'workflow.instance.status.TERMINATED', type: 'danger' },
  REJECTED: { labelKey: 'workflow.instance.status.REJECTED', type: 'danger' },
}

/** 任务状态映射 */
const taskStatusMap: Record<string, { labelKey: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  PENDING: { labelKey: 'workflow.task.status.PENDING', type: 'warning' },
  CLAIMED: { labelKey: 'workflow.task.status.CLAIMED', type: 'primary' },
  COMPLETED: { labelKey: 'workflow.task.status.COMPLETED', type: 'success' },
  REJECTED: { labelKey: 'workflow.task.status.REJECTED', type: 'danger' },
  SKIPPED: { labelKey: 'workflow.task.status.SKIPPED', type: 'info' },
  CANCELLED: { labelKey: 'workflow.task.status.CANCELLED', type: 'info' },
  TIMEOUT: { labelKey: 'workflow.task.status.TIMEOUT', type: 'danger' },
  DELEGATED: { labelKey: 'workflow.task.status.DELEGATED', type: 'info' },
  FROZEN: { labelKey: 'workflow.task.status.FROZEN', type: 'info' },
}

/** 流程实例状态标签 */
export function instanceStatusLabel(s: string): string {
  const entry = instanceStatusMap[s]
  return entry ? i18n.global.t(entry.labelKey) : s
}

/** 流程实例状态类型 */
export function instanceStatusType(s: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  return instanceStatusMap[s]?.type ?? 'info'
}

/** 任务状态标签与类型 */
export function taskStatusLabel(s: string): { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' } {
  const entry = taskStatusMap[s]
  return entry
    ? { label: i18n.global.t(entry.labelKey), type: entry.type }
    : { label: s, type: 'info' }
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
  if (s < 60) return i18n.global.t('workflow.approval.duration.second', { n: s })
  const m = Math.floor(s / 60)
  if (m < 60) return i18n.global.t('workflow.approval.duration.minute', { n: m })
  const h = Math.floor(m / 60)
  if (h < 24) return i18n.global.t('workflow.approval.duration.hour', { n: h, m: m % 60 })
  return i18n.global.t('workflow.approval.duration.day', { n: Math.floor(h / 24) })
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
