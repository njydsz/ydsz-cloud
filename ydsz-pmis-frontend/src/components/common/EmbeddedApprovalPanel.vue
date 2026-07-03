<!--
  P2-2 嵌入式审批面板
  业务页（项目立项/合同/工时/采购等）通过本组件挂载审批面板，传入业务类型与业务ID即可。
  使用示例：
    <EmbeddedApprovalPanel
      business-type="PROJECT_INITIATION"
      :business-id="row.id"
    />
-->
<template>
  <div class="embedded-approval-panel" :class="{ 'is-compact': compact }">
    <div v-if="loading" class="panel-loading">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="!view || !view.instance" class="panel-empty">
      <el-empty :description="view?.message || '未发起流程'" :image-size="80">
        <el-button
          v-if="showStartButton"
          type="primary"
          :loading="starting"
          @click="handleStart"
        >
          <el-icon><Promotion /></el-icon>
          发起审批
        </el-button>
      </el-empty>
    </div>

    <div v-else class="panel-content">
      <!-- 流程状态卡片 -->
      <div class="panel-header">
        <div class="header-left">
          <el-tag
            :type="statusTagType"
            effect="dark"
            size="large"
          >
            {{ statusLabel }}
          </el-tag>
          <span class="flow-name">{{ view.instance?.flowName || view.instance?.flowCode }}</span>
          <span v-if="myRoleLabel" class="my-role" :class="`role-${view.myRole.toLowerCase()}`">
            {{ myRoleLabel }}
          </span>
        </div>
        <div class="header-right">
          <el-tooltip content="刷新" placement="top">
            <el-button text :icon="Refresh" circle @click="loadPanel" />
          </el-tooltip>
          <el-tooltip content="查看完整流程图" placement="top">
            <el-button text :icon="Share" circle @click="emit('view-diagram')" />
          </el-tooltip>
        </div>
      </div>

      <!-- 当前节点高亮 -->
      <el-alert
        v-if="view.diagram?.currentNodeName"
        :title="`当前节点：${view.diagram.currentNodeName}`"
        type="info"
        :closable="false"
        show-icon
        class="current-node-alert"
      >
        <template #default>
          <div class="current-node-meta">
            <span v-if="view.instance?.initiatorName">
              发起人：{{ view.instance.initiatorName }}
            </span>
            <span v-if="view.instance?.startAt">
              · 发起于 {{ formatDate(view.instance.startAt as string) }}
            </span>
          </div>
        </template>
      </el-alert>

      <!-- 当前待办 -->
      <div v-if="view.currentTasks.length > 0" class="panel-section">
        <div class="section-title">
          <el-icon><Bell /></el-icon>
          <span>当前待办（{{ view.currentTasks.length }}）</span>
        </div>
        <div class="task-list">
          <div
            v-for="t in view.currentTasks"
            :key="t.taskId"
            class="task-card"
            :class="{ 'is-mine': t.mine, 'is-overdue': isOverdue(t.dueAt) }"
          >
            <div class="task-card-header">
              <span class="node-name">
                <el-icon><Avatar /></el-icon>
                {{ t.nodeName || t.nodeCode }}
                <el-tag v-if="t.mine" type="success" size="small" effect="plain">我可操作</el-tag>
              </span>
              <span class="assignee">
                <el-icon><User /></el-icon>
                {{ t.assigneeName || t.assigneeId }}
                <el-tag v-if="t.performType === 'VOTE'" size="small" type="warning">会签</el-tag>
              </span>
            </div>
            <div v-if="t.dueAt" class="task-card-meta">
              <el-icon><Timer /></el-icon>
              截止：{{ formatDate(t.dueAt) }}
              <el-tag v-if="isOverdue(t.dueAt)" type="danger" size="small">已超期</el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 审批操作按钮区 -->
      <div v-if="actionButtons.length > 0" class="panel-actions">
        <el-button
          v-for="btn in actionButtons"
          :key="btn.action"
          :type="btn.type"
          :icon="btn.icon"
          :loading="actionLoading === btn.action"
          @click="handleAction(btn.action)"
        >
          {{ btn.label }}
        </el-button>
      </div>

      <!-- AI 智能辅助（推荐审批人 / 起草意见） -->
      <div v-if="view.aiAvailable && showAiPanel" class="panel-ai">
        <el-divider>
          <span class="ai-title">
            <el-icon><MagicStick /></el-icon>
            AI 智能辅助
          </span>
        </el-divider>
        <div class="ai-actions">
          <el-button
            size="small"
            :icon="MagicStick"
            :loading="aiLoading === 'recommend'"
            @click="handleAiRecommend"
          >
            推荐审批人
          </el-button>
          <el-button
            size="small"
            :icon="EditPen"
            :loading="aiLoading === 'draft'"
            @click="handleAiDraft"
          >
            起草意见
          </el-button>
        </div>
        <div v-if="aiResult" class="ai-result">
          <pre>{{ aiResult }}</pre>
        </div>
      </div>

      <!-- 审批轨迹时间线 -->
      <div v-if="view.history.length > 0" class="panel-section">
        <div class="section-title">
          <el-icon><Clock /></el-icon>
          <span>审批轨迹（{{ view.history.length }}）</span>
        </div>
        <el-timeline>
          <el-timeline-item
            v-for="(h, i) in view.history"
            :key="i"
            :type="historyItemType(h)"
            :timestamp="formatDate(h.timestamp as string | undefined)"
            placement="top"
          >
            <div class="history-item">
              <span class="history-actor">{{ h.assigneeName || h.assigneeId || '系统' }}</span>
              <span class="history-node">在 {{ h.nodeName || h.nodeCode }}</span>
              <el-tag :type="historyActionTagType(h.action as string | undefined)" size="small">
                {{ actionLabel(h.action as string | undefined) }}
              </el-tag>
              <div v-if="h.comment" class="history-comment">"{{ h.comment }}"</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>

    <!-- 审批意见弹窗 -->
    <el-dialog
      v-model="commentDialogVisible"
      :title="commentDialogTitle"
      width="600px"
      :close-on-click-modal="false"
      append-to-body
    >
      <CommentEditor
        v-model="commentDraft"
        :enable-image="false"
        :enable-mention="false"
        :phrases="commentPhrases"
      />
      <template #footer>
        <el-button @click="commentDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="commentSubmitting"
          @click="submitComment"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 转办/委派 选人弹窗 -->
    <el-dialog
      v-model="transferDialogVisible"
      :title="transferAction === 'TRANSFER' ? '转办给他人' : '委派给他人'"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
    >
      <UserPicker v-model="transferTargetId" :options="userOptions" />
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="commentSubmitting"
          :disabled="!transferTargetId"
          @click="submitTransfer"
        >
          确定{{ transferAction === 'TRANSFER' ? '转办' : '委派' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Share,
  Promotion,
  Bell,
  Avatar,
  User,
  Timer,
  MagicStick,
  EditPen,
  Clock,
  Check,
  Close,
  Switch,
  Position,
  RefreshLeft,
  ChatLineSquare,
} from '@element-plus/icons-vue'
import {
  loadEmbeddedPanel,
  embeddedQuickAction,
  recommendApprovers,
  draftComment,
  type EmbeddedApprovalView as EmbeddedApprovalViewType,
} from '@/api/workflow'
import { CommentEditor, UserPicker } from '@/components/common'
import { logger } from '@/utils/logger'

const props = withDefaults(
  defineProps<{
    /** 业务类型（如 PROJECT_INITIATION / CONTRACT / TIMESHEET） */
    businessType: string
    /** 业务 ID */
    businessId: string | number
    /** 自定义发起流程的回调（不传则不显示"发起审批"按钮） */
    onStart?: () => Promise<unknown> | void
    /** 是否显示"发起审批"按钮（默认 false） */
    showStartButton?: boolean
    /** 紧凑模式：缩小间距与字号 */
    compact?: boolean
    /** 是否显示 AI 智能辅助区 */
    showAiPanel?: boolean
    /** 预置常用语 */
    phrases?: string[]
  }>(),
  {
    onStart: undefined,
    showStartButton: false,
    compact: false,
    showAiPanel: true,
    phrases: undefined,
  },
)

const emit = defineEmits<{
  (e: 'view-diagram'): void
  (e: 'action-success', action: string, data: unknown): void
  (e: 'action-error', action: string, err: unknown): void
}>()

const loading = ref(false)
const starting = ref(false)
const view = ref<EmbeddedApprovalViewType | null>(null)
const actionLoading = ref<string | null>(null)
const aiLoading = ref<string | null>(null)
const aiResult = ref<string>('')

// 意见弹窗
const commentDialogVisible = ref(false)
const commentDialogTitle = ref('请输入审批意见')
const commentDraft = ref('')
const commentSubmitting = ref(false)
const commentAction = ref<string>('')

const commentPhrases = computed(() => props.phrases || [
  '同意',
  '同意，请按计划推进',
  '请补充资料后再议',
  '请修改后重新提交',
])

// 转办/委派弹窗
const transferDialogVisible = ref(false)
const transferAction = ref<'TRANSFER' | 'DELEGATE'>('TRANSFER')
const transferTargetId = ref<number | null>(null)
const userOptions = ref<Array<{ id: number; username: string; realName: string }>>([])

// ============== 计算属性 ==============

const myRoleLabel = computed(() => {
  if (!view.value) return ''
  return {
    INITIATOR: '发起人',
    APPROVER: '当前审批人',
    OBSERVER: '观察者',
  }[view.value.myRole] || ''
})

const statusLabel = computed(() => {
  if (!view.value?.instance) return ''
  if (view.value.finished) return '已结束'
  const map: Record<string, string> = {
    RUNNING: '进行中',
    SUSPENDED: '已挂起',
    COMPLETED: '已完成',
    TERMINATED: '已终止',
    REJECTED: '已驳回',
  }
  return map[view.value.instance.flowStatus as string] || (view.value.instance.flowStatus as string)
})

const statusTagType = computed<'success' | 'warning' | 'info' | 'danger' | 'primary'>(() => {
  if (!view.value?.instance) return 'info'
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    RUNNING: 'primary',
    SUSPENDED: 'warning',
    COMPLETED: 'success',
    TERMINATED: 'danger',
    REJECTED: 'danger',
  }
  return map[view.value.instance.flowStatus as string] || 'info'
})

