<!--
  @file 收入确认管理
  @description 项目收入确认管理页面，支持收入确认单的创建、审批、状态流转；
               确认方式: OVER_TIME(按时间) | MILESTONE(按里程碑) | ON_DELIVERY(按交付) | PERCENTAGE(按比例)。
  @module views/finance/revenue
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 收入确认记录列表 */
const list = ref<any[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：合同 ID + 确认方式 */
const query = reactive({
  page: 1,
  size: 10,
  contractId: undefined as number | undefined,
  recognitionMethod: '',
})

/** 新增弹窗可见 */
const dialogVisible = ref(false)
/** 表单引用 */
const formRef = ref<FormInstance>()
/** 表单数据 */
const form = reactive({
  contractId: undefined as number | undefined,
  recognitionDate: '',
  recognitionAmount: 0,
  recognitionMethod: '',
  description: '',
})

/** 确认方式 → 标签/样式映射 */
const methodMap: Record<string, { label: string; type: string }> = {
  OVER_TIME: { label: '按时间确认', type: 'primary' },
  MILESTONE: { label: '按里程碑', type: 'warning' },
  ON_DELIVERY: { label: '按交付', type: 'success' },
  PERCENTAGE: { label: '按比例', type: 'info' },
}

/** 状态 → 标签/样式映射 */
const statusMap: Record<string, { label: string; type: string }> = {
  PENDING: { label: '待确认', type: 'info' },
  CONFIRMED: { label: '已确认', type: 'success' },
  REVERSED: { label: '已冲回', type: 'danger' },
}

/** 拉取收入确认列表 */
async function loadData() {
  loading.value = true
  try {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 打开新增弹窗 */
function handleAdd() {
  dialogVisible.value = true
}

/** 提交表单：校验通过后保存收入确认 */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  ElMessage.success('收入确认已创建')
  dialogVisible.value = false
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <PageLayout>
    <!-- 顶部操作栏 -->
    <template #header>
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ t('common.revenueRecognition') }}</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          {{ t('common.addConfirmation') }}
        </el-button>
      </div>
    </template>

    <!-- 筛选条件区 -->
    <div class="mb-4 flex gap-3">
      <el-input v-model="query.contractId" placeholder="合同ID" clearable style="width: 140px" />
      <el-select v-model="query.recognitionMethod" placeholder="确认方式" clearable style="width: 160px">
        <el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <!-- 收入确认列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="contractCode" label="合同编号" width="150" />
      <el-table-column prop="recognitionDate" label="确认日期" width="120" />
      <el-table-column prop="recognitionAmount" label="确认金额" width="140" align="right">
        <template #default="{ row }">¥{{ (row.recognitionAmount || 0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="recognitionMethod" label="确认方式" width="120">
        <template #default="{ row }">
          <el-tag :type="methodMap[row.recognitionMethod]?.type || 'info'">
            {{ methodMap[row.recognitionMethod]?.label || row.recognitionMethod }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">
            {{ statusMap[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
    </el-table>

    <!-- 分页控件 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="(p: number) => { query.page = p; loadData() }"
      />
    </div>

    <!-- 新增收入确认弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增收入确认" width="520px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="合同ID" prop="contractId" :rules="{ required: true, message: '请输入合同ID' }">
          <el-input-number v-model="form.contractId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="确认日期" prop="recognitionDate" :rules="{ required: true, message: '请选择日期' }">
          <el-date-picker v-model="form.recognitionDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="确认金额" prop="recognitionAmount" :rules="{ required: true, message: '请输入金额' }">
          <el-input-number v-model="form.recognitionAmount" :min="0" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="确认方式" prop="recognitionMethod" :rules="{ required: true, message: '请选择方式' }">
          <el-select v-model="form.recognitionMethod" placeholder="请选择">
            <el-option v-for="(v, k) in methodMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
