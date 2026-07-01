<script setup lang="ts">
/**
 * 合同模板管理
 *
 * 状态: DRAFT -> PUBLISHED -> DEPRECATED
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  pageContractTemplates,
  createContractTemplate,
  changeContractTemplateStatus,
} from '@/api/project/contract'
import type { ContractTemplateVO, ContractTemplateCreateDTO } from '@/api/project/contract/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<ContractTemplateVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  type: '',
  status: '',
})

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  PUBLISHED: { label: '已发布', type: 'success' as const },
  DEPRECATED: { label: '已废弃', type: 'danger' as const },
}

const typeMap = {
  FIXED_PRICE: { label: '固定总价' },
  T_M: { label: '人月计费' },
  MILESTONE: { label: '里程碑' },
  RETAINER: { label: '框架协议' },
  LICENSE: { label: '授权' },
  SAAS: { label: 'SaaS' },
  MAINTENANCE: { label: '运维' },
  OTHER: { label: '其他' },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageContractTemplates(query.page, query.size, {
      keyword: query.keyword,
      type: query.type,
      status: query.status,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.type = ''
  query.status = ''
  query.page = 1
  fetchList()
}

/** 是否处于空态: 非加载中且列表无数据 */
const isEmpty = computed(() => !loading.value && list.value.length === 0)

/** 选中的合同模板行 (用于批量操作) */
const selectedRows = ref<ContractTemplateVO[]>([])

function onSelectionChange({ rows }: { rows: ContractTemplateVO[] }) {
  selectedRows.value = rows
}

function clearSelection() {
  selectedRows.value = []
}

const BATCH_CONCURRENCY = 4

/**
 * 通用批量状态变更: 并发上限 BATCH_CONCURRENCY, 单条失败不阻断其他.
 * 完成后弹成功/失败统计, 自动刷新列表并清空选中.
 */
async function batchChangeStatus(target: 'PUBLISHED' | 'DEPRECATED', fromStatuses: string[]) {
  const eligible = selectedRows.value.filter((r) => fromStatuses.includes(r.status))
  if (eligible.length === 0) {
    ElMessage.warning(`当前选中的 ${selectedRows.value.length} 条记录中, 没有可${target === 'PUBLISHED' ? '发布' : '废弃'}的模板`)
    return
  }
  const targetText = statusMap[target].label
  try {
    await ElMessageBox.confirm(
      `确认对 ${eligible.length} 条合同模板执行「${targetText}」吗？`,
      '批量操作',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const results = await runWithConcurrency(
    eligible,
    BATCH_CONCURRENCY,
    (row) => changeContractTemplateStatus({ id: row.id, targetStatus: target }),
  )
  const ok = results.filter((r) => r.ok).length
  const fail = results.length - ok
  if (fail === 0) {
    ElMessage.success(`批量${targetText}完成: ${ok} 条`)
  } else {
    ElMessage.warning(`批量${targetText}完成: 成功 ${ok} 条, 失败 ${fail} 条`)
  }
  clearSelection()
  fetchList()
}

async function handleBatchPublish() {
  await batchChangeStatus('PUBLISHED', ['DRAFT'])
}

async function handleBatchDeprecate() {
  await batchChangeStatus('DEPRECATED', ['PUBLISHED'])
}

async function runWithConcurrency<T, R>(
  items: T[],
  limit: number,
  worker: (item: T) => Promise<R>,
): Promise<Array<{ item: T; ok: boolean; error?: unknown }>> {
  const out: Array<{ item: T; ok: boolean; error?: unknown }> = []
  let cursor = 0
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const idx = cursor++
      const item = items[idx]
      try {
        await worker(item)
        out.push({ item, ok: true })
      } catch (error) {
        out.push({ item, ok: false, error })
      }
    }
  })
  await Promise.all(runners)
  return out
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<ContractTemplateCreateDTO>>({
  code: '',
  name: '',
  type: 'FIXED_PRICE',
  version: 'v1.0',
  content: '',
  description: '',
})

const formRules = {
  code: [{ required: true, message: '模板编码必填', trigger: 'blur' }],
  name: [{ required: true, message: '模板名称必填', trigger: 'blur' }],
  content: [{ required: true, message: '模板内容必填', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    code: '',
    name: '',
    type: 'FIXED_PRICE',
    version: 'v1.0',
    content: '',
    description: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createContractTemplate(form as ContractTemplateCreateDTO)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  fetchList()
}

async function handleStatus(row: ContractTemplateVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeContractTemplateStatus({ id: row.id, targetStatus: target })
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
}

onMounted(fetchList)
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="total"
    :loading="loading"
    @query="query.page = 1; fetchList()"
    @reset="handleReset"
    @page-change="fetchList"
    @refresh="fetchList"
  >
    <template #search>
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="编码/名称" clearable />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_CONTRACT_TEMPLATE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增模板
      </el-button>
    </template>

    <template #table>
      <EmptyState
        v-if="isEmpty"
        preset="search"
        :title="query.keyword || query.type || query.status ? '未找到匹配的合同模板' : '暂无合同模板'"
        :description="query.keyword || query.type || query.status ? '请尝试调整筛选条件或清空搜索关键字' : '当前还没有任何合同模板, 可以创建第一条模板'"
        action-text="新增模板"
        @action="openCreate"
      />
      <vxe-table v-else :data="list" :loading="loading" border stripe @checkbox-change="onSelectionChange" @checkbox-all="onSelectionChange">
        <vxe-column type="checkbox" width="50" :checkStrictly="false" />
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="code" title="编码" width="160" />
        <vxe-column field="name" title="模板名称" min-width="200" show-overflow />
        <vxe-column field="type" title="类型" width="120">
          <template #default="{ row }">
            {{ typeMap[row.type as keyof typeof typeMap]?.label || row.type || '-' }}
          </template>
        </vxe-column>
        <vxe-column field="version" title="版本" width="100" align="center" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" :map="statusMap" />
          </template>
        </vxe-column>
        <vxe-column field="description" title="说明" min-width="200" show-overflow />
        <vxe-column field="createdAt" title="创建时间" width="170" />
        <vxe-column title="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.PROJECT_CONTRACT_TEMPLATE_PUBLISH]" link type="success" size="small" @click="handleStatus(row, 'PUBLISHED')">
              发布
            </el-button>
            <el-button v-if="row.status === 'PUBLISHED'" v-permission="[PC.PROJECT_CONTRACT_TEMPLATE_PUBLISH]" link type="danger" size="small" @click="handleStatus(row, 'DEPRECATED')">
              废弃
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增合同模板" width="780px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板编码" prop="code">
              <el-input v-model="form.code" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合同类型" prop="type">
              <el-select v-model="form.type" style="width: 100%">
                <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本">
              <el-input v-model="form.version" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="模板内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="支持 ${variable} 嵌套变量" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>

<style lang="scss" scoped>
.batch-count {
  margin-left: 4px;
  font-weight: 600;
  opacity: 0.85;
}
</style>
