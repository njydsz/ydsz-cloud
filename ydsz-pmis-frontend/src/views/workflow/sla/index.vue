<script setup lang="ts">
/**
 * @file SLA 管理页
 * @module views/workflow/sla
 * @description P1-2: SLA 配置与管理：
 *   1. SLA 规则预览：选择流程定义 → 展示各节点 SLA 配置（超时阈值/动作/提醒策略）
 *   2. 超时任务列表：支持手动扫描和单任务处理
 *   3. SLA 策略说明（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  listOverdueTasks,
  scanSla,
  processSlaTask,
  pageDefinitions,
  getDefinition,
} from '@/api/workflow'
import type { FlowTaskDTO, FlowDefinitionDTO, SlaStrategy, SlaRuleConfigDTO } from '@/api/workflow/types'

// ==================== SLA 规则预览 ====================
const ruleLoading = ref(false)
const flowDefinitions = ref<FlowDefinitionDTO[]>([])
const selectedDefinitionId = ref<number | null>(null)
interface NodeSlaRow {
  nodeCode: string
  nodeName: string
  nodeType: number
  slaEnabled: boolean
  timeoutMinutes?: number
  action?: SlaStrategy
  reminderIntervalMinutes?: number
  maxReminders?: number
  escalateUserId?: number | null
  autoComment?: string
}
const nodeSlaRows = ref<NodeSlaRow[]>([])

async function loadFlowDefinitions() {
  try {
    const res = await pageDefinitions({ status: 'PUBLISHED', pageNum: 1, pageSize: 200 })
    if (res.data?.code === 0) {
      flowDefinitions.value = res.data.data?.list || []
    }
  } catch {
    // 静默失败
  }
}

async function loadNodeSlaConfig(defId: number) {
  ruleLoading.value = true
  try {
    const res = await getDefinition(defId)
    if (res.data?.code === 0 && res.data?.data) {
      // FlowDefinitionDTO 暂未声明 nodes 字段，此处为运行期扩展
      const data = res.data.data as unknown as { nodes?: Array<{
        nodeCode: string
        nodeName: string
        nodeType: number
        slaConfig?: string | null
      }> }
      const nodes = data.nodes || []
      nodeSlaRows.value = nodes.map((n) => {
        const cfg = parseSlaConfig(n.slaConfig)
        return {
          nodeCode: n.nodeCode,
          nodeName: n.nodeName,
          nodeType: n.nodeType,
          slaEnabled: !!cfg,
          timeoutMinutes: cfg?.timeoutMinutes,
          action: cfg?.action,
          reminderIntervalMinutes: cfg?.reminderIntervalMinutes,
          maxReminders: cfg?.maxReminders,
          escalateUserId: cfg?.escalateUserId,
          autoComment: cfg?.autoComment,
        }
      })
    }
  } catch (e) {
    ElMessage.error('加载 SLA 规则失败：' + (e as Error).message)
  } finally {
    ruleLoading.value = false
  }
}

function parseSlaConfig(jsonStr?: string | null): Partial<SlaRuleConfigDTO> | null {
  if (!jsonStr) return null
  try {
    return JSON.parse(jsonStr) as Partial<SlaRuleConfigDTO>
  } catch {
    return null
  }
}

function onDefinitionChange(val: number | null) {
  if (val) {
    loadNodeSlaConfig(val)
  } else {
    nodeSlaRows.value = []
  }
}

// ==================== 状态 ====================
const loading = ref(false)
const scanning = ref(false)
const taskList = ref<FlowTaskDTO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// ==================== SLA 策略映射 ====================
const slaStrategyMap: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  REMIND: { label: '提醒', type: 'warning' },
  ESCALATE: { label: '升级', type: 'danger' },
  AUTO_PASS: { label: '自动通过', type: 'success' },
  AUTO_REJECT: { label: '自动驳回', type: 'danger' },
}

const slaStrategyOptions = [
  { label: '提醒', value: 'REMIND', desc: '超时后发送提醒通知给处理人' },
  { label: '升级', value: 'ESCALATE', desc: '超时后升级给上级处理人' },
  { label: '自动通过', value: 'AUTO_PASS', desc: '超时后自动通过任务' },
  { label: '自动驳回', value: 'AUTO_REJECT', desc: '超时后自动驳回任务' },
]

// ==================== 加载超时任务列表 ====================
async function loadOverdueTasks() {
  loading.value = true
  try {
    const res = await listOverdueTasks({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    if (res.data?.code === 0 && res.data?.data) {
      taskList.value = res.data.data.list || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载超时任务失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadOverdueTasks()
}

// ==================== 手动扫描 ====================
async function handleScan() {
  try {
    await ElMessageBox.confirm('确认手动扫描 SLA 超时任务？扫描将触发超时策略执行。', '扫描确认', {
      type: 'warning',
    })
    scanning.value = true
    const res = await scanSla()
    if (res.data?.code === 0) {
      const count = res.data.data
      ElMessage.success(`扫描完成，共处理 ${count || 0} 个超时任务`)
      loadOverdueTasks()
    } else {
      ElMessage.error(res.data?.message || '扫描失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('扫描失败：' + (e as Error).message)
    }
  } finally {
    scanning.value = false
  }
}

// ==================== 单任务处理 ====================
async function handleProcessTask(row: FlowTaskDTO) {
  try {
    await ElMessageBox.confirm(
      `确认处理任务「${row.nodeName || row.nodeCode}」（实例：${row.instanceId}）？`,
      '任务处理确认',
      { type: 'warning' },
    )
    const res = await processSlaTask(row.id)
    if (res.data?.code === 0) {
      ElMessage.success('任务处理成功')
      loadOverdueTasks()
    } else {
      ElMessage.error(res.data?.message || '处理失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('处理失败：' + (e as Error).message)
    }
  }
}

/** 计算超时天数 */
function getOverdueDays(row: FlowTaskDTO): number {
  if (!row.dueAt) return 0
  const due = dayjs(row.dueAt)
  const now = dayjs()
  return Math.max(0, now.diff(due, 'day'))
}

