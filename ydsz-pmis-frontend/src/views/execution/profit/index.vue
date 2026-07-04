<!--
  @file 收入确认 + 利润快照
  @description 项目执行过程中的收入确认与利润快照管理页面，采用双 Tab 布局：
               Tab1 收入确认：支持终验法/里程碑/按月三种确认方式，可新增/删除收入记录；
               Tab2 利润快照：按项目 + 期间生成利润快照，自动汇总收入、人工成本、采购成本、费用成本、
               总成本、毛利、毛利率及健康度评分。
  @module views/execution/profit
-->
<script setup lang="ts">
/**
 * 收入确认 + 利润快照
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import {
  pageRevenues,
  createRevenue,
  deleteRevenue,
  pageProfitSnapshots,
  generateProfitSnapshot,
} from '@/api/execution/profit'
import type { RevenueVO, RevenueCreateDTO, ProfitSnapshotVO } from '@/api/execution/profit/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 当前激活的 Tab（revenue 收入确认 / profit 利润快照） */
const tab = ref<'revenue' | 'profit'>('revenue')

// ============= 收入确认 =============
/** 收入列表加载状态 */
const rLoading = ref(false)
/** 收入确认记录列表 */
const rList = ref<RevenueVO[]>([])
/** 收入记录总数（分页用） */
const rTotal = ref(0)
/** 收入查询条件：关键字 + 项目 ID + 确认方法 */
const rQuery = reactive({ page: 1, size: 10, keyword: '', initiationId: undefined as number | undefined, method: '' })

/** 收入确认方法 → 标签映射（终验法/里程碑/按月） */
const methodMap = computed(() => ({
  FINAL: { label: t('execution.profit.method.FINAL') },
  MILESTONE: { label: t('execution.profit.method.MILESTONE') },
  MONTHLY: { label: t('execution.profit.method.MONTHLY') },
}))

/** 分页查询收入确认列表 */
async function fetchRevenue() {
  rLoading.value = true
  try {
    const { data } = await pageRevenues(rQuery.page, rQuery.size, {
      keyword: rQuery.keyword,
      initiationId: rQuery.initiationId,
      method: rQuery.method,
    })
    rList.value = data.list
    rTotal.value = data.total
  } finally {
    rLoading.value = false
  }
}

/** 重置收入查询条件并回到首页刷新 */
function resetRevenue() {
  rQuery.keyword = ''
  rQuery.initiationId = undefined
  rQuery.method = ''
  rQuery.page = 1
  fetchRevenue()
}

/** 提交按钮 loading 状态，防止重复提交 */
const rSubmitting = ref(false)
/** 新增收入弹窗可见性 */
const rDialogVisible = ref(false)
/** 收入表单引用（用于校验） */
const rFormRef = ref<any>()
/** 新增收入表单数据 */
const rForm = reactive<Partial<RevenueCreateDTO>>({
  initiationId: 0,
  recognitionMethod: 'MILESTONE',
  amount: 0,
  period: new Date().toISOString().slice(0, 7),
  recognitionDate: new Date().toISOString().slice(0, 10),
})

/** 收入表单校验规则 */
const rFormRules = computed(() => ({
  initiationId: [{ required: true, message: t('execution.profit.rules.initiationIdRequired'), trigger: 'blur' }],
  recognitionMethod: [{ required: true, message: t('execution.profit.rules.methodRequired'), trigger: 'change' }],
  amount: [{ required: true, message: t('execution.profit.rules.amountRequired'), trigger: 'blur' }],
  period: [{ required: true, message: t('execution.profit.rules.periodRequired'), trigger: 'blur' }],
}))

/** 打开新增收入弹窗并重置表单为默认值 */
function openRCreate() {
  Object.assign(rForm, {
    initiationId: 0,
    contractId: undefined,
    recognitionMethod: 'MILESTONE',
    amount: 0,
    period: new Date().toISOString().slice(0, 7),
    recognitionDate: new Date().toISOString().slice(0, 10),
    description: '',
  })
  rDialogVisible.value = true
}

/** 提交新建收入记录，校验通过后创建并刷新列表 */
async function submitR() {
  try {
    rSubmitting.value = true
    await rFormRef.value?.validate()
    await createRevenue(rForm as RevenueCreateDTO)
    ElMessage.success(t('execution.profit.messages.created'))
    rDialogVisible.value = false
    fetchRevenue()
  } catch {
    // 拦截器已弹错，保持弹窗打开
  } finally {
    rSubmitting.value = false
  }
}