const actionButtons = computed(() => {
  if (!view.value) return []
  const map: Record<
    string,
    { type: string; icon: unknown; label: string }
  > = {
    PASS: { type: 'primary', icon: Check, label: '通过' },
    REJECT: { type: 'danger', icon: Close, label: '驳回' },
    TRANSFER: { type: 'warning', icon: Switch, label: '转办' },
    DELEGATE: { type: 'info', icon: Position, label: '委派' },
    URGE: { type: 'warning', icon: ChatLineSquare, label: '催办' },
    WITHDRAW: { type: 'info', icon: RefreshLeft, label: '撤回' },
    SUBMIT: { type: 'primary', icon: Promotion, label: '发起审批' },
  }
  return (view.value.actions || []).map((a) => ({
    action: a,
    ...(map[a] || { type: 'default', icon: null, label: a }),
  }))
})

// ============== 方法 ==============

const loadPanel = async () => {
  if (!props.businessType || !props.businessId) return
  loading.value = true
  try {
    const resp = await loadEmbeddedPanel({
      businessType: props.businessType,
      businessId: props.businessId,
    })
    // 拦截器返回 ApiResponse（含 code/data），但 mock 可能直接返回 payload
    const apiResp = resp as { data?: EmbeddedApprovalViewType; code?: number }
    const data = apiResp?.data ?? (resp as unknown as EmbeddedApprovalViewType)
    view.value = data || null
  } catch (e) {
    logger.error('[EmbeddedApprovalPanel]', e, { phase: 'load' })
    ElMessage.error('加载审批面板失败')
  } finally {
    loading.value = false
  }
}

