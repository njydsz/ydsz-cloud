<!--
  @fileoverview 流程实例详情页
  @description
    流程图（当前节点高亮）+ 审批轨迹时间线 + 当前任务 + 操作面板。
    集成：通过/驳回/转办/催办/终止/挂起/激活/撤回/沟通等动作。
    移动端使用 useResponsive 适配：流程图与时间线纵向排版。
    配套自研工作流 v2 引擎，PC 为主，兼顾响应式。
  @module views/workflow/instance
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 流程实例详情页
 * @module views/workflow/instance
 * @description 流程图 + 审批轨迹时间线 + 当前任务 + 操作面板
 * P0-7 + P0-8 联调：消费 getDiagram 和 getTimeline。
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { useResponsive } from '@/composables/useResponsive'
import {
  getInstance,
  getDiagram,
  getTimeline,
  passTask,
  rejectTask,
  transferTask,
  urgeTask,
  terminateInstance,
  suspendInstance,
  activateInstance,
  recallInstance,
  getFormRenderData,
} from '@/api/workflow'
import type {
  FlowInstanceDTO,
  FlowDiagramDTO,
  FlowTimelineDTO,
  FlowTaskOperateDTO,
  FormRenderDataDTO,
} from '@/api/workflow/types'
import FlowDiagramViewer from '../components/FlowDiagramViewer.vue'
import FlowTimeline from '../components/FlowTimeline.vue'
import FlowDiagramReplay from '../components/FlowDiagramReplay.vue'
import FormRenderer from '../components/FormRenderer.vue'
import UserPicker from '@/components/common/UserPicker.vue'
import TaskCommentThread from '../components/TaskCommentThread.vue'
import type { UserVO } from '@/api/system/user/types'

const route = useRoute()
const { t } = useI18n()
const { isMobile } = useResponsive()

const instance = ref<FlowInstanceDTO | null>(null)
const diagram = ref<FlowDiagramDTO | null>(null)
const timeline = ref<FlowTimelineDTO | null>(null)
const loading = ref(false)

// 表单渲染数据
const formRenderData = ref<FormRenderDataDTO | null>(null)
const formRendererRef = ref<InstanceType<typeof FormRenderer> | null>(null)

const activeTab = ref<'diagram' | 'timeline' | 'replay' | 'form' | 'comment' | 'detail'>('diagram')

// 操作弹窗
const opDialog = ref(false)
const opType = ref<'pass' | 'reject' | 'transfer' | 'terminate' | 'suspend' | 'activate' | 'recall' | 'urge'>('pass')
const opForm = reactive({
  taskId: undefined as string | undefined,
  comment: '',
  targetUserId: undefined as string | undefined,
  targetUserName: '',
  targetNodeCode: '',
  reason: '',
})

const instanceId = computed(() => (route.query.id as string) || '')

async function loadAll() {
  if (!instanceId.value) return
  loading.value = true
  try {
    const [ins, dia, tl] = await Promise.all([
      getInstance(instanceId.value),
      getDiagram(instanceId.value),
      getTimeline(instanceId.value),
    ])
    if (ins.data?.code === 0) instance.value = ins.data.data
    if (dia.data?.code === 0) diagram.value = dia.data.data
    if (tl.data?.code === 0) timeline.value = tl.data.data

    // 加载表单渲染数据（非阻塞，失败不影响主流程）
    try {
      const fr = await getFormRenderData(instanceId.value)
      if (fr.data?.code === 0 && fr.data?.data) {
        formRenderData.value = fr.data.data
      }
    } catch {
      // 表单渲染数据加载失败时静默处理
    }
  } finally {
    loading.value = false
  }
}

function openOp(type: typeof opType.value) {
  opType.value = type
  opForm.taskId = undefined
  opForm.comment = ''
  opForm.targetUserId = undefined
  opForm.targetUserName = ''
  opForm.targetNodeCode = ''
  opForm.reason = ''
  opDialog.value = true
}

// P1-5: 转办用户选择回调
function onTransferUserPicked(user: UserVO | UserVO[] | null) {
  if (Array.isArray(user)) {
    // 多选场景不应出现在转办，取第一个
    const u = user[0]
    if (u) {
      opForm.targetUserId = String(u.id)
      opForm.targetUserName = u.realName || u.username || ''
    } else {
      opForm.targetUserId = undefined
      opForm.targetUserName = ''
    }
    return
  }
  if (user && typeof user === 'object') {
    opForm.targetUserId = String(user.id)
    opForm.targetUserName = user.realName || user.username || ''
  } else {
    opForm.targetUserId = undefined
    opForm.targetUserName = ''
  }
}

