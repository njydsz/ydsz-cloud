<!--
  @file 兼职职级费率管理
  @description 兼职职级费率管理页面（P1-P18）：左侧展示兼职职级列表并按段位分类，右侧展示所选职级的生效费率（月薪、商业保险、总成本、对外人天、对内人天等）及历史版本。对应路由 /resource/part-time-rate，后端服务 ydsz-userinfo。
  @module views/resource/part-time-rate
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pagePartTimeRates,
  createPartTimeRate,
  updatePartTimeRate,
  deletePartTimeRate,
} from '@/api/resource/part-time-rate'
import type { PartTimeRateVO, PartTimeRateCreateDTO } from '@/api/resource/part-time-rate/types'

/** 费率加载状态 */
const loading = ref(false)
/** 兼职费率列表数据 */
const rateList = ref<PartTimeRateVO[]>([])
/** 费率列表总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页条数 */
const pageSize = ref(20)

/** 新增/编辑弹窗显隐 */
const dialogVisible = ref(false)
/** 弹窗模式：新增 / 编辑 */
const dialogMode = ref<'create' | 'edit'>('create')
/** 弹窗提交状态 */
const dialogLoading = ref(false)
/** 编辑中的费率 ID */
const editingId = ref('')

/** 段位筛选 */
const segmentFilter = ref('')
/** 状态筛选 */
const statusFilter = ref('')
/** 搜索关键字 */
const keyword = ref('')

const segmentMap: Record<string, string> = {
  PRIMARY: '初级',
  MIDDLE: '中级',
  SENIOR: '高级',
  EXPERT: '专家',
  STRATEGIC: '战略',
}

const form = ref<PartTimeRateCreateDTO & { id?: string }>({
  rateCode: '',
  rateName: '',
  levelSegment: 'PRIMARY',
  hourlyRate: 0,
  monthlyHours: 176,
  commercialInsurance: 0,
  travelReimbursement: 0,
  travelAllowance: 0,
  externalDaily: 0,
  internalDaily: 0,
  billableTarget: 0.7,
  sortOrder: 1,
  effectiveDate: new Date().toISOString().slice(0, 10),
  expireDate: undefined,
  version: 1,
  description: '',
  status: 'ACTIVE',
})

/** 兼职核心：月薪 = 时薪 × 月工时数（自动推导） */
const computedMonthlySalary = computed(() => {
  const hourly = Number(form.value.hourlyRate) || 0
  const hours = Number(form.value.monthlyHours) || 176
  return (hourly * hours).toFixed(2)
})

const computedTotalCost = computed(() => {
  const salary = Number(computedMonthlySalary.value) || 0
  const insurance = Number(form.value.commercialInsurance) || 0
  const reimbursement = Number(form.value.travelReimbursement) || 0
  const allowance = Number(form.value.travelAllowance) || 0
  return (salary + insurance + reimbursement + allowance).toFixed(2)
})

async function fetchData() {
  loading.value = true
  try {
    const { data } = await pagePartTimeRates({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      levelSegment: segmentFilter.value || undefined,
      status: statusFilter.value || undefined,
    })
    rateList.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = ''
  form.value = {
    rateCode: '',
    rateName: '',
    levelSegment: 'PRIMARY',
    hourlyRate: 0,
    monthlyHours: 176,
    commercialInsurance: 0,
    travelReimbursement: 0,
    travelAllowance: 0,
    externalDaily: 0,
    internalDaily: 0,
    billableTarget: 0.7,
    sortOrder: 1,
    effectiveDate: new Date().toISOString().slice(0, 10),
    expireDate: undefined,
    version: 1,
    description: '',
    status: 'ACTIVE',
  }
  dialogVisible.value = true
}

function openEdit(row: PartTimeRateVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.value = {
    id: row.id,
    rateCode: row.rateCode,
    rateName: row.rateName,
    levelSegment: row.levelSegment || 'PRIMARY',
    hourlyRate: row.hourlyRate || 0,
    monthlyHours: row.monthlyHours || 176,
    commercialInsurance: row.commercialInsurance || 0,
    travelReimbursement: row.travelReimbursement || 0,
    travelAllowance: row.travelAllowance || 0,
    externalDaily: row.externalDaily || 0,
    internalDaily: row.internalDaily || 0,
    billableTarget: row.billableTarget || 0.7,
    sortOrder: row.sortOrder || 1,
    effectiveDate: row.effectiveDate || new Date().toISOString().slice(0, 10),
    expireDate: row.expireDate || undefined,
    version: row.version || 1,
    description: row.description || '',
    status: row.status || 'ACTIVE',
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.rateCode || !form.value.rateName || !form.value.hourlyRate) {
    ElMessage.warning('请填写必填项')
    return
  }
  dialogLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      await createPartTimeRate(form.value)
      ElMessage.success('创建成功')
    } else {
      await updatePartTimeRate(editingId.value, form.value)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    dialogLoading.value = false
  }
}

