<!--
  @file 我的收藏页面
  @description 展示当前用户收藏的文件列表，支持取消收藏、下载、预览。
  @module views/nextwiki/starred
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listFiles, toggleStar } from '@/api/nextwiki/file'
import { downloadFile } from '@/api/nextwiki/download'
import type { FileNodeVO } from '@/api/nextwiki/types'
import { PC } from '@/constants/permissionCodes'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

/** 收藏列表加载状态 */
const loading = ref(false)
/** 收藏文件列表 */
const list = ref<FileNodeVO[]>([])
/** 收藏列表总数 */
const total = ref(0)
/** 分页查询参数 */
const query = reactive({ page: 1, size: 10 })

/** 查询收藏文件列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await listFiles({ starred: true, page: query.page, size: query.size })
    list.value = data.list || []
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

/** 取消收藏 */
async function handleUnstar(row: FileNodeVO) {
  await toggleStar(row.id, { starred: false })
  ElMessage.success(t('nextwiki.files.unstarSuccess'))
  fetchList()
}

/** 下载文件 */
async function handleDownload(row: FileNodeVO) {
  const { data } = await downloadFile(row.id)
  const url = window.URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = row.name
  a.click()
  window.URL.revokeObjectURL(url)
}

onMounted(fetchList)
</script>

<template>
  <PageLayout
    :query="query"
    :list="list"
    :total="total"
    :loading="loading"
    @page-change="fetchList"
    @refresh="fetchList"
  >
    <template #toolbar>
      <el-button @click="fetchList">{{ $t('common.refresh') }}</el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe height="480">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="name" :title="$t('nextwiki.files.name')" min-width="200" show-overflow>
          <template #default="{ row }">
            <el-icon class="file-icon"><Document /></el-icon>
            <span>{{ row.name }}</span>
          </template>
        </vxe-column>
        <vxe-column field="path" :title="$t('nextwiki.trash.originalPath')" min-width="180" show-overflow />
        <vxe-column field="size" :title="$t('nextwiki.files.size')" width="100">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </vxe-column>
        <vxe-column field="updatedAt" :title="$t('nextwiki.files.modified')" width="160" />
        <vxe-column :title="$t('common.more')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.NEXTWIKI_DOWNLOAD]" link type="primary" size="small" @click="handleDownload(row as FileNodeVO)">{{ $t('nextwiki.files.download') }}</el-button>
            <el-button v-permission="[PC.NEXTWIKI_FILE_STAR]" link type="warning" size="small" @click="handleUnstar(row as FileNodeVO)">{{ $t('nextwiki.files.unstar') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>
  </PageLayout>
</template>

<style scoped>
.file-icon {
  margin-right: 4px;
  color: #409EFF;
}
</style>
