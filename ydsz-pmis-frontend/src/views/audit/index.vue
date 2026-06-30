<script setup lang="ts">
/**
 * 审计日志
 *
 * 通过消息中心获取操作日志（消息模板已支持分页）
 */
import { ref, reactive, onMounted } from 'vue'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { PC } from '@/constants/permissionCodes'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  module: '',
  action: '',
  userId: undefined as number | undefined,
  startDate: '',
  endDate: '',
})

const levelMap = {
  INFO: { label: '信息', type: 'info' as const },
  WARN: { label: '警告', type: 'warning' as const },
  ERROR: { label: '错误', type: 'danger' as const },
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
  query.module = ''
  query.action = ''
  query.userId = undefined
  query.startDate = ''
  query.endDate = ''
  query.page = 1
  fetchList()
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
      <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="操作内容" clearable /></el-form-item>
      <el-form-item label="模块"><el-input v-model="query.module" placeholder="如 project" clearable /></el-form-item>
      <el-form-item label="操作"><el-input v-model="query.action" placeholder="如 create" clearable /></el-form-item>
      <el-form-item label="用户 ID"><el-input-number v-model="query.userId" :min="0" :controls="false" /></el-form-item>
      <el-form-item label="日期">
        <el-date-picker
          v-model="query.startDate"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          unlink-panels
        />
      </el-form-item>
    </template>

    <template #table>
      <el-empty v-if="!loading && list.length === 0" description="暂无审计日志" />
      <vxe-table v-else :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
      </vxe-table>
    </template>
  </PageLayout>
</template>