/** 获取任务的 SLA 策略（从任务扩展字段解析，后端可能通过 ext 或其他字段返回） */
function getSlaStrategy(row: FlowTaskDTO): SlaStrategy {
  const v = (row as unknown as { strategy?: string; slaStrategy?: SlaStrategy }).strategy
    || (row as unknown as { slaStrategy?: SlaStrategy }).slaStrategy
  return (v as SlaStrategy) || 'REMIND'
}

onMounted(() => {
  loadFlowDefinitions()
  loadOverdueTasks()
})
</script>

<template>
  <div class="page-sla">
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <h2>SLA 管理</h2>
          <p class="page-header__sub">监控流程超时任务，手动触发 SLA 扫描和单任务处理</p>
        </div>
        <el-button type="primary" :loading="scanning" @click="handleScan">
          <el-icon><Refresh /></el-icon>手动扫描
        </el-button>
      </div>
    </div>

    <!-- SLA 策略说明卡片 -->
    <el-card shadow="never" class="strategy-card">
      <template #header>
        <span class="card-title">SLA 策略说明</span>
      </template>
      <div class="strategy-list">
        <div v-for="opt in slaStrategyOptions" :key="opt.value" class="strategy-item">
          <el-tag :type="slaStrategyMap[opt.value]?.type || 'info'" size="small">
            {{ opt.label }}
          </el-tag>
          <span class="strategy-desc">{{ opt.desc }}</span>
        </div>
      </div>
    </el-card>

    <!-- P1-2: SLA 规则预览（按流程定义查看节点配置） -->
    <el-card shadow="never" class="page-body">
      <template #header>
        <div class="card-header">
          <span class="card-title">SLA 规则预览</span>
          <el-select
            v-model="selectedDefinitionId"
            placeholder="选择流程定义查看节点 SLA 配置"
            clearable
            filterable
            size="small"
            style="width: 280px"
            @change="onDefinitionChange"
          >
            <el-option
              v-for="def in flowDefinitions"
              :key="def.id"
              :label="def.flowName"
              :value="def.id"
            />
          </el-select>
        </div>
      </template>

      <el-table
        v-loading="ruleLoading"
        :data="nodeSlaRows"
        border
        stripe
        size="small"
        empty-text="请选择流程定义查看节点 SLA 配置"
      >
        <el-table-column prop="nodeCode" label="节点编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="nodeName" label="节点名称" min-width="120">
          <template #default="{ row }">
            {{ row.nodeName || row.nodeCode }}
          </template>
        </el-table-column>
        <el-table-column label="SLA 状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.slaEnabled" type="success" size="small">已启用</el-tag>
            <el-tag v-else type="info" size="small">未配置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="超时阈值" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.slaEnabled">{{ row.timeoutMinutes }} 分钟</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="超时动作" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.action"
              :type="slaStrategyMap[row.action]?.type || 'info'"
              size="small"
            >
              {{ slaStrategyMap[row.action]?.label || row.action }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="提醒间隔" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.slaEnabled">{{ row.reminderIntervalMinutes || 60 }} 分钟</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="最大提醒" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.slaEnabled">{{ row.maxReminders ?? 3 }} 次</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="升级用户" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.action === 'ESCALATE' && row.escalateUserId">
              {{ row.escalateUserId }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="sla-rule-tip">
        提示：SLA 配置在流程设计器节点属性面板中维护，发布后在此预览。任务超时后由系统每 60 秒自动扫描触发。
      </div>
    </el-card>

    <!-- 超时任务列表 -->
    <el-card shadow="never" class="page-body">
      <template #header>
        <div class="card-header">
          <span class="card-title">超时任务列表</span>
          <el-tag type="danger" size="small">共 {{ total }} 条</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="taskList" border stripe>
        <el-table-column prop="id" label="任务 ID" width="80" />
        <el-table-column prop="instanceId" label="实例 ID" width="80" />
        <el-table-column prop="flowName" label="流程名称" min-width="120">
          <template #default="{ row }">
            {{ row.flowName || row.flowCode }}
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="节点名称" min-width="120">
          <template #default="{ row }">
            {{ row.nodeName || row.nodeCode }}
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="处理人" min-width="100">
          <template #default="{ row }">
            {{ row.assigneeName || row.assigneeId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="任务标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="150">
          <template #default="{ row }">
            {{ row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm') : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="dueAt" label="截止时间" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'overdue-text': row.dueAt && dayjs(row.dueAt).isBefore(dayjs()) }">
              {{ row.dueAt ? dayjs(row.dueAt).format('YYYY-MM-DD HH:mm') : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="超时天数" width="90">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ getOverdueDays(row) }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="SLA 策略" width="100">
          <template #default="{ row }">
            <el-tag
              :type="slaStrategyMap[getSlaStrategy(row)]?.type || 'info'"
              size="small"
            >
              {{ slaStrategyMap[getSlaStrategy(row)]?.label || getSlaStrategy(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              link
              @click="handleProcessTask(row)"
            >处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.page-sla {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;

  &-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
  }

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

.strategy-card {
  margin-bottom: 16px;
  border-radius: 6px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.strategy-list {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.strategy-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.strategy-desc {
  font-size: 12px;
  color: #64748b;
}

.page-body {
  border-radius: 6px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.overdue-text {
  color: #dc2626;
  font-weight: 600;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-muted {
  color: #c0c4cc;
}

.sla-rule-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
