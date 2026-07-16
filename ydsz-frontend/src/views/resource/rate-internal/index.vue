<!--
  @file 内部价目表
  @description 对内职级成本费率(Rate Internal)管理页面：按(职级×部门)维护每日/每小时内部成本价，支持二级回退(level→department)匹配，对应路由 /resource/rate-internal
  @module views/resource/rate-internal
-->
<script setup lang="ts">
/**
 * 对内职级成本费率 (Rate Internal) 管理
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  pageRateInternal,
  createRateInternal,
  updateRateInternal,
  deleteRateInternal,
} from '@/api/resource/rate-internal'
import type { RateInternalVO, RateInternalCreateDTO } from '@/api/resource/rate-internal'
import { listRanks } from '@/api/resource/rank'
import type { RankVO } from '@/api/resource/rank/types'
import { listDeptTree } from '@/api/system/dept'
import type { DeptVO } from '@/api/system/dept/types'
import { PC } from '@/constants/permissionCodes'
import { useUserStore } from '@/store/modules/user'

// 权限助手：统一通过 userStore 校验按钮级权限
const userStore = useUserStore()
const hasPerm = (code: string) => userStore.hasPermission(code)

// 列表查询状态
const loading = ref(false)
const list = ref<RateInternalVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  levelCode: '',
  departmentId: undefined as number | undefined,
  status: '',
})
// 职级下拉数据
const levels = ref<RankVO[]>([])
// 部门树下拉数据
const depts = ref<DeptVO[]>([])

/** 拉取职级下拉数据（用于表单与查询条件） */
async function fetchLevels() {
  try {
    const { data } = await listRanks()
    levels.value = data || []
  } catch {
    levels.value = []
  }
}
/** 拉取部门树下拉数据（用于表单与查询条件） */
async function fetchDepts() {
  try {
    const { data } = await listDeptTree()
    depts.value = data || []
  } catch {
    depts.value = []
  }
}

/** 拉取内部费率分页数据 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageRateInternal(query.page, query.size, {
      levelCode: query.levelCode || undefined,
      departmentId: query.departmentId,
      status: query.status || undefined,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 状态字典：生效/停用
const statusMap: Record<string, { label: string; type: 'success' | 'info' }> = {
  ACTIVE: { label: '生效', type: 'success' },
  INACTIVE: { label: '停用', type: 'info' },
}

/** 金额格式化：千分位 + ¥ 前缀，空值返回 - */
function fmtMoney(n?: number) {
  if (n === undefined || n === null) return '-'
  return `¥${Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

/** 点击查询：重置页码后拉取列表 */
function onQuery() {
  query.page = 1
  fetchList()
}
/** 重置查询条件并刷新列表 */
function onReset() {
  query.page = 1
  query.size = 10
  query.levelCode = ''
  query.departmentId = undefined
  query.status = ''
  fetchList()
}
/** 翻页回调 */
function onPageChange() {
  fetchList()
}
/** 手动刷新列表 */
async function onRefresh() {
  await fetchList()
}

/** 提交按钮 loading 状态，防止重复提交 */
const submitting = ref(false)
// 弹窗 - 新建/编辑内部费率
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const form = reactive<RateInternalCreateDTO>({
  rateCode: '',
  levelCode: '',
  departmentId: undefined,
  departmentName: '',
  billingUnit: 'DAY',
  costAmount: 0,
  currency: 'CNY',
  effectiveDate: new Date().toISOString().slice(0, 10),
  expiryDate: '',
  status: 'ACTIVE',
  remark: '',
})

const rules = {
  rateCode: [{ required: true, message: '请输入费率编号', trigger: 'blur' }],
  levelCode: [{ required: true, message: '请选择职级', trigger: 'change' }],
  billingUnit: [{ required: true, message: '请选择计费单位', trigger: 'change' }],
  costAmount: [{ required: true, message: '请输入成本金额', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
}

/** 打开新建弹窗：重置表单并生成默认费率编号 */
function openCreate() {
  editingId.value = null
  Object.assign(form, {
    rateCode: `RI-${Date.now()}`,
    levelCode: levels.value[0]?.levelCode || '',
    departmentId: undefined,
    departmentName: '',
    billingUnit: 'DAY',
    costAmount: 0,
    currency: 'CNY',
    effectiveDate: new Date().toISOString().slice(0, 10),
    expiryDate: '',
    status: 'ACTIVE',
    remark: '',
  })
  dialogVisible.value = true
}

/** 打开编辑弹窗：回填当前行数据到表单 */
function openEdit(row: RateInternalVO) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    rateCode: row.rateCode,
    levelCode: row.levelCode,
    departmentId: row.departmentId,
    departmentName: row.departmentName || '',
    billingUnit: row.billingUnit,
    costAmount: row.costAmount,
    currency: row.currency || 'CNY',
    effectiveDate: row.effectiveDate,
    expiryDate: row.expiryDate || '',
    status: row.status || 'ACTIVE',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

/** 提交表单：校验通过后按 editingId 区分新建/更新 */
async function submit() {
  if (!formRef.value) return
  try {
    submitting.value = true
    await formRef.value.validate()
    if (editingId.value) {
      await updateRateInternal(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createRateInternal(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // 校验或保存失败，保持弹窗打开
  } finally {
    submitting.value = false
  }
}

/** 删除内部费率（二次确认） */
async function onDelete(row: RateInternalVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除内部费率 ${row.rateCode}？`, '提示', { type: 'warning' })
    await deleteRateInternal(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 用户取消 */ }
}