/**
 * 删除指定收入记录，需二次确认
 * @param row 收入记录
 */
async function handleRDelete(row: RevenueVO) {
  try {
    await ElMessageBox.confirm(t('execution.profit.messages.confirmDeleteRevenue'), t('common.tip'), { type: 'warning' })
    await deleteRevenue(row.id)
    ElMessage.success(t('execution.profit.messages.deleted'))
    fetchRevenue()
  } catch { /* 取消 */ }
}

// ============= 利润快照 =============
/** 快照列表加载状态 */
const pLoading = ref(false)
/** 利润快照记录列表 */
const pList = ref<ProfitSnapshotVO[]>([])
/** 快照记录总数（分页用） */
const pTotal = ref(0)
/** 快照查询条件：项目 ID + 期间（YYYY-MM） */
const pQuery = reactive({ page: 1, size: 10, initiationId: undefined as number | undefined, period: '' })

/** 分页查询利润快照列表 */
async function fetchProfit() {
  pLoading.value = true
  try {
    const { data } = await pageProfitSnapshots(pQuery.page, pQuery.size, {
      initiationId: pQuery.initiationId,
      period: pQuery.period,
    })
    pList.value = data.list
    pTotal.value = data.total
  } finally {
    pLoading.value = false
  }
}

/**
 * 按项目 + 期间生成利润快照，期间为空时默认取当月
 * @returns 生成成功后返回快照 ID
 */
async function handleGenerate() {
  if (!pQuery.initiationId) {
    ElMessage.warning(t('execution.profit.messages.fillInitiationId'))
    return
  }
  const period = pQuery.period || new Date().toISOString().slice(0, 7)
  try {
    const { data } = await generateProfitSnapshot(pQuery.initiationId, period)
    ElMessage.success(t('execution.profit.messages.snapshotGenerated', { id: data }))
    fetchProfit()
  } catch (e: any) {
    ElMessage.error(e?.message || t('execution.profit.messages.generateFailed'))
  }
}

/** 页面挂载时并行加载收入列表与利润快照列表 */
onMounted(() => {
  fetchRevenue()
  fetchProfit()
})
</script>