async function submitOp() {
  if (opType.value === 'reject' && !opForm.comment.trim()) {
    ElMessage.warning(t('workflow.instance.messages.rejectCommentRequired'))
    return
  }
  if (opType.value === 'terminate' && !opForm.reason.trim()) {
    ElMessage.warning(t('workflow.instance.messages.terminateReasonRequired'))
    return
  }

  // 若存在动态表单，先校验并获取表单数据
  let formVariables: Record<string, unknown> | undefined
  if (formRenderData.value?.formSchema && formRendererRef.value?.hasForm) {
    const valid = await formRendererRef.value.validate()
    if (!valid) return
    formVariables = formRendererRef.value.getFormData()
  }

  try {
    let res
    if (opType.value === 'pass' || opType.value === 'reject' || opType.value === 'transfer') {
      const dto: FlowTaskOperateDTO = {
        taskId: opForm.taskId!,
        comment: opForm.comment,
        targetUserId: opForm.targetUserId,
        targetUserName: opForm.targetUserName,
        targetNodeCode: opForm.targetNodeCode || undefined,
        variables: formVariables,
      }
      if (opType.value === 'pass') res = await passTask(dto)
      else if (opType.value === 'reject') res = await rejectTask(dto)
      else if (opType.value === 'transfer') res = await transferTask(dto)
    } else if (opType.value === 'terminate') {
      res = await terminateInstance(instanceId.value, opForm.reason)
    } else if (opType.value === 'suspend') {
      res = await suspendInstance(instanceId.value)
    } else if (opType.value === 'activate') {
      res = await activateInstance(instanceId.value)
    } else if (opType.value === 'recall') {
      res = await recallInstance(instanceId.value)
    } else if (opType.value === 'urge') {
      res = await urgeTask(instanceId.value, opForm.comment)
    }
    if (res?.data?.code === 0) {
      ElMessage.success(t('common.success'))
      opDialog.value = false
      loadAll()
    } else {
      ElMessage.error(res?.data?.message || t('common.failed'))
    }
  } catch (e) {
    ElMessage.error(t('workflow.instance.messages.opFailedWithMsg', { message: (e as Error).message }))
  }
}

const statusMap = computed<Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }>>(() => ({
  RUNNING: { label: t('workflow.instance.status.RUNNING'), type: 'warning' },
  SUSPENDED: { label: t('workflow.instance.status.SUSPENDED'), type: 'info' },
  COMPLETED: { label: t('workflow.instance.status.COMPLETED'), type: 'success' },
  TERMINATED: { label: t('workflow.instance.status.TERMINATED'), type: 'danger' },
  REJECTED: { label: t('workflow.instance.status.REJECTED'), type: 'danger' },
}))

const canOperate = computed(() => {
  return instance.value && instance.value.status === 'RUNNING'
})

const canSuspend = computed(() => instance.value?.status === 'RUNNING')
const canActivate = computed(() => instance.value?.status === 'SUSPENDED')
const canRecall = computed(() => {
  return instance.value?.status === 'RUNNING' && timeline.value?.events
})

onMounted(() => loadAll())
watch(() => route.query.id, () => loadAll())
</script>

