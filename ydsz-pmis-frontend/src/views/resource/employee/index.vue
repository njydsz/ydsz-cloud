<!--
  @file 员工管理
  @description 员工管理页面：支持按雇佣类型（全职/兼职/外包）筛选，CRUD 操作。兼职类型自动联动兼职费率选择。
  @module views/resource/employee
-->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageEmployees,
  createEmployee,
  updateEmployee,
  deleteEmployee,
} from '@/api/resource/employee'
import type { EmployeeVO, EmployeeCreateDTO } from '@/api/resource/employee/types'
import { listJobLevels } from '@/api/resource/job-level'
import type { JobLevelVO } from '@/api/resource/job-level/types'
import { listEffectivePartTimeRates } from '@/api/resource/part-time-rate'
import type { PartTimeRateVO } from '@/api/resource/part-time-rate/types'
import { listEffectiveOutsourceRates } from '@/api/resource/outsource-rate'
import type { OutsourceRateVO } from '@/api/resource/outsource-rate/types'

const loading = ref(false)
const empList = ref<EmployeeVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const keyword = ref('')
const employeeTypeFilter = ref('')
const workStatusFilter = ref('')

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogLoading = ref(false)
const editingId = ref('')

const jobLevels = ref<JobLevelVO[]>([])
const partTimeRates = ref<PartTimeRateVO[]>([])
const outsourceRates = ref<OutsourceRateVO[]>([])

const employeeTypeMap: Record<string, string> = {
  FULL_TIME: '全职',
  PART_TIME: '兼职',
  OUTSOURCE: '外包',
}

const workStatusMap: Record<string, string> = {
  ACTIVE: '在职',
  LEAVE: '离职',
  SUSPEND: '停薪留职',
  PROBATION: '试用期',
}

const segmentMap: Record<string, string> = {
  PRIMARY: '初级',
  MIDDLE: '中级',
  SENIOR: '高级',
  EXPERT: '专家',
  STRATEGIC: '战略',
}

const form = ref<EmployeeCreateDTO & { id?: string }>({
  userId: '',
  empCode: '',
  empName: '',
  gender: 'U',
  departmentId: '',
  levelCode: '',
employeeType: 'FULL_TIME',
partTimeRateId: undefined,
outsourceRateId: undefined,
  hireDate: new Date().toISOString().slice(0, 10),
  workStatus: 'ACTIVE',
})

const isPartTime = computed(() => form.value.employeeType === 'PART_TIME')
const isOutsource = computed(() => form.value.employeeType === 'OUTSOURCE')

/** 根据雇佣类型筛选可选职级 */
const filteredJobLevels = computed(() => {
  if (isPartTime.value) {
    // 兼职：展示 P1-P18（来自兼职费率列表）
    return []
  }
  return jobLevels.value
})

watch(() => form.value.employeeType, (newType) => {
if (newType !== 'PART_TIME') {
form.value.partTimeRateId = undefined
}
if (newType !== 'OUTSOURCE') {
form.value.outsourceRateId = undefined
}
})

async function fetchJobLevels() {
  try {
    const { data } = await listJobLevels()
    jobLevels.value = data || []
  } catch {
    jobLevels.value = []
  }
}

async function fetchPartTimeRates() {
try {
const { data } = await listEffectivePartTimeRates()
partTimeRates.value = data || []
} catch {
partTimeRates.value = []
}
}

async function fetchOutsourceRates() {
try {
const { data } = await listEffectiveOutsourceRates()
outsourceRates.value = data || []
} catch {
outsourceRates.value = []
}
}

async function fetchData() {
  loading.value = true
  try {
    const { data } = await pageEmployees({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      employeeType: employeeTypeFilter.value || undefined,
      workStatus: workStatusFilter.value || undefined,
    })
    empList.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = ''
  form.value = {
    userId: '',
    empCode: '',
    empName: '',
    gender: 'U',
    departmentId: '',
    levelCode: '',
employeeType: 'FULL_TIME',
partTimeRateId: undefined,
outsourceRateId: undefined,
    hireDate: new Date().toISOString().slice(0, 10),
    workStatus: 'ACTIVE',
  }
  dialogVisible.value = true
}

function openEdit(row: EmployeeVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.value = {
    id: row.id,
    userId: row.userId,
    empCode: row.empCode,
    empName: row.empName,
    gender: row.gender || 'U',
    phone: row.phone,
    email: row.email,
    departmentId: row.departmentId,
    positionId: row.positionId,
    levelCode: row.levelCode,
    employeeType: row.employeeType || 'FULL_TIME',
partTimeRateId: row.partTimeRateId,
outsourceRateId: row.outsourceRateId,
hireDate: row.hireDate,
    leaveDate: row.leaveDate,
    workStatus: row.workStatus,
    description: row.description,
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.empCode || !form.value.empName || !form.value.departmentId || !form.value.levelCode) {
    ElMessage.warning('请填写必填项')
    return
  }
if (isPartTime.value && !form.value.partTimeRateId) {
ElMessage.warning('兼职类型必须选择兼职费率')
return
}
if (isOutsource.value && !form.value.outsourceRateId) {
ElMessage.warning('外包类型必须选择外包费率')
return
}
  dialogLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      await createEmployee(form.value)
      ElMessage.success('创建成功')
    } else {
      await updateEmployee(editingId.value, form.value)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    dialogLoading.value = false
  }
}

