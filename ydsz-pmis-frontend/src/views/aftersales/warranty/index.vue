<script setup lang="ts">
/**
 * 质保期管理 (P7)
 *
 * 状态: ACTIVE / EXPIRING_SOON / EXPIRED / TERMINATED
 * 操作: 创建 / 终止 / 扫描即将到期 / 扫描已过期
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  pageWarranties,
  createWarranty,
  terminateWarranty,
  scanExpiringWarranty,
  scanOverdueWarranty,
} from '@/api/execution/aftersales/warranty'
import type { WarrantyVO, WarrantyCreateDTO } from '@/api/execution/aftersales/types'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<WarrantyVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  initiationId: undefined as number | undefined,
})

const statusMap = {
  ACTIVE: { label: '生效中', type: 'success' as const },
  EXPIRING_SOON: { label: '即将到期', type: 'warning' as const },
  EXPIRED: { label: '已过期', type: 'info' as const },
  TERMINATED: { label: '已终止', type: 'danger' as const },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageWarranties({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
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
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

/** 是否处于空态: 非加载中且列表无数据 */
const isEmpty = computed(() => !loading.value && list.value.length === 0)

/** 选中的质保期行 (用于批量操作) */
const selectedRows = ref<WarrantyVO[]>([])

function onSelectionChange({ rows }: { rows: WarrantyVO[] }) {
  selectedRows.value = rows
}

const dialogVisible = ref(false)
const formRef = ref<any>()
const form = reactive<Partial<WarrantyCreateDTO>>({
  initiationId: 0,
  durationMonths: 12,
  noticeDays: 30,
})

const formRules = {
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  durationMonths: [{ required: true, message: '质保期(月)必填', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, {
    initiationId: 0,
    durationMonths: 12,
    noticeDays: 30,
    startDate: undefined,
    contactName: '',
    contactPhone: '',
    description: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  await createWarranty(form as WarrantyCreateDTO)
  ElMessage.success('已创建')
  dialogVisible.value = false
  fetchList()
}

async function handleTerminate(row: WarrantyVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入提前终止原因', '终止质保期', {
      inputValidator: (v) => !!v || '原因必填',
    })
    await terminateWarranty({ id: row.id, reason: value })
    ElMessage.success('已终止')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleScan(type: 'expiring' | 'overdue') {
  if (type === 'expiring') {
    const n = await scanExpiringWarranty(30)
    ElMessage.success(`扫描到 ${n} 条即将到期质保期`)
  } else {
    const n = await scanOverdueWarranty()
    ElMessage.success(`扫描到 ${n} 条已过期质保期`)
  }
  fetchList()
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="编号/描述" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目 ID"><el-input-number v-model="query.initiationId" :min="0" :controls="false" /></el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.AFTERSALES_WARRANTY_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">
        新增质保期
      </el-button>
      <el-button v-permission="[PC.AFTERSALES_WARRANTY_SCAN]" type="warning" :icon="'Bell'" @click="handleScan('expiring')">
        扫描即将到期
      </el-button>
      <el-button v-permission="[PC.AFTERSALES_WARRANTY_SCAN]" type="danger" :icon="'Warning'" @click="handleScan('overdue')">
        扫描已过期
      </el-button>
    </template>

    <template #table>
      <EmptyState
        v-if="isEmpty"
        preset="search"
        :title="query.keyword || query.status || query.initiationId ? '未找到匹配的质保期' : '暂无质保期记录'"
        :description="query.keyword || query.status || query.initiationId ? '请尝试调整筛选条件或清空搜索关键字' : '当前还没有任何质保期, 可以为已结项项目创建质保期'"
        action-text="新增质保期"
        @action="openCreate"
      />
      <vxe-table v-else :data="list" :loading="loading" border stripe @checkbox-change="onSelectionChange" @checkbox-all="onSelectionChange">
        <vxe-column type="checkbox" width="50" />
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="warrantyCode" title="质保期编号" width="200" />
        <vxe-column field="initiationName" title="项目" min-width="200" show-overflow />
        <vxe-column field="startDate" title="开始日期" width="110" />
        <vxe-column field="endDate" title="结束日期" width="110" />
        <vxe-column field="durationMonths" title="时长(月)" width="90" align="center" />
        <vxe-column field="noticeDays" title="提前提醒(天)" width="100" align="center" />
        <vxe-column field="contactName" title="联系人" width="100" />
        <vxe-column field="contactPhone" title="联系电话" width="130" />
        <vxe-column field="status" title="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="terminatedAt" title="终止时间" width="170" />
        <vxe-column field="terminationReason" title="终止原因" min-width="180" show-overflow />
        <vxe-column title="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE' || row.status === 'EXPIRING_SOON'"
              v-permission="[PC.AFTERSALES_WARRANTY_TERMINATE]"
              link
              type="danger"
              size="small"
              @click="handleTerminate(row)"
            >
              提前终止
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>

    <el-dialog v-model="dialogVisible" title="新增质保期" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="项目 ID" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="质保期(月)" prop="durationMonths">
          <el-input-number v-model="form.durationMonths" :min="1" :max="120" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="提前提醒(天)">
          <el-input-number v-model="form.noticeDays" :min="1" :max="180" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