async function handleDelete(row: PartTimeRateVO) {
  await ElMessageBox.confirm(`确认删除兼职费率 ${row.rateCode}（${row.rateName}）？`, '提示', {
    type: 'warning',
  })
  await deletePartTimeRate(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

function formatMoney(v?: number) {
  if (v === undefined || v === null) return '-'
  return `¥${Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

function formatPct(v?: number) {
  if (v === undefined || v === null) return '-'
  return `${(Number(v) * 100).toFixed(0)}%`
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  keyword.value = ''
  segmentFilter.value = ''
  statusFilter.value = ''
  currentPage.value = 1
  fetchData()
}

onMounted(fetchData)
</script>

<template>
  <div class="part-time-rate-page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="级别编码/名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="段位">
          <el-select v-model="segmentFilter" placeholder="全部" clearable>
            <el-option v-for="(label, key) in segmentMap" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable>
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
          <el-button type="success" @click="openCreate">{{ $t('common.addPartTimeRate') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 费率列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <vxe-table :data="rateList" :loading="loading" border>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="rateCode" title="级别编码" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.rateCode }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="rateName" title="级别名称" min-width="160" />
        <vxe-column field="levelSegment" title="段位" width="80" align="center">
          <template #default="{ row }">
            {{ segmentMap[row.levelSegment] || row.levelSegment || '-' }}
          </template>
        </vxe-column>
        <vxe-column field="hourlyRate" title="时薪" width="100" align="right">
          <template #default="{ row }">{{ formatMoney(row.hourlyRate) }}</template>
        </vxe-column>
        <vxe-column field="monthlyHours" title="月工时" width="90" align="center">
          <template #default="{ row }">{{ row.monthlyHours ?? 176 }}h</template>
        </vxe-column>
        <vxe-column field="monthlySalary" title="月薪(自动)" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.monthlySalary) }}</template>
        </vxe-column>
        <vxe-column field="commercialInsurance" title="商业保险" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.commercialInsurance) }}</template>
        </vxe-column>
        <vxe-column field="travelReimbursement" title="差旅报销" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.travelReimbursement) }}</template>
        </vxe-column>
        <vxe-column field="travelAllowance" title="差旅补贴" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.travelAllowance) }}</template>
        </vxe-column>
        <vxe-column field="totalCost" title="总成本" width="120" align="right">
          <template #default="{ row }">
            <span style="font-weight: 600; color: var(--el-color-primary)">{{ formatMoney(row.totalCost) }}</span>
          </template>
        </vxe-column>
        <vxe-column field="externalDaily" title="对外人天" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.externalDaily) }}</template>
        </vxe-column>
        <vxe-column field="internalDaily" title="对内人天" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.internalDaily) }}</template>
        </vxe-column>
        <vxe-column field="billableTarget" title="利用率目标" width="100" align="center">
          <template #default="{ row }">{{ formatPct(row.billableTarget) }}</template>
        </vxe-column>
        <vxe-column field="version" title="版本" width="70" align="center" />
        <vxe-column field="status" title="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增兼职职级费率' : '编辑兼职职级费率'"
      width="640px"
    >
      <el-form :model="form" label-width="120px" v-loading="dialogLoading">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="级别编码" required>
              <el-input v-model="form.rateCode" placeholder="如 P5" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="级别名称" required>
              <el-input v-model="form.rateName" placeholder="如 兼职高级工程师" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="级别段位" required>
              <el-select v-model="form.levelSegment" style="width: 100%">
                <el-option v-for="(label, key) in segmentMap" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序序号">
              <el-input-number v-model="form.sortOrder" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="时薪" required>
              <el-input-number v-model="form.hourlyRate" :min="0" :precision="2" :step="0.01" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="月工时数">
              <el-input-number v-model="form.monthlyHours" :min="1" :precision="2" :step="8" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="月薪(自动)">
          <span style="font-size: 16px; font-weight: 600; color: var(--el-color-success)">
            ¥{{ computedMonthlySalary }}
          </span>
          <span style="margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px">
            = 时薪 × 月工时数
          </span>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商业保险">
              <el-input-number v-model="form.commercialInsurance" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="差旅报销">
              <el-input-number v-model="form.travelReimbursement" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="差旅补贴">
              <el-input-number v-model="form.travelAllowance" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="总成本（自动）">
          <span style="font-size: 18px; font-weight: 600; color: var(--el-color-primary)">
            ¥{{ computedTotalCost }}
          </span>
          <span style="margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px">
            = 月薪 + 商业保险 + 差旅报销 + 差旅补贴
          </span>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="对外人天单价">
              <el-input-number v-model="form.externalDaily" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对内人天成本">
              <el-input-number v-model="form.internalDaily" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="利用率目标">
              <el-input-number v-model="form.billableTarget" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号">
              <el-input-number v-model="form.version" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生效日期" required>
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效日期">
              <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="空=长期有效" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.part-time-rate-page {
  .filter-card { margin-bottom: 0; }
}
</style>