async function handleDelete(row: EmployeeVO) {
  await ElMessageBox.confirm(`确认删除员工 ${row.empCode}（${row.empName}）？`, '提示', {
    type: 'warning',
  })
  await deleteEmployee(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  keyword.value = ''
  employeeTypeFilter.value = ''
  workStatusFilter.value = ''
  currentPage.value = 1
  fetchData()
}

onMounted(() => {
fetchJobLevels()
fetchPartTimeRates()
fetchOutsourceRates()
fetchData()
})
</script>

<template>
  <div class="employee-page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="工号/姓名" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="雇佣类型">
          <el-select v-model="employeeTypeFilter" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, key) in employeeTypeMap" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="在职状态">
          <el-select v-model="workStatusFilter" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, key) in workStatusMap" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="openCreate">新增员工</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 员工列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <vxe-table :data="empList" :loading="loading" border>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="empCode" title="工号" width="100" />
        <vxe-column field="empName" title="姓名" width="100" />
        <vxe-column field="employeeType" title="类型" width="80" align="center">
          <template #default="{ row }">
<el-tag :type="row.employeeType === 'FULL_TIME' ? 'primary' : row.employeeType === 'PART_TIME' ? 'warning' : 'success'" size="small">
{{ employeeTypeMap[row.employeeType] || row.employeeType || '-' }}
</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="levelCode" title="职级" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.employeeType === 'PART_TIME' ? 'warning' : row.employeeType === 'OUTSOURCE' ? 'success' : 'primary'">{{ row.levelCode }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="levelName" title="职级名称" min-width="140" />
<vxe-column field="partTimeRateName" title="兼职费率" min-width="140">
<template #default="{ row }">
{{ row.partTimeRateName || '-' }}
</template>
</vxe-column>
<vxe-column field="outsourceRateName" title="外包费率" min-width="140">
<template #default="{ row }">
{{ row.outsourceRateName || '-' }}
</template>
</vxe-column>
        <vxe-column field="departmentName" title="部门" min-width="120" />
        <vxe-column field="positionName" title="岗位" min-width="120" />
        <vxe-column field="workStatus" title="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.workStatus === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ workStatusMap[row.workStatus] || row.workStatus }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="hireDate" title="入职日期" width="120" />
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
      :title="dialogMode === 'create' ? '新增员工' : '编辑员工'"
      width="720px"
    >
      <el-form :model="form" label-width="100px" v-loading="dialogLoading">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" required>
              <el-input v-model="form.empCode" placeholder="如 E20260001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" required>
              <el-input v-model="form.empName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="雇佣类型" required>
              <el-select v-model="form.employeeType" style="width: 100%">
                <el-option v-for="(label, key) in employeeTypeMap" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="在职状态">
              <el-select v-model="form.workStatus" style="width: 100%">
                <el-option v-for="(label, key) in workStatusMap" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门 ID" required>
              <el-input v-model="form.departmentId" placeholder="部门 ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位 ID">
              <el-input v-model="form.positionId" placeholder="可空" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 全职/外包：选择职级 -->
        <el-form-item v-if="!isPartTime && !isOutsource" label="职级" required>
          <el-select v-model="form.levelCode" filterable style="width: 100%">
            <el-option
              v-for="lv in filteredJobLevels"
              :key="lv.levelCode"
              :label="`${lv.levelCode} - ${lv.levelName}（${segmentMap[lv.levelSegment || ''] || ''}）`"
              :value="lv.levelCode"
            />
          </el-select>
        </el-form-item>
        <!-- 兼职：选择 P1-P18 兼职费率 -->
        <template v-if="isPartTime">
          <el-form-item label="兼职费率" required>
            <el-select v-model="form.partTimeRateId" filterable style="width: 100%">
              <el-option
                v-for="rate in partTimeRates"
                :key="rate.id"
                :label="`${rate.rateCode} - ${rate.rateName}（¥${rate.totalCost}/月）`"
                :value="rate.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="兼职级别" required>
            <el-input v-model="form.levelCode" placeholder="如 P5（与所选费率一致）" />
          </el-form-item>
        </template>
        <!-- 外包：选择 V1-V18 外包费率 -->
        <template v-if="isOutsource">
          <el-form-item label="外包费率" required>
            <el-select v-model="form.outsourceRateId" filterable style="width: 100%">
              <el-option
                v-for="rate in outsourceRates"
                :key="rate.id"
                :label="`${rate.rateCode} - ${rate.rateName}（¥${rate.totalCost}/月）`"
                :value="rate.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="外包级别" required>
            <el-input v-model="form.levelCode" placeholder="如 V5（与所选费率一致）" />
          </el-form-item>
        </template>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="入职日期" required>
              <el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="离职日期">
              <el-date-picker v-model="form.leaveDate" type="date" value-format="YYYY-MM-DD" placeholder="在职为空" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
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
.employee-page {
  .filter-card { margin-bottom: 0; }
}
</style>
