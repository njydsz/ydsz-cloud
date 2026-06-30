<script setup lang="ts">
/**
 * 项目结项管理
 *
 * 类型: FORMAL(正式结项) / PRE_CLOSURE(预结项) / FORCED(强制结项)
 * 状态: DRAFT -> SUBMITTED -> APPROVED -> ARCHIVED / REJECTED
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageProjectClosures,
  createProjectClosure,
  changeProjectClosureStatus,
} from '@/api/execution/closure'
import type { ProjectClosureVO, ProjectClosureCreateDTO } from '@/api/execution/closure/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<ProjectClosureVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  type: '',
  initiationId: undefined as number | undefined,
})

const typeMap = {
  FORMAL: { label: '正式结项', type: 'primary' as const },
  PRE_CLOSURE: { label: '预结项', type: 'warning' as const },
  FORCED: { label: '强制结项', type: 'danger' as const },
}

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  APPROVED: { label: '已通过', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  ARCHIVED: { label: '已归档', type: 'info' as const },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageProjectClosures(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      type: query.type,
      initiationId: query.initiationId,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.type = ''
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<ProjectClosureCreateDTO>>({
  closureCode: '',
  initiationId: 0,
  type: 'FORMAL',
  reason: '',
  summary: '',
  lessonsLearned: '',
  warrantyEndDate: '',
})

const formRules = {
  closureCode: [{ required: true, message: '结项单号必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  type: [{ required: true, message: '结项类型必填', trigger: 'change' }],
}

function openCreate() {
  Object.assign(form, {
    closureCode: '',
    initiationId: 0,
    type: 'FORMAL',
    reason: '',
    summary: '',
    lessonsLearned: '',
    warrantyEndDate: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createProjectClosure(form as ProjectClosureCreateDTO)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchList()
}

async function handleStatus(row: ProjectClosureVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    await changeProjectClosureStatus({ id: row.id, targetStatus: target })
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="单号/项目" clearable /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.CLOSURE_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新建结项
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="closureCode" title="结项单号" width="160" />
        <vxe-column field="initiationName" title="项目" min-width="200" show-overflow />
        <vxe-column field="type" title="类型" width="100">
          <template #default="{ row }"><StatusTag :value="row.type" :map="typeMap" /></template>
        </vxe-column>
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="paymentRatio" title="回款比例" width="110" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(0)}%` : '-'" />
        <vxe-column field="grossMargin" title="毛利率" width="100" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(1)}%` : '-'" />
        <vxe-column field="applicantName" title="申请人" width="100" />
        <vxe-column field="approverName" title="审批人" width="100" />
        <vxe-column field="warrantyEndDate" title="质保期至" width="110" />
        <vxe-column field="createdAt" title="创建时间" width="170" />
        <vxe-column title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-permission="[PC.CLOSURE_STATUS]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">提交</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.CLOSURE_STATUS]" link type="success" size="small" @click="handleStatus(row, 'APPROVED')">通过</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.CLOSURE_STATUS]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">驳回</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="[PC.CLOSURE_STATUS]" link type="info" size="small" @click="handleStatus(row, 'ARCHIVED')">归档</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新建结项" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="结项单号" prop="closureCode"><el-input v-model="form.closureCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="结项类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="结项原因"><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="项目总结"><el-input v-model="form.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="经验教训"><el-input v-model="form.lessonsLearned" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="质保期至">
          <el-date-picker v-model="form.warrantyEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
