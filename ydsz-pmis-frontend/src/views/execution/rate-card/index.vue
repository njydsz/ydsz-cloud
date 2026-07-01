<script setup lang="ts">
/**
 * 对外报价费率 (Rate Card) 管理
 *
 * 按 (职级 × 项目类型 × 客户等级) 维护每日/每小时报价。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  pageRateCards,
  createRateCard,
  updateRateCard,
  deleteRateCard,
} from '@/api/execution/rate-card'
import type { RateCardVO, RateCardCreateDTO } from '@/api/execution/rate-card'
import { listJobLevels } from '@/api/resource/job-level'
import type { JobLevelVO } from '@/api/resource/job-level/types'
import { PC } from '@/constants/permissionCodes'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const hasPerm = (code: string) => userStore.hasPermission(code)

const loading = ref(false)
const list = ref<RateCardVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  levelCode: '',
  status: '',
})
const levels = ref<JobLevelVO[]>([])

async function fetchLevels() {
  try {
    const { data } = await listJobLevels()
    levels.value = data || []
  } catch {
    levels.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageRateCards(query.page, query.size, {
      levelCode: query.levelCode || undefined,
      status: query.status || undefined,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const statusMap: Record<string, { label: string; type: 'success' | 'info' }> = {
  ACTIVE: { label: '生效', type: 'success' },
  INACTIVE: { label: '停用', type: 'info' },
}

function fmtMoney(n?: number, cur = 'CNY') {
  if (n === undefined || n === null) return '-'
  const symbol = cur === 'CNY' ? '¥' : cur === 'USD' ? '$' : '€'
  return `${symbol}${Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

function onQuery() {
  query.page = 1
  fetchList()
}
function onReset() {
  query.page = 1
  query.size = 10
  query.levelCode = ''
  query.status = ''
  fetchList()
}
function onPageChange() {
  fetchList()
}
async function onRefresh() {
  await fetchList()
}

// 弹窗
const dialogVisible = ref(false)
const formRef = ref<any>()
const editingId = ref<number | null>(null)
const form = reactive<RateCardCreateDTO>({
  rateCode: '',
  levelCode: '',
  projectType: '',
  customerLevel: '',
  billingUnit: 'DAY',
  rateAmount: 0,
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
  rateAmount: [{ required: true, message: '请输入报价金额', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    rateCode: `RC-${Date.now()}`,
    levelCode: levels.value[0]?.levelCode || '',
    projectType: '',
    customerLevel: '',
    billingUnit: 'DAY',
    rateAmount: 0,
    currency: 'CNY',
    effectiveDate: new Date().toISOString().slice(0, 10),
    expiryDate: '',
    status: 'ACTIVE',
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: RateCardVO) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    rateCode: row.rateCode,
    levelCode: row.levelCode,
    projectType: row.projectType || '',
    customerLevel: row.customerLevel || '',
    billingUnit: row.billingUnit,
    rateAmount: row.rateAmount,
    currency: row.currency || 'CNY',
    effectiveDate: row.effectiveDate,
    expiryDate: row.expiryDate || '',
    status: row.status || 'ACTIVE',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (editingId.value) {
    await updateRateCard(editingId.value, form)
    ElMessage.success('更新成功')
  } else {
    await createRateCard(form)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  fetchList()
}

async function onDelete(row: RateCardVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除费率 ${row.rateCode}？`, '提示', { type: 'warning' })
    await deleteRateCard(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 用户取消 */ }
}

onMounted(async () => {
  await fetchLevels()
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
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="生效" value="ACTIVE" />
          <el-option label="停用" value="INACTIVE" />
        </el-select>
      </el-form-item>
    </template>
    <template #toolbar>
      <el-button
        v-if="hasPerm(PC.EXECUTION_RATE_CARD_CREATE)"
        type="primary"
        :icon="'Plus'"
        @click="openCreate"
      >
        新建报价
      </el-button>
    </template>
    <template #table>
      <vxe-table :data="list" :loading="loading" border height="auto">
        <vxe-column field="rateCode" title="费率编号" width="150" />
        <vxe-column field="levelCode" title="职级" width="100" />
        <vxe-column field="projectType" title="项目类型" width="100" />
        <vxe-column field="customerLevel" title="客户等级" width="90" />
        <vxe-column field="billingUnit" title="计费单位" width="100">
          <template #default="{ row }">
            {{ row.billingUnit === 'DAY' ? '元/天' : '元/小时' }}
          </template>
        </vxe-column>
        <vxe-column field="rateAmount" title="报价金额" width="130">
          <template #default="{ row }">
            <span class="num">{{ fmtMoney(row.rateAmount, row.currency) }}</span>
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
              v-if="hasPerm(PC.EXECUTION_RATE_CARD_CREATE)"
              link
              type="primary"
              @click="openEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="hasPerm(PC.EXECUTION_RATE_CARD_CREATE)"
              link
              type="danger"
              @click="onDelete(row)"
            >删除</el-button>
          </template>
        </vxe-column>
        <template #empty><el-empty description="暂无对外报价费率" /></template>
      </vxe-table>
    </template>
  </PageLayout>

  <el-dialog
    v-model="dialogVisible"
    :title="editingId ? '编辑报价费率' : '新建报价费率'"
    width="640px"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="费率编号" prop="rateCode">
        <el-input v-model="form.rateCode" placeholder="例如 RC-L5-FIX-2026Q3" />
      </el-form-item>
      <el-form-item label="职级" prop="levelCode">
        <el-select v-model="form.levelCode" filterable style="width: 100%">
          <el-option v-for="l in levels" :key="l.id" :label="`${l.levelCode} - ${l.levelName}`" :value="l.levelCode" />
        </el-select>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目类型">
            <el-input v-model="form.projectType" placeholder="可空，对应 ProjectType.code" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户等级">
            <el-select v-model="form.customerLevel" clearable placeholder="可空 A/B/C/D" style="width: 100%">
              <el-option label="A级 - 战略" value="A" />
              <el-option label="B级 - 重点" value="B" />
              <el-option label="C级 - 普通" value="C" />
              <el-option label="D级 - 试单" value="D" />
            </el-select>
          </el-form-item>
        </el-col>
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
              <el-option label="美元 USD" value="USD" />
              <el-option label="欧元 EUR" value="EUR" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="报价金额" prop="rateAmount">
            <el-input-number v-model="form.rateAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
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
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.num { font-variant-numeric: tabular-nums; }
</style>
