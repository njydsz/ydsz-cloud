<script setup lang="ts">
/**
 * 项目变更管理
 *
 * 状态: DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED / REJECTED -> CLOSED
 * 影响等级: LOW / MEDIUM / HIGH (ChangeImpactEvaluator 评估)
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { PC } from '@/constants/permissionCodes'

// 项目变更 API 待 ydsz-pmis-project 提供，先用占位 mock
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  changeType: '',
  initiationId: undefined as number | undefined,
})

const statusMap = {
  DRAFT: { label: '草稿', type: 'info' as const },
  SUBMITTED: { label: '已提交', type: 'warning' as const },
  UNDER_REVIEW: { label: '审批中', type: 'warning' as const },
  APPROVED: { label: '已通过', type: 'success' as const },
  REJECTED: { label: '已驳回', type: 'danger' as const },
  CLOSED: { label: '已关闭', type: 'info' as const },
  CANCELLED: { label: '已取消', type: 'info' as const },
}

const typeMap = {
  SCOPE: { label: '范围' },
  COST: { label: '成本' },
  SCHEDULE: { label: '进度' },
  QUALITY: { label: '质量' },
  RESOURCE: { label: '资源' },
  OTHER: { label: '其他' },
}

const impactMap = {
  LOW: { label: '低', type: 'success' as const },
  MEDIUM: { label: '中', type: 'warning' as const },
  HIGH: { label: '高', type: 'danger' as const },
}

async function fetchList() {
  loading.value = true
  // mock data
  await new Promise((r) => setTimeout(r, 200))
  list.value = []
  total.value = 0
  loading.value = false
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.changeType = ''
  query.initiationId = undefined
  query.page = 1
  fetchList()
}

async function handleStatus(row: any, target: string) {
  const targetText = (statusMap as any)[target]?.label || target
  try {
    await ElMessageBox.confirm(`确认将状态变更为「${targetText}」吗？`, '提示', { type: 'warning' })
    ElMessage.success('演示状态机，暂无后端接口')
    fetchList()
  } catch { /* 取消 */ }
}

onMounted(fetchList)
</script>

<template>
  <PageLayout
    v-model:query="query"
    :list="list"
    :total="total"
    :loading="loading"
    @query="query.page = 1; fetchList()"
    @reset="handleReset"
    @page-change="fetchList"
    @refresh="fetchList"
  >
    <template #search>
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="名称" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button v-permission="[PC.PROJECT_CHANGE_CREATE]" type="primary" :icon="'Plus'">新增变更</el-button>
    </template>

    <template #table>
      <el-empty v-if="!loading && list.length === 0" description="暂无项目变更数据" />
      <vxe-table v-else :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
      </vxe-table>
    </template>
  </PageLayout>
</template>
