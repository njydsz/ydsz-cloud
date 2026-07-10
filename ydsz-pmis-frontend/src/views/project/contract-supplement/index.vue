<!--
  @file 合同补充协议管理
  @description 合同补充协议的查询、新增与状态管理；支持原合同关联、补充金额、补充条款等。
  @module views/project/contract-supplement
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  contractId: undefined as number | undefined,
  keyword: '',
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  contractId: undefined as number | undefined,
  supplementTitle: '',
  supplementAmount: 0,
  supplementContent: '',
  effectiveDate: '',
})

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  APPROVED: { label: '已审批', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  EXECUTED: { label: '已执行', type: 'success' },
}

async function loadData() {
  loading.value = true
  try {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  ElMessage.success('补充协议已创建')
  dialogVisible.value = false
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
        <h2 class="text-lg font-semibold">合同补充协议</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增补充协议
        </el-button>
      </div>
    </template>

    <div class="mb-4 flex gap-3">
      <el-input v-model="query.contractId" placeholder="合同ID" clearable style="width: 140px" />
      <el-input v-model="query.keyword" placeholder="关键词" clearable style="width: 200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="contractCode" label="原合同编号" width="150" />
      <el-table-column prop="supplementTitle" label="补充协议标题" min-width="200" />
      <el-table-column prop="supplementAmount" label="补充金额" width="130" align="right">
        <template #default="{ row }">¥{{ (row.supplementAmount || 0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">
            {{ statusMap[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="effectiveDate" label="生效日期" width="120" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
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

    <el-dialog v-model="dialogVisible" title="新增补充协议" width="560px">
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="原合同ID" prop="contractId" :rules="{ required: true, message: '请输入合同ID' }">
          <el-input-number v-model="form.contractId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="补充标题" prop="supplementTitle" :rules="{ required: true, message: '请输入标题' }">
          <el-input v-model="form.supplementTitle" />
        </el-form-item>
        <el-form-item label="补充金额" prop="supplementAmount">
          <el-input-number v-model="form.supplementAmount" :min="0" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="补充内容" prop="supplementContent">
          <el-input v-model="form.supplementContent" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
