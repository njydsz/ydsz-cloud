<script setup lang="ts">
/**
 * @file SLA 管理页
 * @module views/workflow/sla
 * @description P1-2: SLA 配置与管理，显示超时任务列表，支持手动扫描和单任务处理，
 *   展示 SLA 策略（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）。
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  listOverdueTasks,
  scanSla,
  processSlaTask,
} from '@/api/workflow'
import type { FlowTaskDTO } from '@/api/workflow/types'
import type { SlaStrategy } from '@/api/workflow/types'

// ==================== 状态 ====================
const loading = ref(false)
const scanning = ref(false)
const taskList = ref<FlowTaskDTO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// ==================== SLA 策略映射 ====================
const slaStrategyMap: Record<string, { label: string; type: string }> = {
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
function getSlaStrategy(row: any): string {
  return row.strategy || row.slaStrategy || 'REMIND'
}

onMounted(() => loadOverdueTasks())
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
          <el-tag :type="(slaStrategyMap[opt.value]?.type as any) || 'info'" size="small">
            {{ opt.label }}
          </el-tag>
          <span class="strategy-desc">{{ opt.desc }}</span>
        </div>
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
              :type="(slaStrategyMap[getSlaStrategy(row)]?.type as any) || 'info'"
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
</style>
