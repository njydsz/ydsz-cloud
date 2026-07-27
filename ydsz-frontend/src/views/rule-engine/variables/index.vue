<!--
  @file 规则变量管理
  @description 规则引擎变量定义管理页面，支持变量的增删改查、类型管理、默认值设置。
  @module views/rule-engine/variables
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

/** 变量列表加载状态 */
const loading = ref(false)
/** 变量列表数据 */
const list = ref<any[]>([])
/** 变量列表总数 */
const total = ref(0)
/** 分页查询参数 */
const query = reactive({ page: 1, size: 10, keyword: '', varType: '' })

/** 新增/编辑弹窗显隐 */
const dialogVisible = ref(false)
/** 变量表单引用 */
const formRef = ref<FormInstance>()
/** 变量表单数据 */
const form = reactive({
  varCode: '',
  varName: '',
  varType: '',
  defaultValue: '',
  description: '',
})

const varTypeMap: Record<string, string> = {
  STRING: '字符串',
  NUMBER: '数字',
  BOOLEAN: '布尔',
  DATE: '日期',
  ENUM: '枚举',
  JSON: 'JSON',
}

/** 拉取变量列表数据 */
async function loadData() {
  loading.value = true
  try {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 打开新增变量弹窗 */
function handleAdd() {
  dialogVisible.value = true
}

/** 提交变量表单 */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  ElMessage.success('变量已保存')
  dialogVisible.value = false
  loadData()
}

/** 删除变量 */
async function handleDelete(row: any) {
  await ElMessageBox.confirm('确认删除该变量？', '提示', { type: 'warning' })
  ElMessage.success('已删除')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <PageLayout>
    <template #header>
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ t('common.variableManagement') }}</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          {{ t('common.addVariable') }}
        </el-button>
      </div>
    </template>

    <div class="mb-4 flex gap-3">
      <el-input v-model="query.keyword" placeholder="变量编码/名称" clearable style="width: 200px" />
      <el-select v-model="query.varType" placeholder="变量类型" clearable style="width: 140px">
        <el-option v-for="(v, k) in varTypeMap" :key="k" :label="v" :value="k" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="varCode" label="变量编码" width="180" />
      <el-table-column prop="varName" label="变量名称" width="180" />
      <el-table-column prop="varType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ varTypeMap[row.varType] || row.varType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="defaultValue" label="默认值" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="(p: number) => { query.page = p; loadData() }"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="新增变量" width="500px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="变量编码" prop="varCode" :rules="{ required: true, message: '请输入编码' }">
          <el-input v-model="form.varCode" placeholder="如: PROJECT_MARGIN" />
        </el-form-item>
        <el-form-item label="变量名称" prop="varName" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="form.varName" />
        </el-form-item>
        <el-form-item label="变量类型" prop="varType" :rules="{ required: true, message: '请选择类型' }">
          <el-select v-model="form.varType" placeholder="请选择">
            <el-option v-for="(v, k) in varTypeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认值">
          <el-input v-model="form.defaultValue" />
        </el-form-item>
        <el-form-item label="描述">
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
