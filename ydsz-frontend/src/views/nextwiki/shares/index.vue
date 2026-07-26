<!--
  @file 我的分享页面
  @description 展示当前用户创建的分享链接列表，支持撤销分享、复制链接。
  @module views/nextwiki/shares
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listShares, revokeShare } from '@/api/nextwiki/share'
import type { ShareLinkVO } from '@/api/nextwiki/types'
import { PC } from '@/constants/permissionCodes'
import PageLayout from '@/components/common/PageLayout.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const { t } = useI18n()

/** 分享列表加载状态 */
const loading = ref(false)
/** 分享链接列表 */
const list = ref<ShareLinkVO[]>([])
/** 分享列表总数 */
const total = ref(0)
/** 分页查询参数 */
const query = reactive({ page: 1, size: 10 })

/** 分享状态映射 */
const statusMap = {
  active: { label: t('nextwiki.shares.active'), type: 'success' as const },
  expired: { label: t('nextwiki.shares.expired'), type: 'info' as const },
  revoked: { label: t('nextwiki.shares.revoked'), type: 'danger' as const },
}

/** 分享类型映射 */
const shareTypeMap = {
  view: { label: t('nextwiki.shares.view') },
  download: { label: t('nextwiki.shares.download') },
  edit: { label: t('nextwiki.shares.edit') },
}

/** 查询分享列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await listShares(query.page, query.size)
    list.value = data.list || []
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 撤销分享 */
async function handleRevoke(row: ShareLinkVO) {
  try {
    await ElMessageBox.confirm(t('nextwiki.shares.confirmRevoke'), t('common.tip'), { type: 'warning' })
    await revokeShare(row.id)
    ElMessage.success(t('nextwiki.shares.revokeSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 复制分享链接 */
async function copyLink(row: ShareLinkVO) {
  const link = `${window.location.origin}/s/${row.shareCode}`
  try {
    await navigator.clipboard.writeText(link)
    ElMessage.success(t('nextwiki.shares.linkCopied'))
  } catch {
    ElMessage.warning(t('nextwiki.shares.copyLink'))
  }
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
        <vxe-column field="shareCode" :title="$t('nextwiki.shares.extractCode')" width="160" />
        <vxe-column field="shareType" :title="$t('nextwiki.shares.shareType')" width="100">
          <template #default="{ row }">{{ shareTypeMap[row.shareType as keyof typeof shareTypeMap]?.label || row.shareType }}</template>
        </vxe-column>
        <vxe-column field="expireTime" :title="$t('nextwiki.shares.expireTime')" width="160">
          <template #default="{ row }">{{ row.expireTime || $t('nextwiki.shares.noExpiry') }}</template>
        </vxe-column>
        <vxe-column field="accessCount" :title="$t('nextwiki.shares.accessCount')" width="100" align="center">
          <template #default="{ row }">
            {{ row.accessCount }}<span v-if="row.maxAccessCount"> / {{ row.maxAccessCount }}</span>
            <span v-else> {{ $t('nextwiki.shares.times') }}</span>
          </template>
        </vxe-column>
        <vxe-column field="hasPassword" :title="$t('nextwiki.shares.password')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hasPassword ? 'warning' : 'info'" size="small">{{ row.hasPassword ? $t('common.yes') : $t('common.no') }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="status" :title="$t('nextwiki.shares.status')" width="80">
          <template #default="{ row }"><StatusTag :value="row.status" :map="statusMap" /></template>
        </vxe-column>
        <vxe-column field="createdAt" :title="$t('nextwiki.files.modified')" width="160" />
        <vxe-column :title="$t('common.more')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="copyLink(row as ShareLinkVO)">{{ $t('nextwiki.shares.copyLink') }}</el-button>
            <el-button v-if="row.status === 'active'" v-permission="[PC.NEXTWIKI_SHARE_REVOKE]" link type="danger" size="small" @click="handleRevoke(row as ShareLinkVO)">{{ $t('nextwiki.shares.revoke') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </template>
  </PageLayout>
</template>