const handleStart = async () => {
  if (!props.onStart) return
  starting.value = true
  try {
    await props.onStart()
    await loadPanel()
  } catch (e) {
    logger.error('[EmbeddedApprovalPanel]', e, { phase: 'start' })
  } finally {
    starting.value = false
  }
}

const handleAction = async (action: string) => {
  if (action === 'SUBMIT') {
    handleStart()
    return
  }
  if (action === 'PASS' || action === 'REJECT') {
    commentAction.value = action
    commentDialogTitle.value = action === 'PASS' ? '审批通过' : '审批驳回'
    commentDraft.value = ''
    commentDialogVisible.value = true
    return
  }
  if (action === 'TRANSFER' || action === 'DELEGATE') {
    transferAction.value = action
    transferTargetId.value = null
    transferDialogVisible.value = true
    return
  }
  if (action === 'URGE') {
    commentAction.value = action
    commentDialogTitle.value = '催办提醒'
    commentDraft.value = '请尽快处理，谢谢！'
    commentDialogVisible.value = true
    return
  }
  if (action === 'WITHDRAW') {
    try {
      await ElMessageBox.confirm('确认撤回该流程？', '撤回确认', {
        type: 'warning',
        confirmButtonText: '撤回',
        cancelButtonText: '取消',
      })
      await doAction(action, '')
    } catch {
      // 用户取消
    }
    return
  }
}

