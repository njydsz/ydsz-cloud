<!--
  @file 审计日志页面
  @description 规则操作审计日志查询页面：支持按规则编码、操作人、操作类型、时间范围多维查询，
               展示字段级 before/after 差异对比。
               对应路由 /execution/rule-engine/audit-log。
  @module views/execution/rule-engine
-->
<script setup lang="ts">
/**
 * 审计日志页面
 *
 * 功能区域：
 *  1. 查询条件区：查询维度切换（最近/规则/操作人/类型/时间范围）+ 查询参数
 *  2. 审计日志表格：操作类型/规则/操作人/来源/变更描述/结果/时间
 *  3. 字段级差异详情对话框：before/after 快照 + 字段级 diff
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Document, Refresh, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type { AuditLogEntry } from '@/api/rule-engine'
import { logger } from '@/utils/logger'

// ==================== 响应式状态 ====================

/** 查询维度 */
const queryType = ref<'recent' | 'rule' | 'operator' | 'action' | 'timeRange'>('recent')

/** 查询参数 */
const queryParams = reactive({
  ruleCode: '',
  operator: '',
  action: '',
  limit: 50,
  startTime: '',
  endTime: '',
})

/** 时间范围快捷选项 */
const dateRange = ref<[string, string] | null>(null)

/** 审计日志列表 */
const auditLogs = ref<AuditLogEntry[]>([])

/** 加载状态 */
const loading = ref(false)

/** 详情对话框 */
const detailVisible = ref(false)
const detailEntry = ref<AuditLogEntry | null>(null)

/** 操作类型选项 */
const actionOptions = [
  { label: '创建 CREATE', value: 'CREATE' },
  { label: '更新 UPDATE', value: 'UPDATE' },
  { label: '启停 TOGGLE', value: 'TOGGLE' },
  { label: '状态变更 STATUS_CHANGE', value: 'STATUS_CHANGE' },
  { label: '回滚 ROLLBACK', value: 'ROLLBACK' },
  { label: '审批通过 APPROVE', value: 'APPROVE' },
  { label: '审批驳回 REJECT', value: 'REJECT' },
  { label: '导入 IMPORT', value: 'IMPORT' },
  { label: '导出 EXPORT', value: 'EXPORT' },
  { label: '删除 DELETE', value: 'DELETE' },
  { label: '试运行 DRY_RUN', value: 'DRY_RUN' },
  { label: '压测 STRESS_TEST', value: 'STRESS_TEST' },
  { label: '回放 REPLAY', value: 'REPLAY' },
]

// ==================== 计算属性 ====================

/** 操作类型 → 标签映射 */
const actionTagType: Record<string, string> = {
  CREATE: 'success',
  UPDATE: 'primary',
  TOGGLE: 'warning',
  STATUS_CHANGE: 'warning',
  ROLLBACK: 'danger',
  APPROVE: 'success',
  REJECT: 'danger',
  IMPORT: 'info',
  EXPORT: 'info',
  DELETE: 'danger',
  DRY_RUN: '',
  STRESS_TEST: '',
  REPLAY: '',
}

/** 字段差异列表 */
const fieldDiffList = computed(() => {
  if (!detailEntry.value?.fieldDiffs) return []
  return Object.values(detailEntry.value.fieldDiffs)
})

/** before 快照键值对 */
const beforeSnapshot = computed(() => {
  if (!detailEntry.value?.beforeSnapshot) return []
  return Object.entries(detailEntry.value.beforeSnapshot).map(([key, value]) => ({
    field: key,
    value: value,
  }))
})

/** after 快照键值对 */
const afterSnapshot = computed(() => {
  if (!detailEntry.value?.afterSnapshot) return []
  return Object.entries(detailEntry.value.afterSnapshot).map(([key, value]) => ({
    field: key,
    value: value,
  }))
})

// ==================== 方法 ====================

