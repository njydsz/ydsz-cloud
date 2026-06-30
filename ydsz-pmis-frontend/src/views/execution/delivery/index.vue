<script setup lang="ts">
/**
 * 交付物管理
 *
 * 阶段: CD1_KICKOFF -> CD2_DESIGN -> CD3_BUILD -> CD4_UAT -> CD5_GO_LIVE
 * 类型: STANDARD(标准交付) / SPECIFIC(项目特异)
 * 状态: PENDING -> SUBMITTED -> ACCEPTED / REJECTED / WAIVED
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageDeliveryItems,
  createDeliveryItem,
  changeDeliveryItemStatus,
} from '@/api/execution/delivery'
import type { DeliveryItemVO, DeliveryItemCreateDTO } from '@/api/execution/delivery/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<DeliveryItemVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  initiationId: undefined as number | undefined,
  stage: '',
})

const stageMap = {
  CD1_KICKOFF: { label: 'CD1 启动', type: 'info' as const },
  CD2_DESIGN: { label: 'CD2 设计', type: 'primary' as const },
  CD3_BUILD: { label: 'CD3 构建', type: 'primary' as const },
  CD4_UAT: { label: 'CD4 UAT', type: 'warning' as const },
  CD5_GO_LIVE: { label: 'CD5 上线', type: 'success' as const },
}

const typeMap = {
  STANDARD: { label: '标准', type: 'info' as const },
  SPECIFIC: { label: '项目特异', type: 'primary' as const },
}

const statusMap = {
  PENDING: { label: '待提交', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  ACCEPTED: { label: '已验收', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  WAIVED: { label: '已豁免', type: 'info' as const },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageDeliveryItems(query.page, query.size, {
      keyword: query.keyword,
      status: query.status,
      initiationId: query.initiationId,
      stage: query.stage,
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
  query.initiationId = undefined
  query.stage = ''
  query.page = 1
  fetchList()
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<DeliveryItemCreateDTO>>({
  initiationId: 0,
  stage: 'CD2_DESIGN',
  type: 'SPECIFIC',
  name: '',
  level: 'NORMAL',
})

const formRules = {
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  stage: [{ required: true, message: '阶段必填', trigger: 'change' }],
  name: [{ required: true, message: '交付物名称必填', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    initiationId: 0,
    stage: 'CD2_DESIGN',
    type: 'SPECIFIC',
    name: '',
    level: 'NORMAL',
    description: '',
    ownerId: undefined,
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createDeliveryItem(form as DeliveryItemCreateDTO)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchList()
}

async function handleStatus(row: DeliveryItemVO, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    let reason: string | undefined
    if (target === 'REJECTED') {
      const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回交付物', { inputValidator: (v) => !!v || '原因必填' })
      reason = value
    }
    await changeDeliveryItemStatus({ id: row.id, targetStatus: target, reason })
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="名称" clearable /></el-form-item>
      <el-form-item label="阶段">
        <el-select v-model="query.stage" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in stageMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.EXECUTION_DELIVERY_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增交付物
      </el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="name" title="交付物名称" min-width="200" show-overflow />
        <vxe-column field="initiationName" title="项目" width="160" show-overflow />
        <vxe-column field="stage" title="阶段" width="100">
          <template #default="{ row }"><StatusTag :value="row.stage" :map="stageMap" /></template>
        </vxe-column>
        <vxe-column field="type" title="类型" width="100">
          <template #default="{ row }"><StatusTag :value="row.type" :map="typeMap" /></template>
        </vxe-column>
        <vxe-column field="level" title="重要度" width="80" align="center" />
        <vxe-column field="ownerName" title="负责人" width="100" />
        <vxe-column field="submittedAt" title="提交时间" width="170" />
        <vxe-column field="acceptedAt" title="验收时间" width="170" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="warning" size="small" @click="handleStatus(row, 'SUBMITTED')">提交</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="success" size="small" @click="handleStatus(row, 'ACCEPTED')">验收</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="danger" size="small" @click="handleStatus(row, 'REJECTED')">驳回</el-button>
            <el-button v-if="['PENDING', 'REJECTED'].includes(row.status || '')" v-permission="[PC.EXECUTION_DELIVERY_REVIEW]" link type="info" size="small" @click="handleStatus(row, 'WAIVED')">豁免</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增交付物" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="阶段" prop="stage">
          <el-select v-model="form.stage" style="width: 100%">
            <el-option v-for="(v, k) in stageMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="交付物名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="重要度">
          <el-select v-model="form.level" style="width: 100%">
            <el-option label="普通" value="NORMAL" />
            <el-option label="重要" value="IMPORTANT" />
            <el-option label="关键" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="form.ownerId" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
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