const submitComment = async () => {
  commentSubmitting.value = true
  try {
    await doAction(commentAction.value, commentDraft.value)
    commentDialogVisible.value = false
  } catch (e) {
    logger.error('[EmbeddedApprovalPanel]', e, { phase: 'comment' })
  } finally {
    commentSubmitting.value = false
  }
}

const submitTransfer = async () => {
  if (!transferTargetId.value) {
    ElMessage.warning('请选择转办/委派对象')
    return
  }
  commentSubmitting.value = true
  try {
    const target = userOptions.value.find((u) => u.id === transferTargetId.value)
    await embeddedQuickAction({
      businessType: props.businessType,
      businessId: props.businessId,
      action: transferAction.value,
      targetUserId: transferTargetId.value,
      targetUserName: target?.realName,
      comment: '',
    })
    transferDialogVisible.value = false
    ElMessage.success(`${transferAction.value === 'TRANSFER' ? '转办' : '委派'}成功`)
    emit('action-success', transferAction.value, null)
    await loadPanel()
  } catch (e) {
    ElMessage.error(`${transferAction.value === 'TRANSFER' ? '转办' : '委派'}失败`)
    emit('action-error', transferAction.value, e)
  } finally {
    commentSubmitting.value = false
  }
}

const doAction = async (action: string, comment: string) => {
  actionLoading.value = action
  try {
    await embeddedQuickAction({
      businessType: props.businessType,
      businessId: props.businessId,
      action: action as 'PASS' | 'REJECT' | 'TRANSFER' | 'DELEGATE' | 'URGE' | 'WITHDRAW',
      comment,
    })
    ElMessage.success(`${actionLabel(action)}成功`)
    emit('action-success', action, null)
    await loadPanel()
  } catch (e) {
    ElMessage.error(`${actionLabel(action)}失败`)
    emit('action-error', action, e)
    throw e
  } finally {
    actionLoading.value = null
  }
}

const handleAiRecommend = async () => {
  if (!view.value?.currentTasks.length) {
    ElMessage.warning('当前没有待办任务')
    return
  }
  aiLoading.value = 'recommend'
  try {
    const mine = view.value.currentTasks.find((t) => t.mine) || view.value.currentTasks[0]
    const resp = await recommendApprovers({
      flowCode: view.value.instance?.flowCode as string,
      nodeCode: mine.nodeCode,
      businessType: view.value.businessType,
      businessId: Number(view.value.businessId),
      topN: 3,
      candidates: view.value.currentTasks.map((t) => ({
        userId: Number(t.assigneeId) || 0,
        name: t.assigneeName,
      })),
    })
    const data = (resp as { data?: Array<Record<string, unknown>> }).data
    const list = data ?? (resp as unknown as Array<Record<string, unknown>>)
    aiResult.value = list && list.length
      ? list
          .map((c, i) => `${i + 1}. ${c.realName || c.name || c.username} (得分: ${(c._score as number)?.toFixed?.(3) || c._score})`)
          .join('\n')
      : '暂无推荐'
  } catch (e) {
    aiResult.value = '推荐失败：' + ((e as Error).message || '未知错误')
  } finally {
    aiLoading.value = null
  }
}

const handleAiDraft = async () => {
  if (!commentAction.value) {
    ElMessage.warning('请先选择操作类型（通过/驳回）')
    return
  }
  aiLoading.value = 'draft'
  try {
    const resp = await draftComment({
      action: commentAction.value as 'PASS' | 'REJECT' | 'TRANSFER' | 'DELEGATE' | 'URGE',
      flowName: view.value?.instance?.flowName as string,
      nodeName: view.value?.diagram?.currentNodeName as string,
      title: view.value?.instance?.title as string,
    })
    const data = (resp as { data?: { primary: string; alternatives: string[] } }).data
    const result = data ?? (resp as unknown as { primary: string; alternatives: string[] })
    if (result?.primary) {
      commentDraft.value = result.primary
      aiResult.value = `主意见：${result.primary}\n\n备选：\n${(result.alternatives || []).map((s, i) => `${i + 1}. ${s}`).join('\n')}`
    } else {
      aiResult.value = '未生成意见'
    }
  } catch (e) {
    aiResult.value = '起草失败：' + ((e as Error).message || '未知错误')
  } finally {
    aiLoading.value = null
  }
}

