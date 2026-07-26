<!--
  @file 搜索结果页面
  @description 提供全文搜索功能，展示匹配的文件名、路径、内容片段。
  @module views/nextwiki/search
-->
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { searchFiles, rebuildSearchIndex } from '@/api/nextwiki/search'
import { downloadFile } from '@/api/nextwiki/download'
import type { SearchResultVO } from '@/api/nextwiki/types'
import { PC } from '@/constants/permissionCodes'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()
const route = useRoute()

/** 搜索加载状态 */
const loading = ref(false)
/** 搜索结果列表 */
const list = ref<SearchResultVO[]>([])
/** 搜索结果总数 */
const total = ref(0)
/** 搜索关键字（支持从路由参数初始化） */
const keyword = ref('')

/** 搜索文件 */
async function handleSearch() {
  if (!keyword.value.trim()) return
  loading.value = true
  try {
    const { data } = await searchFiles(keyword.value)
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

/** 下载文件 */
async function handleDownload(row: SearchResultVO) {
  const { data } = await downloadFile(row.fileNodeId)
  const url = window.URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = row.name
  a.click()
  window.URL.revokeObjectURL(url)
}

/** 重建索引 */
async function handleRebuild() {
  try {
    await ElMessageBox.confirm(t('nextwiki.search.rebuildConfirm'), t('common.tip'), { type: 'warning' })
    await rebuildSearchIndex()
    ElMessage.success(t('nextwiki.search.rebuildSuccess'))
  } catch { /* 取消 */ }
}

/** 监听路由参数变化 */
watch(
  () => route.query.keyword,
  (val) => {
    if (typeof val === 'string' && val) {
      keyword.value = val
      handleSearch()
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (route.query.keyword) {
    keyword.value = route.query.keyword as string
    handleSearch()
  }
})
</script>

<template>
  <PageLayout
    :list="list"
    :total="total"
    :loading="loading"
    :hide-pagination="true"
    @refresh="handleSearch"
  >
    <template #search>
      <el-form-item :label="$t('nextwiki.search.title')">
        <el-input
          v-model="keyword"
          :placeholder="$t('nextwiki.search.placeholder')"
          clearable
          style="width: 300px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
    </template>

    <template #toolbar>
      <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
      <el-button v-permission="[PC.NEXTWIKI_SEARCH_REBUILD]" @click="handleRebuild">{{ $t('nextwiki.search.rebuild') }}</el-button>
    </template>

    <template #table>
      <div v-if="list.length === 0 && !loading" class="search-empty">
        <el-empty :description="keyword ? $t('nextwiki.search.noResults') : $t('nextwiki.search.placeholder')" />
      </div>
      <div v-else class="search-results">
        <div class="search-results__count">{{ $t('nextwiki.search.resultCount', { count: total }) }}</div>
        <vxe-table :data="list" :loading="loading" border stripe height="440">
          <vxe-column type="seq" title="#" width="50" />
          <vxe-column field="name" :title="$t('nextwiki.files.name')" min-width="200" show-overflow>
            <template #default="{ row }">
              <el-icon class="file-icon"><Document /></el-icon>
              <span>{{ row.name }}</span>
            </template>
          </vxe-column>
          <vxe-column field="path" :title="$t('nextwiki.trash.originalPath')" min-width="200" show-overflow />
          <vxe-column field="snippet" :title="$t('nextwiki.search.snippet')" min-width="240" show-overflow>
            <template #default="{ row }">
              <span v-if="row.snippet" class="search-snippet" v-html="row.snippet" />
              <span v-else>-</span>
            </template>
          </vxe-column>
          <vxe-column field="size" :title="$t('nextwiki.files.size')" width="100">
            <template #default="{ row }">{{ formatSize(row.size) }}</template>
          </vxe-column>
          <vxe-column :title="$t('common.more')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="[PC.NEXTWIKI_DOWNLOAD]" link type="primary" size="small" @click="handleDownload(row as SearchResultVO)">{{ $t('nextwiki.files.download') }}</el-button>
            </template>
          </vxe-column>
        </vxe-table>
      </div>
    </template>
  </PageLayout>
</template>

<style scoped>
.search-empty {
  padding: 60px 0;
}
.search-results__count {
  margin-bottom: 12px;
  color: #909399;
  font-size: 13px;
}
.file-icon {
  margin-right: 4px;
  color: #409EFF;
}
.search-snippet {
  color: #606266;
  font-size: 13px;
}
</style>