onMounted(async () => {
  await Promise.all([fetchLevels(), fetchDepts()])
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
    <template #search>
      <el-form-item label="职级">
        <el-select v-model="query.levelCode" placeholder="全部" clearable filterable style="width: 160px">
          <el-option v-for="l in levels" :key="l.id" :label="`${l.levelCode} - ${l.levelName}`" :value="l.levelCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="部门">
        <el-tree-select
          v-model="query.departmentId"
          :data="depts"
          :props="({ value: 'id', label: 'deptName', children: 'children' } as any)"
          check-strictly
          clearable
          placeholder="全部"
          style="width: 180px"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="生效" value="ACTIVE" />
          <el-option label="停用" value="INACTIVE" />
        </el-select>
      </el-form-item>
    </template>
    <template #toolbar>
      <el-button
        v-if="hasPerm(PC.EXECUTION_RATE_INTERNAL_CREATE)"
        type="primary"
        :icon="'Plus'"
        @click="openCreate"
      >
        新建内部费率
      </el-button>
    </template>
    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border height="auto" :scroll-y="scope.tableProps.scrollY">
        <vxe-column field="rateCode" title="费率编号" width="150" />
        <vxe-column field="levelCode" title="职级" width="100" />
        <vxe-column field="departmentName" title="部门" min-width="140" show-overflow />
        <vxe-column field="billingUnit" title="计费单位" width="100">
          <template #default="{ row }">
            {{ row.billingUnit === 'DAY' ? '元/天' : '元/小时' }}
          </template>
        </vxe-column>
        <vxe-column field="costAmount" title="成本金额" width="130">
          <template #default="{ row }">
            <span class="num">{{ fmtMoney(row.costAmount) }}</span>
          </template>
        </vxe-column>
        <vxe-column field="effectiveDate" title="生效日期" width="120" />
        <vxe-column field="expiryDate" title="失效日期" width="120" />
        <vxe-column field="status" title="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status" :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="remark" title="备注" min-width="120" show-overflow />
        <vxe-column title="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="hasPerm(PC.EXECUTION_RATE_INTERNAL_CREATE)"
              link
              type="primary"
              @click="openEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="hasPerm(PC.EXECUTION_RATE_INTERNAL_CREATE)"
              link
              type="danger"
              @click="onDelete(row)"
            >删除</el-button>
          </template>
        </vxe-column>
        <template #empty><el-empty description="暂无内部职级费率" /></template>
      </vxe-table>
    </template>
  </PageLayout>

  <el-dialog
    v-model="dialogVisible"
    :title="editingId ? '编辑内部费率' : '新建内部费率'"
    width="640px"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="费率编号" prop="rateCode">
        <el-input v-model="form.rateCode" placeholder="例如 RI-L5-DEV-2026Q3" />
      </el-form-item>
      <el-form-item label="职级" prop="levelCode">
        <el-select v-model="form.levelCode" filterable style="width: 100%">
          <el-option v-for="l in levels" :key="l.id" :label="`${l.levelCode} - ${l.levelName}`" :value="l.levelCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="部门">
        <el-tree-select
          v-model="form.departmentId"
          :data="depts"
          :props="({ value: 'id', label: 'deptName', children: 'children' } as any)"
          check-strictly
          clearable
          placeholder="可空，应用于全公司"
          style="width: 100%"
          @change="(v: number) => { const d = depts.find(x => x.id === v); form.departmentName = d?.deptName || '' }"
        />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="计费单位" prop="billingUnit">
            <el-select v-model="form.billingUnit" style="width: 100%">
              <el-option label="元/天" value="DAY" />
              <el-option label="元/小时" value="HOUR" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="币种">
            <el-select v-model="form.currency" style="width: 100%">
              <el-option label="人民币 CNY" value="CNY" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="成本金额" prop="costAmount">
            <el-input-number v-model="form.costAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="生效 ACTIVE" value="ACTIVE" />
              <el-option label="停用 INACTIVE" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="生效日期" prop="effectiveDate">
            <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="失效日期">
            <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.num { font-variant-numeric: tabular-nums; }
</style>