/** 查询审计日志 */
async function handleQuery() {
  loading.value = true
  try {
    let res: AuditLogEntry[]
    switch (queryType.value) {
      case 'recent':
        res = await ruleApi.getRecentAuditLogs(queryParams.limit)
        break
      case 'rule':
        if (!queryParams.ruleCode.trim()) {
          ElMessage.warning('请输入规则编码')
          loading.value = false
          return
        }
        res = await ruleApi.getAuditLogsByRule(queryParams.ruleCode.trim(), queryParams.limit)
        break
      case 'operator':
        if (!queryParams.operator.trim()) {
          ElMessage.warning('请输入操作人')
          loading.value = false
          return
        }
        res = await ruleApi.getAuditLogsByOperator(queryParams.operator.trim(), queryParams.limit)
        break
      case 'action':
        if (!queryParams.action) {
          ElMessage.warning('请选择操作类型')
          loading.value = false
          return
        }
        res = await ruleApi.getAuditLogsByAction(queryParams.action, queryParams.limit)
        break
      case 'timeRange':
        if (!queryParams.startTime || !queryParams.endTime) {
          ElMessage.warning('请选择时间范围')
          loading.value = false
          return
        }
        res = await ruleApi.getAuditLogsByTimeRange(
          queryParams.startTime,
          queryParams.endTime,
          queryParams.limit
        )
        break
      default:
        res = []
    }
    auditLogs.value = res
    ElMessage.success(`查询到 ${res.length} 条审计日志`)
  } catch (e: any) {
    logger.error('审计日志查询失败', e)
    ElMessage.error('查询失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

/** 查看详情 */
function handleDetail(row: AuditLogEntry) {
  detailEntry.value = row
  detailVisible.value = true
}

/** 时间范围变化 */
function handleDateRangeChange(val: [string, string] | null) {
  if (val) {
    queryParams.startTime = val[0]
    queryParams.endTime = val[1]
  } else {
    queryParams.startTime = ''
    queryParams.endTime = ''
  }
}

/** 格式化时间 */
function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

/** 获取操作类型标签 */
function getActionTag(action: string) {
  return actionTagType[action] || ''
}

/** 判断字段是否变化 */
function isFieldChanged(field: string): boolean {
  if (!detailEntry.value?.fieldDiffs) return false
  return !!detailEntry.value.fieldDiffs[field]
}

/** 获取字段旧值 */
function getOldValue(field: string): string {
  if (!detailEntry.value?.beforeSnapshot) return '-'
  const val = detailEntry.value.beforeSnapshot[field]
  return val !== null && val !== undefined ? String(val) : '-'
}

/** 获取字段新值 */
function getNewValue(field: string): string {
  if (!detailEntry.value?.afterSnapshot) return '-'
  const val = detailEntry.value.afterSnapshot[field]
  return val !== null && val !== undefined ? String(val) : '-'
}

/** 合并 before/after 字段列表 */
const allFields = computed(() => {
  const fields = new Set<string>()
  if (detailEntry.value?.beforeSnapshot) {
    Object.keys(detailEntry.value.beforeSnapshot).forEach((k) => fields.add(k))
  }
  if (detailEntry.value?.afterSnapshot) {
    Object.keys(detailEntry.value.afterSnapshot).forEach((k) => fields.add(k))
  }
  return Array.from(fields)
})

// ==================== 初始化 ====================

onMounted(() => {
  handleQuery()
})
</script>

<template>
  <div class="audit-log-page">
    <!-- 页头 -->
    <el-page-header @back="$router.push('/execution/rule-engine')" class="mb-4">
      <template #content>
        <span class="page-title">审计日志</span>
      </template>
    </el-page-header>

    <!-- 查询条件 -->
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" size="default">
        <el-form-item label="查询维度">
          <el-radio-group v-model="queryType" @change="handleQuery">
            <el-radio-button value="recent">最近</el-radio-button>
            <el-radio-button value="rule">按规则</el-radio-button>
            <el-radio-button value="operator">按操作人</el-radio-button>
            <el-radio-button value="action">按类型</el-radio-button>
            <el-radio-button value="timeRange">按时间</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 按规则 -->
        <el-form-item v-if="queryType === 'rule'" label="规则编码">
          <el-input
            v-model="queryParams.ruleCode"
            placeholder="如 EVM_RED_ALERT"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>

        <!-- 按操作人 -->
        <el-form-item v-if="queryType === 'operator'" label="操作人">
          <el-input
            v-model="queryParams.operator"
            placeholder="工号/用户名"
            clearable
            style="width: 180px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>

        <!-- 按操作类型 -->
        <el-form-item v-if="queryType === 'action'" label="操作类型">
          <el-select
            v-model="queryParams.action"
            placeholder="选择操作类型"
            style="width: 200px"
          >
            <el-option
              v-for="opt in actionOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <!-- 按时间范围 -->
        <template v-if="queryType === 'timeRange'">
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              @change="handleDateRangeChange"
              style="width: 380px"
            />
          </el-form-item>
        </template>

        <!-- 条数 -->
        <el-form-item label="条数">
          <el-input-number
            v-model="queryParams.limit"
            :min="1"
            :max="500"
            :step="10"
            style="width: 120px"
          />
        </el-form-item>

        <el-form-item>
          <el-button :icon="Search" type="primary" @click="handleQuery" :loading="loading">
            查询
          </el-button>
          <el-button :icon="Refresh" @click="handleQuery">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 审计日志表格 -->
    <el-card shadow="never">
      <el-table
        :data="auditLogs"
        v-loading="loading"
        stripe
        border
        size="default"
        style="width: 100%"
      >
        <el-table-column label="操作类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="getActionTag(row.action)" size="small">
              {{ row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ruleCode" label="规则编码" width="180" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" width="180" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="source" label="来源" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.source || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeDesc" label="变更描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && auditLogs.length === 0" description="暂无审计日志" />
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="审计日志详情"
      width="900px"
      :close-on-click-modal="false"
    >
      <template v-if="detailEntry">
        <!-- 基本信息 -->
        <el-descriptions :column="3" border size="small" class="mb-4">
          <el-descriptions-item label="操作类型">
            <el-tag :type="getActionTag(detailEntry.action)" size="small">
              {{ detailEntry.action }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="规则编码">{{ detailEntry.ruleCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规则名称">{{ detailEntry.ruleName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detailEntry.operator || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detailEntry.source || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag :type="detailEntry.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ detailEntry.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="时间" :span="3">
            {{ formatTime(detailEntry.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="变更描述" :span="3">
            {{ detailEntry.changeDesc || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailEntry.errorMessage" label="错误信息" :span="3">
            <el-text type="danger">{{ detailEntry.errorMessage }}</el-text>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 字段级差异对比 -->
        <template v-if="allFields.length > 0">
          <h4 class="section-title">字段级差异对比</h4>
          <el-table :data="allFields.map(f => ({ field: f }))" border size="small" class="diff-table">
            <el-table-column prop="field" label="字段" width="200" />
            <el-table-column label="变更前（Before）">
              <template #default="{ row }">
                <div :class="{ 'field-changed': isFieldChanged(row.field) }">
                  {{ getOldValue(row.field) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="变更后（After）">
              <template #default="{ row }">
                <div :class="{ 'field-changed': isFieldChanged(row.field) }">
                  {{ getNewValue(row.field) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="变化" width="80" align="center">
              <template #default="{ row }">
                <el-icon v-if="isFieldChanged(row.field)" color="#e6a23c" size="16">
                  <ArrowUp v-if="getNewValue(row.field) > getOldValue(row.field)" />
                  <ArrowDown v-else />
                </el-icon>
                <el-icon v-else color="#67c23a"><Check /></el-icon>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- Before 快照（JSON） -->
        <template v-if="detailEntry.beforeSnapshot && !allFields.length">
          <h4 class="section-title">操作前快照</h4>
          <pre class="json-display">{{ JSON.stringify(detailEntry.beforeSnapshot, null, 2) }}</pre>
        </template>

        <!-- After 快照（JSON） -->
        <template v-if="detailEntry.afterSnapshot && !allFields.length">
          <h4 class="section-title">操作后快照</h4>
          <pre class="json-display">{{ JSON.stringify(detailEntry.afterSnapshot, null, 2) }}</pre>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.audit-log-page {
  padding: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.diff-table .field-changed {
  background-color: #fdf6ec;
  padding: 2px 4px;
  border-radius: 2px;
  font-weight: 500;
}

.json-display {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 300px;
  overflow: auto;
}
</style>
