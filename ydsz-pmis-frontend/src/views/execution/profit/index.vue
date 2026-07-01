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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

/** 收入确认方法 → 中文标签映射（终验法/里程碑/按月） */
const methodMap = {
  FINAL: { label: '终验法' },
  MILESTONE: { label: '里程碑' },
  MONTHLY: { label: '按月' },
}

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
const rFormRules = {
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  recognitionMethod: [{ required: true, message: '确认方法必填', trigger: 'change' }],
  amount: [{ required: true, message: '金额必填', trigger: 'blur' }],
  period: [{ required: true, message: '期间必填', trigger: 'blur' }],
}

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
  await rFormRef.value?.validate()
  await createRevenue(rForm as RevenueCreateDTO)
  ElMessage.success('已创建')
  rDialogVisible.value = false
  fetchRevenue()
}

/**
 * 删除指定收入记录，需二次确认
 * @param row 收入记录
 */
async function handleRDelete(row: RevenueVO) {
  try {
    await ElMessageBox.confirm(`确认删除该收入记录？`, '提示', { type: 'warning' })
    await deleteRevenue(row.id)
    ElMessage.success('已删除')
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
    ElMessage.warning('请填写项目 ID')
    return
  }
  const period = pQuery.period || new Date().toISOString().slice(0, 7)
  try {
    const { data } = await generateProfitSnapshot(pQuery.initiationId, period)
    ElMessage.success(`快照已生成 (ID: ${data})`)
    fetchProfit()
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
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
      <el-tab-pane label="收入确认" name="revenue">
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
            <el-form-item label="关键字"><el-input v-model="rQuery.keyword" placeholder="项目/合同" clearable /></el-form-item>
            <el-form-item label="项目 ID"><el-input-number v-model="rQuery.initiationId" :min="0" :controls="false" /></el-form-item>
            <el-form-item label="确认方法">
              <el-select v-model="rQuery.method" placeholder="全部" clearable style="width: 140px">
                <el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </template>
          <template #toolbar>
            <el-button v-permission="[PC.EXECUTION_REVENUE_CREATE]" type="primary" :icon="'Plus'" @click="openRCreate">新增收入</el-button>
          </template>
          <template #table>
            <vxe-table :data="rList" :loading="rLoading" border stripe>
              <vxe-column type="seq" title="#" width="50" />
              <vxe-column field="initiationName" title="项目" width="160" show-overflow />
              <vxe-column field="contractCode" title="合同" width="140" />
              <vxe-column field="recognitionMethod" title="方法" width="100">
                <template #default="{ row }">{{ methodMap[row.recognitionMethod as keyof typeof methodMap]?.label || row.recognitionMethod || '-' }}</template>
              </vxe-column>
              <vxe-column field="period" title="期间" width="100" />
              <vxe-column field="amount" title="金额" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="recognitionDate" title="确认日期" width="110" />
              <vxe-column field="description" title="说明" min-width="200" show-overflow />
              <vxe-column title="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" size="small" @click="handleRDelete(row)">删除</el-button>
                </template>
              </vxe-column>
            </vxe-table>
          </template>
        </PageLayout>
      </el-tab-pane>

      <el-tab-pane label="利润快照" name="profit">
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
              <el-form-item label="项目 ID"><el-input-number v-model="pQuery.initiationId" :min="0" :controls="false" /></el-form-item>
              <el-form-item label="期间 (YYYY-MM)"><el-input v-model="pQuery.period" placeholder="如 2026-07" /></el-form-item>
              <el-form-item>
                <el-button v-permission="[PC.EXECUTION_PROFIT_SNAPSHOT]" type="primary" :icon="'Plus'" @click="handleGenerate">生成快照</el-button>
                <el-button @click="pQuery.initiationId = undefined; pQuery.period = ''; fetchProfit()">重置</el-button>
              </el-form-item>
            </el-form>
          </template>
          <template #table>
            <vxe-table :data="pList" :loading="pLoading" border stripe>
              <vxe-column type="seq" title="#" width="50" />
              <vxe-column field="initiationName" title="项目" width="200" show-overflow />
              <vxe-column field="period" title="期间" width="100" />
              <vxe-column field="revenue" title="收入" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="laborCost" title="人工成本" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="purchaseCost" title="采购成本" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="expenseCost" title="费用成本" width="120" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="totalCost" title="总成本" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="grossProfit" title="毛利" width="130" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `¥${Number(cellValue).toLocaleString()}` : '-'" />
              <vxe-column field="grossMargin" title="毛利率" width="100" align="right" :formatter="({ cellValue }: any) => cellValue != null ? `${(Number(cellValue) * 100).toFixed(1)}%` : '-'" />
              <vxe-column field="healthScore" title="健康度" width="100" align="center">
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

  <el-dialog v-model="rDialogVisible" title="新增收入确认" width="520px">
    <el-form ref="rFormRef" :model="rForm" :rules="rFormRules" label-width="100px">
      <el-form-item label="项目 ID" prop="initiationId"><el-input-number v-model="rForm.initiationId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
      <el-form-item label="合同 ID"><el-input-number v-model="rForm.contractId" :min="0" :controls="false" style="width: 100%" /></el-form-item>
      <el-form-item label="确认方法" prop="recognitionMethod">
        <el-select v-model="rForm.recognitionMethod" style="width: 100%">
          <el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="金额" prop="amount"><el-input-number v-model="rForm.amount" :min="0" :controls="false" style="width: 100%" /></el-form-item>
      <el-form-item label="期间 (YYYY-MM)" prop="period"><el-input v-model="rForm.period" placeholder="如 2026-07" /></el-form-item>
      <el-form-item label="确认日期">
        <el-date-picker v-model="rForm.recognitionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="说明"><el-input v-model="rForm.description" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="rDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submitR">确定</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.profit-form { display: flex; align-items: center; }
</style>
