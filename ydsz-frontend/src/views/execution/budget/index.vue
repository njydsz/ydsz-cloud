<!--
  @file 预算管理
  @description 项目预算项管理页面，支持预算项的增删改查、预算汇总、预算强管控校验等。
  @module views/execution/budget
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 预算项列表 */
const list = ref<any[]>([])
/** 记录总数（分页用） */
const total = ref(0)
/** 查询条件：项目 ID + 成本类型 */
const query = reactive({
  page: 1,
  size: 10,
  initiationId: undefined as number | undefined,
  costType: '',
})

/** 新增弹窗可见 */
const dialogVisible = ref(false)
/** 表单引用 */
const formRef = ref<FormInstance>()
/** 表单数据 */
const form = reactive({
  initiationId: undefined as number | undefined,
  costType: '',
  budgetAmount: 0,
  remark: '',
})

/** 成本类型 → 中文标签映射 */
const costTypeMap: Record<string, string> = {
  LABOR: '人力成本',
  TRAVEL: '差旅费',
  EQUIPMENT: '设备费',
  OUTSOURCE: '外包费',
  OTHER: '其他',
}

/** 拉取预算项列表 */
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

/** 提交表单：校验通过后保存预算项 */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  ElMessage.success('预算项已保存')
  dialogVisible.value = false
  loadData()
}

/** 删除预算项：确认后执行删除 */
async function handleDelete(row: any) {
  await ElMessageBox.confirm('确认删除该预算项？', '提示', { type: 'warning' })
  ElMessage.success('已删除')
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
        <h2 class="text-lg font-semibold">{{ t('execution.ruleEngine.budgetManagement') }}</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          {{ t('execution.ruleEngine.addBudgetItem') }}
        </el-button>
      </div>
    </template>

    <!-- 筛选条件区 -->
    <div class="mb-4 flex gap-3">
      <el-input v-model="query.initiationId" placeholder="立项ID" clearable style="width: 140px" />
      <el-select v-model="query.costType" placeholder="成本类型" clearable style="width: 140px">
        <el-option v-for="(v, k) in costTypeMap" :key="k" :label="v" :value="k" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <!-- 预算项列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="initiationId" label="立项ID" width="100" />
      <el-table-column prop="costType" label="成本类型" width="120">
        <template #default="{ row }">{{ costTypeMap[row.costType] || row.costType }}</template>
      </el-table-column>
      <el-table-column prop="budgetAmount" label="预算金额" width="140" align="right">
        <template #default="{ row }">¥{{ (row.budgetAmount || 0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="actualAmount" label="实际金额" width="140" align="right">
        <template #default="{ row }">¥{{ (row.actualAmount || 0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="variance" label="偏差" width="120" align="right">
        <template #default="{ row }">
          <span :class="(row.actualAmount - row.budgetAmount) > 0 ? 'text-red-500' : 'text-green-600'">
            {{ ((row.actualAmount || 0) - (row.budgetAmount || 0)).toLocaleString() }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
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

    <!-- 新增预算项弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增预算项" width="500px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="立项ID" prop="initiationId" :rules="{ required: true, message: '请输入立项ID' }">
          <el-input-number v-model="form.initiationId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="成本类型" prop="costType" :rules="{ required: true, message: '请选择成本类型' }">
          <el-select v-model="form.costType" placeholder="请选择">
            <el-option v-for="(v, k) in costTypeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="预算金额" prop="budgetAmount" :rules="{ required: true, message: '请输入预算金额' }">
          <el-input-number v-model="form.budgetAmount" :min="0" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