const actionLabel = (action?: string) => {
  const map: Record<string, string> = {
    PASS: '通过',
    REJECT: '驳回',
    TRANSFER: '转办',
    DELEGATE: '委派',
    URGE: '催办',
    WITHDRAW: '撤回',
  }
  return map[action || ''] || action || ''
}

const historyActionTagType = (action?: string): 'success' | 'danger' | 'warning' | 'info' => {
  if (action === 'PASS' || action === 'COMPLETED') return 'success'
  if (action === 'REJECT') return 'danger'
  if (action === 'TRANSFER' || action === 'DELEGATE') return 'warning'
  return 'info'
}

const historyItemType = (h: { taskStatus?: string }): 'primary' | 'success' | 'danger' | 'warning' => {
  if (h.taskStatus === 'COMPLETED') return 'success'
  if (h.taskStatus === 'REJECTED') return 'danger'
  return 'primary'
}

const isOverdue = (dueAt?: string) => {
  if (!dueAt) return false
  return new Date(dueAt).getTime() < Date.now()
}

const formatDate = (s?: string) => {
  if (!s) return ''
  try {
    const d = new Date(s)
    if (Number.isNaN(d.getTime())) return s
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return s
  }
}

// 监听业务 ID 变化自动重载
watch(
  () => [props.businessType, props.businessId],
  () => {
    loadPanel()
  },
)

onMounted(() => {
  loadPanel()
})

defineExpose({ loadPanel, view })
</script>

<style scoped lang="scss">
.embedded-approval-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px;

  &.is-compact {
    padding: 12px;
  }

  .panel-loading,
  .panel-empty {
    padding: 16px 0;
  }

  .panel-content {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;

      .flow-name {
        font-size: 16px;
        font-weight: 600;
        color: var(--el-text-color-primary);
      }

      .my-role {
        font-size: 12px;
        padding: 2px 8px;
        border-radius: 10px;

        &.role-initiator {
          background: #ecf5ff;
          color: #409eff;
        }
        &.role-approver {
          background: #f0f9eb;
          color: #67c23a;
        }
        &.role-observer {
          background: #f4f4f5;
          color: #909399;
        }
      }
    }

    .header-right {
      display: flex;
      gap: 4px;
    }
  }

  .current-node-alert {
    .current-node-meta {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-top: 4px;
    }
  }

  .panel-section {
    .section-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 8px;
      color: var(--el-text-color-regular);
    }

    .task-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .task-card {
      border: 1px solid var(--el-border-color-lighter);
      border-radius: 6px;
      padding: 10px 12px;
      background: var(--el-fill-color-blank);
      transition: all 0.2s;

      &.is-mine {
        border-color: #67c23a;
        background: #f0f9eb;
      }

      &.is-overdue {
        border-color: #f56c6c;
        background: #fef0f0;
      }

      .task-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 13px;

        .node-name {
          display: flex;
          align-items: center;
          gap: 4px;
          font-weight: 500;
        }

        .assignee {
          display: flex;
          align-items: center;
          gap: 4px;
          color: var(--el-text-color-secondary);
        }
      }

      .task-card-meta {
        margin-top: 4px;
        font-size: 12px;
        color: var(--el-text-color-secondary);
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }

  .panel-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    padding: 8px 0;
    border-top: 1px dashed var(--el-border-color-lighter);
    border-bottom: 1px dashed var(--el-border-color-lighter);
  }

  .panel-ai {
    .ai-title {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: #8e44ad;
    }

    .ai-actions {
      display: flex;
      gap: 8px;
      margin-top: 8px;
    }

    .ai-result {
      margin-top: 8px;
      padding: 10px;
      background: #faf5ff;
      border: 1px solid #e9d8fd;
      border-radius: 4px;
      font-size: 12px;
      white-space: pre-wrap;
      word-break: break-all;
    }
  }

  .history-item {
    font-size: 13px;

    .history-actor {
      font-weight: 500;
      margin-right: 4px;
    }

    .history-node {
      margin-right: 4px;
      color: var(--el-text-color-secondary);
    }

    .history-comment {
      margin-top: 4px;
      padding: 6px 8px;
      background: var(--el-fill-color-light);
      border-radius: 4px;
      color: var(--el-text-color-regular);
    }
  }
}
</style>
