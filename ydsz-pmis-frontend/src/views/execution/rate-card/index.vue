<!--
  @file 外部价目表
  @description 对外报价费率(Rate Card)管理页面：按(职级×项目类型×客户等级)维护每日/每小时报价，支持三级回退(level→project→customer)匹配，对应路由 /execution/rate-card
  @module views/execution/rate-card
-->
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
import { isHandledError } from '@/utils/error'
import { PC } from '@/constants/permissionCodes'
import { useUserStore } from '@/store/modules/user'

// 权限助手：统一通过 userStore 校验按钮级权限
const userStore = useUserStore()
const hasPerm = (code: string) => userStore.hasPermission(code)

// 列表查询状态
const loading = ref(false)
const list = ref<RateCardVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  levelCode: '',
  status: '',
})
// 职级下拉数据
const levels = ref<JobLevelVO[]>([])

/** 拉取职级下拉数据（用于表单与查询条件） */
async function fetchLevels() {
  try {
    const { data } = await listJobLevels()
    levels.value = data || []
  } catch (e) {
    levels.value = []
    if (!isHandledError(e)) {
      ElMessage.error('职级数据加载失败，请刷新重试')
    }
  }
}

/** 拉取报价费率分页数据 */
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

// 状态字典：生效/停用
const statusMap: Record<string, { label: string; type: 'success' | 'info' }> = {
  ACTIVE: { label: '生效', type: 'success' },
  INACTIVE: { label: '停用', type: 'info' },
}

/** 金额格式化：按币种选择符号(¥/$/€)，空值返回 - */
function fmtMoney(n?: number, cur = 'CNY') {
  if (n === undefined || n === null) return '-'
  const symbol = cur === 'CNY' ? '¥' : cur === 'USD' ? '$' : '€'
  return `${symbol}${Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
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
// 弹窗 - 新建/编辑报价费率
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

/** 打开新建弹窗：重置表单并生成默认费率编号 */
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

/** 打开编辑弹窗：回填当前行数据到表单 */
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

/** 提交表单：校验通过后按 editingId 区分新建/更新 */
async function submit() {
  if (!formRef.value) return
  try {
    submitting.value = true
    await formRef.value.validate()
    if (editingId.value) {
      await updateRateCard(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createRateCard(form)
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

/** 删除报价费率（二次确认） */
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
    <!-- 查询条件区：职级 / 状态 -->
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
    <!-- 工具栏：新建报价按钮（受权限控制） -->
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
    <!-- 数据表格：报价费率明细 + 编辑/删除操作列 -->
    <template #table="scope">
      <vxe-table :data="list" :loading="loading" border height="auto" :scroll-y="scope.tableProps.scrollY">
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

  <!-- 新建/编辑报价费率弹窗：费率编号、职级、项目类型、客户等级、计费单位、币种、金额、生效/失效日期 -->
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
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.num { font-variant-numeric: tabular-nums; }
</style>
