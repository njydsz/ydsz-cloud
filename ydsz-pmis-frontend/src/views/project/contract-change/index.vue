<!--
  @file 合同变更管理
  @description 合同变更单的查询与新增；状态按 DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED → CLOSED 流转
  @module views/project/contract-change
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageContractChanges,
  createContractChange,
  changeContractChangeStatus,
} from '@/api/project/contract'
import type { ContractChangeVO, ContractChangeCreateDTO } from '@/api/project/contract/types'
import { PC } from '@/constants/permissionCodes'

// ===== 列表查询状态 =====
const loading = ref(false)
const list = ref<ContractChangeVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  contractId: undefined as number | undefined,
  status: '',
  changeType: '',
})

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  UNDER_REVIEW: { label: '审批中', type: 'warning' as const },
  APPROVED: { label: '已通过', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  CLOSED: { label: '已关闭', type: 'info' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

const typeMap = {
  SCOPE: { label: '范围变更' },
  COST: { label: '成本变更' },
  TERM: { label: '条款变更' },
  STAFF: { label: '人员变更' },
  SCHEDULE: { label: '进度变更' },
  OTHER: { label: '其他' },
}

const impactMap = {
  LOW: { label: '低', type: 'success' as const },
  MEDIUM: { label: '中', type: 'warning' as const },
  HIGH: { label: '高', type: 'danger' as const },
}

// ===== 列表加载 =====
/** 拉取合同变更分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageContractChanges(query.page, query.size, {
      contractId: query.contractId,
      status: query.status,
      changeType: query.changeType,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并重新加载列表 */
function handleReset() {
  query.contractId = undefined
  query.status = ''
  query.changeType = ''
  query.page = 1
  fetchList()
}

// ===== 新增变更表单弹窗 =====
const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<ContractChangeCreateDTO>>({
  contractId: 0,
  changeType: 'SCOPE',
  reason: '',
  beforeValue: '',
  afterValue: '',
})

const formRules = {
  contractId: [{ required: true, message: '合同 ID 必填', trigger: 'blur' }],
  changeType: [{ required: true, message: '变更类型必填', trigger: 'change' }],
  reason: [{ required: true, message: '变更原因必填', trigger: 'blur' }],
}

/** 打开新增变更弹窗，重置表单为初始值 */
function openCreate() {
  Object.assign(form, {
    contractId: 0,
    changeType: 'SCOPE',
    reason: '',
    beforeValue: '',
    afterValue: '',
  })
  dialogVisible.value = true
}

/** 提交新增变更表单：校验通过后创建并刷新列表 */
async function submitForm() {
  await formRef.value?.validate()
  await createContractChange(form as ContractChangeCreateDTO)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  fetchList()
}

/** 变更单状态流转（需二次确认），状态机见文件头 */
async function handleStatus(row: ContractChangeVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeContractChangeStatus({ id: row.id, targetStatus: target })
    ElMessage.success('状态已更新')
    fetchList()
  } catch { /* 取消 */ }
}

// ===== 生命周期 =====
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
      <el-form-item label="合同 ID">
        <el-input-number v-model="query.contractId" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_CONTRACT_CHANGE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增变更
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="contractCode" title="合同" width="160" />
        <vxe-column field="changeType" title="类型" width="100">
          <template #default="{ row }">
            {{ typeMap[row.changeType as keyof typeof typeMap]?.label || row.changeType || '-' }}
          </template>
        </vxe-column>
        <vxe-column field="impactLevel" title="影响" width="80" align="center">
          <template #default="{ row }">
            <StatusTag v-if="row.impactLevel" :value="row.impactLevel" :map="impactMap" />
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="reason" title="原因" min-width="200" show-overflow />
        <vxe-column field="beforeValue" title="变更前" min-width="160" show-overflow />
        <vxe-column field="afterValue" title="变更后" min-width="160" show-overflow />
        <vxe-column field="applicantName" title="申请人" width="100" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" :map="statusMap" />
          </template>
        </vxe-column>
        <vxe-column field="createdAt" title="创建时间" width="170" />
        <vxe-column title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.PROJECT_CONTRACT_CHANGE_APPROVE]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">
              提交
            </el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.PROJECT_CONTRACT_CHANGE_APPROVE]" link type="primary" size="small" @click="handleStatus(row, 'UNDER_REVIEW')">
              进入评审
            </el-button>
            <el-button v-if="row.status === 'UNDER_REVIEW'" v-permission="[PC.PROJECT_CONTRACT_CHANGE_APPROVE]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">
              通过
            </el-button>
            <el-button v-if="row.status === 'UNDER_REVIEW'" v-permission="[PC.PROJECT_CONTRACT_CHANGE_APPROVE]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">
              驳回
            </el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.PROJECT_CONTRACT_CHANGE_APPROVE]" link type="info" size="small" @click="handleStatus(row, 'CLOSED')">
              关闭
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <!-- 新增变更弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增合同变更" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="合同 ID" prop="contractId">
          <el-input-number v-model="form.contractId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="变更类型" prop="changeType">
          <el-select v-model="form.changeType" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="变更前">
          <el-input v-model="form.beforeValue" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="变更后">
          <el-input v-model="form.afterValue" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
