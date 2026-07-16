<!--
  @file 合同模板管理
  @description 合同模板的查询与新增；模板编码 code 唯一，状态按 DRAFT → PUBLISHED → DEPRECATED 线性转换；对接 @/api/contract
  @module views/contract-template
-->
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  pageContractTemplates,
  createContractTemplate,
  changeContractTemplateStatus,
} from '@/api/contract'
import type { ContractTemplateVO, ContractTemplateCreateDTO } from '@/api/contract/types'
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

/** 拉取合同模板分页列表 */
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

/** 重置查询条件并重新加载列表 */
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

/** 表格勾选行变更回调，同步 selectedRows */
function onSelectionChange({ records }: { records: ContractTemplateVO[] }) {
  selectedRows.value = records
}

// ===== 新增表单弹窗 =====
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
/** 提交中状态（防重复提交） */
const submitting = ref(false)
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

/** 打开新增模板弹窗，重置表单为初始值 */
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

/** 提交新增模板表单：校验通过后创建并刷新列表 */
async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await createContractTemplate(form as ContractTemplateCreateDTO)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

/**
 * 变更模板状态（二次确认），状态机：DRAFT → PUBLISHED → DEPRECATED
 * @param row 选中的模板行数据
 * @param target 目标状态编码
 */
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
        <el-input v-model="query.keyword" placeholder="编码/名称" clearable @keyup.enter="query.page = 1; fetchList()" />
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

    <template #table="scope">
      <EmptyState
        v-if="isEmpty"
        preset="search"
        :title="query.keyword || query.type || query.status ? '未找到匹配的合同模板' : '暂无合同模板'"
        :description="query.keyword || query.type || query.status ? '请尝试调整筛选条件或清空搜索关键字' : '当前还没有任何合同模板, 可以创建第一条模板'"
        action-text="新增模板"
        @action="openCreate"
      />
      <vxe-table v-else :data="list" :loading="loading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY" @checkbox-change="onSelectionChange" @checkbox-all="onSelectionChange">
        <vxe-column type="checkbox" width="50" :check-strictly="false" />
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

    <!-- 新增模板弹窗 -->
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
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitForm">确定</el-button>
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
