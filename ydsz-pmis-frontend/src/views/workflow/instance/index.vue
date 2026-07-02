<script setup lang="ts">
/**
 * @file 流程实例详情页
 * @module views/workflow/instance
 * @description 流程图 + 审批轨迹时间线 + 当前任务 + 操作面板
 * P0-7 + P0-8 联调：消费 getDiagram 和 getTimeline。
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
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
} from '@/api/workflow'
import type {
  FlowInstanceDTO,
  FlowDiagramDTO,
  FlowTimelineDTO,
  FlowTaskOperateDTO,
} from '@/api/workflow/types'
import FlowDiagramViewer from '../components/FlowDiagramViewer.vue'
import FlowTimeline from '../components/FlowTimeline.vue'

const route = useRoute()

const instance = ref<FlowInstanceDTO | null>(null)
const diagram = ref<FlowDiagramDTO | null>(null)
const timeline = ref<FlowTimelineDTO | null>(null)
const loading = ref(false)

const activeTab = ref<'diagram' | 'timeline' | 'detail'>('diagram')

// 操作弹窗
const opDialog = ref(false)
const opType = ref<'pass' | 'reject' | 'transfer' | 'terminate' | 'suspend' | 'activate' | 'recall' | 'urge'>('pass')
const opForm = reactive({
  taskId: undefined as number | undefined,
  comment: '',
  targetUserId: undefined as number | undefined,
  targetUserName: '',
  targetNodeCode: '',
  reason: '',
})

const instanceId = computed(() => Number(route.query.id) || 0)

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

async function submitOp() {
  if (opType.value === 'reject' && !opForm.comment.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  if (opType.value === 'terminate' && !opForm.reason.trim()) {
    ElMessage.warning('请填写终止原因')
    return
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
      res = await urgeTask({ instanceId: instanceId.value, comment: opForm.comment })
    }
    if (res?.data?.code === 0) {
      ElMessage.success('操作成功')
      opDialog.value = false
      loadAll()
    } else {
      ElMessage.error(res?.data?.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败：' + (e as Error).message)
  }
}

const statusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '审批中', type: 'warning' },
  SUSPENDED: { label: '已挂起', type: 'info' },
  COMPLETED: { label: '已完成', type: 'success' },
  TERMINATED: { label: '已终止', type: 'danger' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

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
          <span class="header-title">流程实例详情</span>
        </template>
      </el-page-header>
    </div>
    <div v-if="instance" class="instance-summary">
      <el-card shadow="never">
        <div class="summary-row">
          <div class="summary-cell">
            <div class="cell-label">流程名称</div>
            <div class="cell-value">{{ instance.flowName || instance.flowCode }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">标题</div>
            <div class="cell-value">{{ instance.title || '-' }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">业务单号</div>
            <div class="cell-value">{{ instance.businessNo || '-' }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">发起人</div>
            <div class="cell-value">{{ instance.initiatorName || instance.initiatorId || '-' }}</div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">发起时间</div>
            <div class="cell-value">
              {{ instance.startTime ? dayjs(instance.startTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
            </div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">状态</div>
            <div class="cell-value">
              <el-tag :type="(statusMap[instance.status]?.type as any) || 'info'" size="small">
                {{ statusMap[instance.status]?.label || instance.status }}
              </el-tag>
            </div>
          </div>
          <div class="summary-cell">
            <div class="cell-label">当前节点</div>
            <div class="cell-value">{{ instance.currentNodeName || '-' }}</div>
          </div>
        </div>
        <div class="summary-actions" v-if="canOperate">
          <el-button type="primary" @click="openOp('urge')">
            <el-icon><Bell /></el-icon>催办
          </el-button>
          <el-button v-if="canSuspend" @click="openOp('suspend')">
            <el-icon><VideoPause /></el-icon>挂起
          </el-button>
          <el-button v-if="canActivate" @click="openOp('activate')">
            <el-icon><VideoPlay /></el-icon>激活
          </el-button>
          <el-button v-if="canRecall" @click="openOp('recall')">
            <el-icon><RefreshLeft /></el-icon>撤回
          </el-button>
          <el-button type="danger" @click="openOp('terminate')">
            <el-icon><CircleClose /></el-icon>终止
          </el-button>
        </div>
      </el-card>
    </div>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="流程图" name="diagram">
        <div v-if="diagram" class="diagram-wrap">
          <FlowDiagramViewer :diagram="diagram" />
        </div>
        <el-empty v-else description="暂无流程图" />
      </el-tab-pane>
      <el-tab-pane label="审批轨迹" name="timeline">
        <div v-if="timeline">
          <FlowTimeline :timeline="timeline" />
        </div>
        <el-empty v-else description="暂无审批轨迹" />
      </el-tab-pane>
      <el-tab-pane label="实例详情" name="detail">
        <el-descriptions v-if="instance" :column="2" border>
          <el-descriptions-item label="实例 ID">{{ instance.id }}</el-descriptions-item>
          <el-descriptions-item label="流程编码">{{ instance.flowCode }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ instance.businessType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务 ID">{{ instance.businessKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="租户 ID">{{ instance.tenantId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="链路追踪">{{ instance.providerTraceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间" :span="2">
            {{ instance.endTime ? dayjs(instance.endTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="instance.variableJson" label="流程变量" :span="2">
            <pre class="var-json">{{ instance.variableJson }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <!-- 操作弹窗 -->
    <el-dialog
      v-model="opDialog"
      :title="
        opType === 'pass' ? '通过审批' :
        opType === 'reject' ? '驳回审批' :
        opType === 'transfer' ? '转办' :
        opType === 'terminate' ? '终止流程' :
        opType === 'suspend' ? '挂起流程' :
        opType === 'activate' ? '激活流程' :
        opType === 'recall' ? '撤回流程' :
        opType === 'urge' ? '催办' : '操作'
      "
      width="500px"
    >
      <el-form label-position="top">
        <el-form-item label="任务 ID" v-if="opType === 'pass' || opType === 'reject' || opType === 'transfer'">
          <el-input v-model.number="opForm.taskId" placeholder="任务 ID" />
        </el-form-item>
        <el-form-item label="审批意见" v-if="opType === 'pass' || opType === 'reject'">
          <el-input v-model="opForm.comment" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="目标用户 ID" v-if="opType === 'transfer'">
          <el-input v-model.number="opForm.targetUserId" />
        </el-form-item>
        <el-form-item label="目标用户姓名" v-if="opType === 'transfer'">
          <el-input v-model="opForm.targetUserName" />
        </el-form-item>
        <el-form-item label="驳回到节点" v-if="opType === 'reject'">
          <el-input v-model="opForm.targetNodeCode" placeholder="可选" />
        </el-form-item>
        <el-form-item label="原因" v-if="opType === 'terminate' || opType === 'recall'">
          <el-input v-model="opForm.reason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="催办意见" v-if="opType === 'urge' || opType === 'suspend'">
          <el-input v-model="opForm.comment" type="textarea" :rows="3" />
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
</style>