<template>
  <div class="instance-detail" v-loading="loading">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="header-title">{{ t('workflow.instance.headerTitle') }}</span>
        </template>
      </el-page-header>
    </div>
    <div v-if="instance" class="instance-summary">
      <el-card shadow="never">
        <div class="summary-row">
          <div class="summary-cell">
            <div class="cell-label">{{ t('workflow.instance.summary.flowName') }}</div>
            <div class="cell-value">{{ instance.flowName || instance.flowCode }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">{{ t('workflow.instance.summary.title') }}</div>
            <div class="cell-value">{{ instance.title || '-' }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">{{ t('workflow.instance.summary.businessNo') }}</div>
            <div class="cell-value">{{ instance.businessNo || '-' }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">{{ t('workflow.instance.summary.initiator') }}</div>
            <div class="cell-value">{{ instance.initiatorName || instance.initiatorId || '-' }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">{{ t('workflow.instance.summary.startTime') }}</div>
            <div class="cell-value">
              {{ instance.startTime ? dayjs(instance.startTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
            </div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">{{ t('workflow.instance.summary.status') }}</div>
            <div class="cell-value">
              <el-tag :type="statusMap[instance.status]?.type || 'info'" size="small">
                {{ statusMap[instance.status]?.label || instance.status }}
              </el-tag>
            </div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">{{ t('workflow.instance.summary.currentNode') }}</div>
            <div class="cell-value">{{ instance.currentNodeName || '-' }}</div>
          </div>
        </div>
        <div class="summary-actions" v-if="canOperate">
          <el-button type="primary" @click="openOp('urge')">
            <el-icon><Bell /></el-icon>{{ t('workflow.instance.buttons.urge') }}
          </el-button>
          <el-button v-if="canSuspend" @click="openOp('suspend')">
            <el-icon><VideoPause /></el-icon>{{ t('workflow.instance.buttons.suspend') }}
          </el-button>
          <el-button v-if="canActivate" @click="openOp('activate')">
            <el-icon><VideoPlay /></el-icon>{{ t('workflow.instance.buttons.activate') }}
          </el-button>
          <el-button v-if="canRecall" @click="openOp('recall')">
            <el-icon><RefreshLeft /></el-icon>{{ t('workflow.instance.buttons.recall') }}
          </el-button>
          <el-button type="danger" @click="openOp('terminate')">
            <el-icon><CircleClose /></el-icon>{{ t('workflow.instance.buttons.terminate') }}
          </el-button>
        </div>
      </el-card>
    </div>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane :label="t('workflow.instance.diagram')" name="diagram">
        <div v-if="diagram" class="diagram-wrap">
          <FlowDiagramViewer :diagram="diagram" />
        </div>
        <el-empty v-else :description="t('workflow.instance.emptyDiagram')" />
      </el-tab-pane>
      <el-tab-pane :label="t('workflow.instance.timeline')" name="timeline">
        <div v-if="timeline">
          <FlowTimeline :timeline="timeline" />
        </div>
        <el-empty v-else :description="t('workflow.instance.emptyTimeline')" />
      </el-tab-pane>
      <el-tab-pane :label="t('workflow.instance.replay')" name="replay">
        <FlowDiagramReplay
          v-if="instanceId"
          :instance-id="instanceId"
          :auto-play="false"
        />
        <el-empty v-else :description="t('workflow.replay.empty')" />
      </el-tab-pane>
      <el-tab-pane :label="t('workflow.instance.tabForm')" name="form">
        <FormRenderer
          v-if="formRenderData?.formSchema"
          ref="formRendererRef"
          :instance-id="instanceId"
          :form-schema="formRenderData.formSchema"
          :readonly="!canOperate"
        />
        <el-empty v-else :description="t('workflow.instance.formEmpty')" />
      </el-tab-pane>
      <el-tab-pane :label="t('workflow.instance.tabComment')" name="comment">
        <TaskCommentThread
          v-if="instanceId"
          :instance-id="instanceId"
          :node-code="instance?.currentNodeCode"
        />
      </el-tab-pane>
      <el-tab-pane :label="t('workflow.instance.detail')" name="detail">
        <el-descriptions v-if="instance" :column="isMobile ? 1 : 2" border>
          <el-descriptions-item :label="t('workflow.instance.detailLabels.instanceId')">{{ instance.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('workflow.instance.detailLabels.flowCode')">{{ instance.flowCode }}</el-descriptions-item>
          <el-descriptions-item :label="t('workflow.instance.detailLabels.businessType')">{{ instance.businessType || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('workflow.instance.detailLabels.businessId')">{{ instance.businessKey || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('workflow.instance.detailLabels.tenantId')">{{ instance.tenantId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('workflow.instance.detailLabels.traceId')">{{ instance.providerTraceId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('workflow.instance.detailLabels.endTime')" :span="2">
            {{ instance.endTime ? dayjs(instance.endTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="instance.variableJson" :label="t('workflow.instance.variable')" :span="2">
            <pre class="var-json">{{ instance.variableJson }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <!-- 操作弹窗 -->
    <el-dialog
      v-model="opDialog"
      :title="
        opType === 'pass' ? t('workflow.instance.opDialog.pass') :
        opType === 'reject' ? t('workflow.instance.opDialog.reject') :
        opType === 'transfer' ? t('workflow.instance.opDialog.transfer') :
        opType === 'terminate' ? t('workflow.instance.opDialog.terminate') :
        opType === 'suspend' ? t('workflow.instance.opDialog.suspend') :
        opType === 'activate' ? t('workflow.instance.opDialog.activate') :
        opType === 'recall' ? t('workflow.instance.opDialog.recall') :
        opType === 'urge' ? t('workflow.instance.opDialog.urge') : t('workflow.instance.opDialog.default')
      "
      :width="isMobile ? '90%' : '500px'"
    >
      <el-form label-position="top">
        <el-form-item :label="t('workflow.instance.opForm.taskId')" v-if="opType === 'pass' || opType === 'reject' || opType === 'transfer'">
          <el-input v-model.number="opForm.taskId" :placeholder="t('workflow.instance.opForm.taskIdPlaceholder')" />
        </el-form-item>
        <!-- 动态表单区域：当有 formSchema 且操作类型为通过/驳回时显示 -->
        <el-form-item v-if="formRenderData?.formSchema && (opType === 'pass' || opType === 'reject')" :label="t('workflow.instance.opForm.approvalForm')">
          <FormRenderer
            ref="formRendererRef"
            :instance-id="instanceId"
            :form-schema="formRenderData.formSchema"
            :readonly="false"
          />
        </el-form-item>
        <el-form-item :label="t('workflow.instance.opForm.comment')" v-if="opType === 'pass' || opType === 'reject'">
          <el-input v-model="opForm.comment" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('workflow.instance.opForm.transferTo')" v-if="opType === 'transfer'">
          <UserPicker
            :model-value="opForm.targetUserId"
            :placeholder="t('workflow.instance.opForm.transferUserPlaceholder')"
            @change="(_v, user) => onTransferUserPicked(user)"
          />
        </el-form-item>
        <el-form-item :label="t('workflow.instance.opForm.rejectNode')" v-if="opType === 'reject'">
          <el-input v-model="opForm.targetNodeCode" :placeholder="t('workflow.instance.opForm.rejectNodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('workflow.instance.opForm.reason')" v-if="opType === 'terminate' || opType === 'recall'">
          <el-input v-model="opForm.reason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('workflow.instance.opForm.urgeComment')" v-if="opType === 'urge' || opType === 'suspend'">
          <el-input v-model="opForm.comment" type="textarea" :rows="3" />
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
.instance-detail {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;
  background: #fff;
  padding: 12px 16px;
  border-radius: 6px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.instance-summary {
  margin-bottom: 16px;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.summary-cell {
  .cell-label {
    font-size: 12px;
    color: #94a3b8;
    margin-bottom: 4px;
  }
  .cell-value {
    font-size: 14px;
    color: #1e293b;
    font-weight: 500;
  }
}

.summary-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.detail-tabs {
  background: #fff;
  border-radius: 6px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.diagram-wrap {
  padding: 16px 0;
}

.var-json {
  background: #f8fafc;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

/* P2-6: 移动端 H5 适配 */
@media (max-width: 768px) {
  .instance-detail {
    padding: 8px;
  }

  .page-header {
    margin-bottom: 8px;
    padding: 8px 12px;
  }

  .header-title {
    font-size: 14px;
  }

  .instance-summary {
    margin-bottom: 8px;
  }

  /* summary 区已 grid auto-fit，仅需调小 gap */
  .summary-row {
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 8px;
    margin-bottom: 8px;
  }

  /* 操作按钮组：移动端按钮文字省略、icon 优先 */
  .summary-actions {
    gap: 6px;
    padding-top: 8px;

    :deep(.el-button) {
      padding-left: 10px;
      padding-right: 10px;

      .el-icon + span {
        display: none; /* 仅保留 icon，节省横向空间 */
      }
    }
  }

  .detail-tabs {
    padding: 8px;
  }

  .diagram-wrap {
    padding: 8px 0;
  }

  /* tab 标签紧凑显示 */
  .detail-tabs :deep(.el-tabs__item) {
    padding: 0 8px;
    font-size: 13px;
  }

  .detail-tabs :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
  }
}
</style>
