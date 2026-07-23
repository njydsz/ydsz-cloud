<!--
  @fileoverview DMN 决策表管理列表页
  @description
    DMN 决策表总览：
      1. 分页展示所有决策表（tableKey / tableName / hitPolicy / version / status）；
      2. 关键词搜索 + 新建；
      3. 操作：编辑、发布、执行测试。
    与流程引擎配合实现规则路由（自研工作流 v2 引擎 ydsz_flow_*），PC 端专用。
  @module views/workflow/dmn
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file DMN 决策表管理列表页
 * @module views/workflow/dmn
 * @description P0-4: DMN 决策表管理：
 *   1. 分页展示所有决策表（tableKey/tableName/hitPolicy/version/status）
 *   2. 关键词搜索 + 新建按钮
 *   3. 操作列：编辑 / 发布 / 执行测试
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  pageDmnTables,
  publishDmnTable,
  type FlowDmnTableDTO,
  type DmnStatus,
} from '@/api/workflow/dmn'
import { isHandledError } from '@/utils/error'
import DmnEditDialog from './components/DmnEditDialog.vue'
import DmnExecuteDialog from './components/DmnExecuteDialog.vue'

const { t } = useI18n()

// ==================== 列表查询 ====================
const loading = ref(false)
const list = ref<FlowDmnTableDTO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  tableName: '',
})

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageDmnTables({
      pageNum: query.page,
      pageSize: query.size,
      tableName: query.tableName || undefined,
    })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error(t('workflow.dmn.message.loadFailed'))
    }
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onQuery() {
  query.page = 1
  fetchList()
}

function onReset() {
  query.page = 1
  query.size = 10
  query.tableName = ''
  fetchList()
}

function onPageChange() {
  fetchList()
}

async function onRefresh() {
  await fetchList()
}

// ==================== 状态映射 ====================
const statusMap: Record<DmnStatus, { label: string; type: 'info' | 'success' | 'warning' }> = {
  DRAFT: { label: '', type: 'info' },
  PUBLISHED: { label: '', type: 'success' },
  DEPRECATED: { label: '', type: 'warning' },
}

function statusLabel(s?: string): string {
  if (!s) return '-'
  return statusMap[s as DmnStatus]?.label || s
}

function statusType(s?: string): 'info' | 'success' | 'warning' {
  if (!s) return 'info'
  return statusMap[s as DmnStatus]?.type || 'info'
}

// 初始化状态标签（i18n）
function initStatusLabels() {
  statusMap.DRAFT.label = t('workflow.dmn.status.DRAFT')
  statusMap.PUBLISHED.label = t('workflow.dmn.status.PUBLISHED')
  statusMap.DEPRECATED.label = t('workflow.dmn.status.DEPRECATED')
}
initStatusLabels()

// ==================== 编辑弹窗 ====================
const editVisible = ref(false)
const editingData = ref<FlowDmnTableDTO | null>(null)

function openCreate() {
  editingData.value = null
  editVisible.value = true
}

function openEdit(row: FlowDmnTableDTO) {
  editingData.value = { ...row }
  editVisible.value = true
}

function handleSaved() {
  fetchList()
}

// ==================== 发布 ====================
async function handlePublish(row: FlowDmnTableDTO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(
      t('workflow.dmn.message.publishConfirm', { name: row.tableName }),
      t('common.tip'),
      { type: 'warning' },
    )
    await publishDmnTable(row.id)
    ElMessage.success(t('workflow.dmn.message.publishSuccess'))
    fetchList()
  } catch (e) {
    if (e !== 'cancel') {
      if (!isHandledError(e)) {
        ElMessage.error((e as Error).message)
      }
    }
  }
}

// ==================== 执行测试弹窗 ====================
const executeVisible = ref(false)
const executeData = ref<FlowDmnTableDTO | null>(null)

function openExecute(row: FlowDmnTableDTO) {
  executeData.value = { ...row }
  executeVisible.value = true
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <PageLayout
    :query="query"
    :list="list"
    :total="total"
    :loading="loading"
    @query="onQuery"
    @reset="onReset"
    @page-change="onPageChange"
    @refresh="onRefresh"
  >
    <!-- 搜索区 -->
    <template #search>
      <el-form-item :label="t('workflow.dmn.tableName')">
        <el-input
          v-model="query.tableName"
          :placeholder="t('workflow.dmn.placeholder.searchName')"
          clearable
          style="width: 220px"
          @keyup.enter="onQuery"
        />
      </el-form-item>
    </template>

    <!-- 工具栏 -->
    <template #toolbar>
      <el-button type="primary" :icon="'Plus'" @click="openCreate">
        {{ t('workflow.dmn.createTitle') }}
      </el-button>
    </template>

    <!-- 表格 -->
    <template #table>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="tableKey" :label="t('workflow.dmn.tableKey')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="tableName" :label="t('workflow.dmn.tableName')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" :label="t('workflow.dmn.description')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.description || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="hitPolicy" :label="t('workflow.dmn.hitPolicy')" width="110" align="center" />
        <el-table-column prop="version" :label="t('workflow.dmn.version')" width="90" align="center">
          <template #default="{ row }">
            v{{ row.version ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('workflow.dmn.statusLabel')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="t('workflow.dmn.updatedAt')" min-width="150">
          <template #default="{ row }">
            {{ row.updatedAt || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.edit')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row as FlowDmnTableDTO)">
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              size="small"
              @click="handlePublish(row as FlowDmnTableDTO)"
            >
              {{ t('workflow.dmn.publish') }}
            </el-button>
            <el-button link type="warning" size="small" @click="openExecute(row as FlowDmnTableDTO)">
              {{ t('workflow.dmn.execute.button') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('common.empty')" />
        </template>
      </el-table>
    </template>
  </PageLayout>

  <!-- 编辑弹窗 -->
  <DmnEditDialog v-model="editVisible" :data="editingData" @saved="handleSaved" />

  <!-- 执行测试弹窗 -->
  <DmnExecuteDialog v-model="executeVisible" :data="executeData" />
</template>
