<!--
  @file 回收站页面
  @description 展示已删除的文件列表，支持恢复、彻底删除、清空回收站。
  @module views/nextwiki/trash
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listTrash, restoreFromTrash, purgeFromTrash, emptyTrash } from '@/api/nextwiki/trash'
import type { TrashItemVO } from '@/api/nextwiki/types'
import { PC } from '@/constants/permissionCodes'
import PageLayout from '@/components/common/PageLayout.vue'

const { t } = useI18n()

/** 回收站列表加载状态 */
const loading = ref(false)
/** 回收站文件列表 */
const list = ref<TrashItemVO[]>([])
/** 回收站列表总数 */
const total = ref(0)
/** 分页查询参数 */
const query = reactive({ page: 1, size: 10 })

/** 查询回收站列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await listTrash(query.page, query.size)
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

/** 恢复文件 */
async function handleRestore(row: TrashItemVO) {
  await restoreFromTrash(row.id)
  ElMessage.success(t('nextwiki.trash.restoreSuccess'))
  fetchList()
}

/** 彻底删除 */
async function handlePurge(row: TrashItemVO) {
  try {
    await ElMessageBox.confirm(t('nextwiki.trash.confirmPurge'), t('common.tip'), { type: 'warning' })
    await purgeFromTrash(row.id)
    ElMessage.success(t('nextwiki.trash.purgeSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 清空回收站 */
async function handleEmpty() {
  try {
    await ElMessageBox.confirm(t('nextwiki.trash.confirmEmpty'), t('common.tip'), { type: 'warning' })
    await emptyTrash()
    ElMessage.success(t('nextwiki.trash.emptySuccess'))
    fetchList()
  } catch { /* 取消 */ }
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
      <el-button v-permission="[PC.NEXTWIKI_TRASH_EMPTY]" type="danger" :icon="'Delete'" @click="handleEmpty">
        {{ $t('nextwiki.trash.empty') }}
      </el-button>
      <el-button @click="fetchList">{{ $t('common.refresh') }}</el-button>
    </template>

    <template #table>
      <vxe-table :data="list" :loading="loading" border stripe height="480">
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="originalName" :title="$t('nextwiki.files.name')" min-width="200" show-overflow>
          <template #default="{ row }">
            <el-icon class="file-icon" :class="{ 'file-icon--folder': row.nodeType === 'folder' }">
              <Folder v-if="row.nodeType === 'folder'" />
              <Document v-else />
            </el-icon>
            <span>{{ row.originalName }}</span>
          </template>
        </vxe-column>
        <vxe-column field="originalPath" :title="$t('nextwiki.trash.originalPath')" min-width="180" show-overflow />
        <vxe-column field="size" :title="$t('nextwiki.files.size')" width="100">
          <template #default="{ row }">{{ row.nodeType === 'folder' ? '-' : formatSize(row.size) }}</template>
        </vxe-column>
        <vxe-column field="deletedTime" :title="$t('nextwiki.trash.deletedTime')" width="160" />
        <vxe-column field="purgeTime" :title="$t('nextwiki.trash.purgeTime')" width="160" />
        <vxe-column :title="$t('common.more')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.NEXTWIKI_TRASH_RESTORE]" link type="success" size="small" @click="handleRestore(row as TrashItemVO)">{{ $t('nextwiki.trash.restore') }}</el-button>
            <el-button v-permission="[PC.NEXTWIKI_TRASH_PURGE]" link type="danger" size="small" @click="handlePurge(row as TrashItemVO)">{{ $t('nextwiki.trash.purge') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>
  </PageLayout>
</template>

<style scoped>
.file-icon {
  margin-right: 4px;
}
.file-icon--folder {
  color: #E6A23C;
}
</style>