<template>
  <el-card shadow="never">
    <el-tabs v-model="tab">
      <el-tab-pane :label="$t('execution.profit.tabs.revenue')" name="revenue">
        <PageLayout
          v-model:query="rQuery"
          :list="rList"
          :total="rTotal"
          :loading="rLoading"
          @query="rQuery.page = 1; fetchRevenue()"
          @reset="resetRevenue"
          @page-change="fetchRevenue"
          @refresh="fetchRevenue"
        >
          <template #search>
            <el-form-item :label="$t('execution.profit.search.keyword')"><el-input v-model="rQuery.keyword" :placeholder="$t('execution.profit.search.keywordPlaceholder')" clearable /></el-form-item>
            <el-form-item :label="$t('execution.profit.search.initiationId')"><el-input-number v-model="rQuery.initiationId" :min="0" :controls="false" /></el-form-item>
            <el-form-item :label="$t('execution.profit.search.method')">
              <el-select v-model="rQuery.method" :placeholder="$t('common.all')" clearable style="width: 140px">
                <el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="[PC.EXECUTION_REVENUE_CREATE]" type="primary" :icon="'Plus'" @click="openRCreate">{{ $t('execution.profit.buttons.createRevenue') }}</el-button>
          </template>
          <template #table="scope">
            <vxe-table :data="rList" :loading="rLoading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
              <vxe-column type="seq" title="#" width="50" />
              <vxe-column field="initiationName" :title="$t('execution.profit.columns.initiationName')" width="160" show-overflow />
              <vxe-column field="contractCode" :title="$t('execution.profit.columns.contractCode')" width="140" />
              <vxe-column field="recognitionMethod" :title="$t('execution.profit.columns.recognitionMethod')" width="100">
                <template #default="{ row }">{{ methodMap[row.recognitionMethod as keyof typeof methodMap]?.label || row.recognitionMethod || '-' }}</template>
              </vxe-column>
              <vxe-column field="period" :title="$t('execution.profit.columns.period')" width="100" />
              <vxe-column field="amount" :title="$t('execution.profit.columns.amount')" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="recognitionDate" :title="$t('execution.profit.columns.recognitionDate')" width="110" />
              <vxe-column field="description" :title="$t('execution.profit.columns.description')" min-width="200" show-overflow />
              <vxe-column :title="$t('execution.profit.columns.action')" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" size="small" @click="handleRDelete(row)">{{ $t('execution.profit.buttons.delete') }}</el-button>
                </template>
              </vxe-column>
            </vxe-table>
          </template>
        </PageLayout>
      </el-tab-pane>

      <el-tab-pane :label="$t('execution.profit.tabs.profitSnapshot')" name="profit">
        <PageLayout
          v-model:query="pQuery"
          :list="pList"
          :total="pTotal"
          :loading="pLoading"
          hide-search
          @page-change="fetchProfit"
          @refresh="fetchProfit"
        >
          <template #toolbar>
            <el-form inline :model="pQuery" class="profit-form">
              <el-form-item :label="$t('execution.profit.search.initiationId')"><el-input-number v-model="pQuery.initiationId" :min="0" :controls="false" /></el-form-item>
              <el-form-item :label="$t('execution.profit.dialog.period')"><el-input v-model="pQuery.period" :placeholder="$t('execution.profit.dialog.periodPlaceholder')" /></el-form-item>
              <el-form-item>
                <el-button v-permission="[PC.EXECUTION_PROFIT_SNAPSHOT]" type="primary" :icon="'Plus'" @click="handleGenerate">{{ $t('execution.profit.buttons.generateSnapshot') }}</el-button>
                <el-button @click="pQuery.initiationId = undefined; pQuery.period = ''; fetchProfit()">{{ $t('execution.profit.buttons.reset') }}</el-button>
              </el-form-item>
            </el-form>
          </template>
          <template #table="scope">
            <vxe-table :data="pList" :loading="pLoading" border stripe :height="scope.tableProps.height" :scroll-y="scope.tableProps.scrollY">
              <vxe-column type="seq" title="#" width="50" />
              <vxe-column field="initiationName" :title="$t('execution.profit.columns.initiationName')" width="200" show-overflow />
              <vxe-column field="period" :title="$t('execution.profit.columns.period')" width="100" />
              <vxe-column field="revenue" :title="$t('execution.profit.columns.revenue')" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="laborCost" :title="$t('execution.profit.columns.laborCost')" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="purchaseCost" :title="$t('execution.profit.columns.purchaseCost')" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="expenseCost" :title="$t('execution.profit.columns.expenseCost')" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="totalCost" :title="$t('execution.profit.columns.totalCost')" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="grossProfit" :title="$t('execution.profit.columns.grossProfit')" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="grossMargin" :title="$t('execution.profit.columns.grossMargin')" width="100" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(1)}%` : '-'" />
              <vxe-column field="healthScore" :title="$t('execution.profit.columns.healthScore')" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="Number(row.healthScore || 0) >= 80 ? 'success' : Number(row.healthScore || 0) >= 60 ? 'warning' : 'danger'">
                    {{ row.healthScore ?? '-' }}
                  </el-tag>
                </template>
              </vxe-column>
            </vxe-table>
          </template>
        </PageLayout>
      </el-tab-pane>
    </el-tabs>
  </el-card>

  <el-dialog v-model="rDialogVisible" :title="$t('execution.profit.dialog.createRevenueTitle')" width="520px">
    <el-form ref="rFormRef" :model="rForm" :rules="rFormRules" label-width="100px">
      <el-form-item :label="$t('execution.profit.dialog.initiationId')" prop="initiationId"><el-input-number v-model="rForm.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
      <el-form-item :label="$t('execution.profit.dialog.contractId')"><el-input-number v-model="rForm.contractId" :min="0" :controls="false" style="width: 100%" /></el-form-item>
      <el-form-item :label="$t('execution.profit.dialog.recognitionMethod')" prop="recognitionMethod">
        <el-select v-model="rForm.recognitionMethod" style="width: 100%">
          <el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('execution.profit.dialog.amount')" prop="amount"><el-input-number v-model="rForm.amount" :min="0" :controls="false" style="width: 100%" /></el-form-item>
      <el-form-item :label="$t('execution.profit.dialog.period')" prop="period"><el-input v-model="rForm.period" :placeholder="$t('execution.profit.dialog.periodPlaceholder')" /></el-form-item>
      <el-form-item :label="$t('execution.profit.dialog.recognitionDate')">
        <el-date-picker v-model="rForm.recognitionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="$t('execution.profit.dialog.description')"><el-input v-model="rForm.description" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="rDialogVisible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="rSubmitting" @click="submitR">{{ $t('common.ok') }}</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.profit-form { display: flex; align-items: center; }
</style>
