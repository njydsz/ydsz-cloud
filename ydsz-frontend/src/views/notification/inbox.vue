<!--
  @fileoverview 通知中心收件箱页面
  @description 全量通知收件箱：分页/筛选/标记已读/删除/撤回
  - 支持按分类(SYSTEM/WORKFLOW/ALERT/TODO/ANNOUNCE)筛选
  - 支持按级别(INFO/WARN/ERROR/URGENT)筛选
  - 支持按已读状态(全部/未读/已读)筛选
  - 支持单条/批量删除、单条标记已读、全部标记已读
  - 支持撤回(仅自己发送的通知)
  - WebSocket 实时刷新（降级 60s 轮询）
  @module views/notification/inbox
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getInbox,
  getUnreadCount,
  markRead,
  markAllRead,
  deleteNotifications,
  recallNotification,
} from '@/api/notification'
import { useWebSocket } from '@/composables/useWebSocket'
import type { NotificationVO, NotificationPageQuery } from '@/api/notification/types'

const { t } = useI18n()

/** 查询参数 */
const query = reactive<NotificationPageQuery>({
  page: 1,
  size: 20,
  category: undefined,
  level: undefined,
  readStatus: undefined,
})

/** 列表数据 */
const list = ref<NotificationVO[]>([])
/** 总数 */
const total = ref(0)
/** 加载中 */
const loading = ref(false)
/** 选中行 */
const selectedRows = ref<NotificationVO[]>([])
/** 未读数 */
const unreadCount = ref(0)
/** 轮询定时器 */
let pollTimer: ReturnType<typeof setInterval> | null = null

const { on } = useWebSocket()

/** 分类选项 */
const categoryOptions = [
  { label: 'SYSTEM', value: 'SYSTEM' },
  { label: 'WORKFLOW', value: 'WORKFLOW' },
  { label: 'ALERT', value: 'ALERT' },
  { label: 'TODO', value: 'TODO' },
  { label: 'ANNOUNCE', value: 'ANNOUNCE' },
]

/** 级别选项 */
const levelOptions = [
  { label: 'INFO', value: 'INFO' },
  { label: 'WARN', value: 'WARN' },
  { label: 'ERROR', value: 'ERROR' },
  { label: 'URGENT', value: 'URGENT' },
]

/** Element Plus el-tag type 联合类型 */
type TagType = 'info' | 'warning' | 'primary' | 'success' | 'danger'

/** 级别 Tag 类型映射 */
const levelTagType: Record<string, TagType> = {
  INFO: 'info',
  WARN: 'warning',
  ERROR: 'danger',
  URGENT: 'danger',
}

/** 分类 Tag 类型映射（WORKFLOW 用 primary） */
const categoryTagType: Record<string, TagType> = {
  SYSTEM: 'info',
  WORKFLOW: 'primary',
  ALERT: 'warning',
  TODO: 'success',
  ANNOUNCE: 'info',
}

/** 是否有选中行 */
const hasSelection = computed(() => selectedRows.value.length > 0)

/** 拉取列表 */
const fetchList = async () => {
  loading.value = true
  try {
    const resp = await getInbox(query)
    list.value = resp.data?.records ?? []
    total.value = resp.data?.total ?? 0
  } catch {
    // 静默失败
  } finally {
    loading.value = false
  }
}

/** 拉取未读数 */
const fetchUnreadCount = async () => {
  try {
    const resp = await getUnreadCount()
    unreadCount.value = resp.data ?? 0
  } catch {
    // 静默失败
  }
}

/** 搜索 */
const handleSearch = () => {
  query.page = 1
  fetchList()
}

/** 重置 */
const handleReset = () => {
  query.category = undefined
  query.level = undefined
  query.readStatus = undefined
  query.page = 1
  fetchList()
}

/** 翻页 */
const handlePageChange = (page: number) => {
  query.page = page
  fetchList()
}

/** 每页条数变化 */
const handleSizeChange = (size: number) => {
  query.size = size
  query.page = 1
  fetchList()
}

/** 行选择变化 */
const handleSelectionChange = (rows: NotificationVO[]) => {
  selectedRows.value = rows
}

/** 单条标记已读 */
const handleMarkRead = async (row: NotificationVO) => {
  if (row.readStatus === 1) return
  try {
    await markRead(row.id)
    ElMessage.success(t('notification.markReadSuccess'))
    await fetchUnreadCount()
    await fetchList()
  } catch {
    // 静默失败
  }
}

/** 全部标记已读 */
const handleMarkAllRead = async () => {
  try {
    await markAllRead()
    ElMessage.success(t('notification.markAllReadSuccess'))
    await fetchUnreadCount()
    await fetchList()
  } catch {
    // 静默失败
  }
}

/** 批量删除 */
const handleBatchDelete = async () => {
  if (!hasSelection.value) return
  try {
    await ElMessageBox.confirm(t('notification.deleteConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    const ids = selectedRows.value.map((r) => r.id)
    await deleteNotifications(ids)
    ElMessage.success(t('notification.deleteSuccess'))
    await fetchUnreadCount()
    await fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 单条删除 */
const handleDelete = async (row: NotificationVO) => {
  try {
    await ElMessageBox.confirm(t('notification.deleteConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await deleteNotifications([row.id])
    ElMessage.success(t('notification.deleteSuccess'))
    await fetchUnreadCount()
    await fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 撤回 */
const handleRecall = async (row: NotificationVO) => {
  try {
    await ElMessageBox.confirm(t('notification.recallConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await recallNotification(row.id)
    ElMessage.success(t('notification.recallSuccess'))
    await fetchList()
  } catch {
    // 用户取消或请求失败
  }
}

/** 点击通知行(未读则标记已读) */
const handleRowClick = (row: NotificationVO) => {
  if (row.readStatus === 0) {
    handleMarkRead(row)
  }
}

/** 跳转业务链接 */
const handleAction = (row: NotificationVO) => {
  if (row.actionUrl) {
    window.open(row.actionUrl, '_blank')
  }
}

onMounted(() => {
  fetchList()
  fetchUnreadCount()
  // WebSocket 推送到达时刷新
  on('NOTIFICATION', () => {
    fetchUnreadCount()
    fetchList()
  })
  // 轮询兜底
  pollTimer = setInterval(() => {
    fetchUnreadCount()
  }, 60000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div class="notification-inbox">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item :label="t('notification.category')">
          <el-select
            v-model="query.category"
            :placeholder="t('notification.all')"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="opt in categoryOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('notification.level')">
          <el-select
            v-model="query.level"
            :placeholder="t('notification.all')"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in levelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('notification.readStatus')">
          <el-select
            v-model="query.readStatus"
            :placeholder="t('notification.all')"
            clearable
            style="width: 120px"
          >
            <el-option :label="t('notification.unread')" :value="0" />
            <el-option :label="t('notification.read')" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
          <el-button :disabled="unreadCount === 0" @click="handleMarkAllRead">
            {{ t('notification.markAllRead') }}
          </el-button>
        </el-badge>
        <el-button
          type="danger"
          plain
          :disabled="!hasSelection"
          @click="handleBatchDelete"
        >
          {{ t('notification.delete') }}
        </el-button>
      </div>
      <span class="total-text">{{ t('notification.total', { n: total }) }}</span>
    </div>

    <!-- 列表 -->
    <el-table
      v-loading="loading"
      :data="list"
      style="width: 100%"
      @selection-change="handleSelectionChange"
      @row-click="handleRowClick"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column :label="t('notification.level')" width="90">
        <template #default="{ row }">
          <el-tag :type="levelTagType[row.level] || 'info'" size="small" effect="dark">
            {{ row.level || 'INFO' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('notification.category')" width="110">
        <template #default="{ row }">
          <el-tag :type="categoryTagType[row.category] || 'info'" size="small">
            {{ row.category || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'font-bold': row.readStatus === 0 }">
            {{ row.recallStatus === 'RECALLED' ? `[${t('notification.recalled')}] ` : '' }}{{ row.title }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="content-text">{{ row.content }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('notification.readStatus')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.readStatus === 0 ? 'danger' : 'info'" size="small">
            {{ row.readStatus === 0 ? t('notification.unread') : t('notification.read') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">
          {{ row.createdAt }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="scope">
          <el-button
            v-if="(scope.row as NotificationVO).readStatus === 0"
            link
            type="primary"
            size="small"
            @click.stop="handleMarkRead(scope.row as NotificationVO)"
          >
            {{ t('notification.read') }}
          </el-button>
          <el-button
            v-if="(scope.row as NotificationVO).actionUrl && (scope.row as NotificationVO).recallStatus !== 'RECALLED'"
            link
            type="primary"
            size="small"
            @click.stop="handleAction(scope.row as NotificationVO)"
          >
            {{ t('notification.actionUrl') }}
          </el-button>
          <el-button
            v-if="(scope.row as NotificationVO).recallStatus !== 'RECALLED'"
            link
            type="warning"
            size="small"
            @click.stop="handleRecall(scope.row as NotificationVO)"
          >
            {{ t('notification.recall') }}
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            @click.stop="handleDelete(scope.row as NotificationVO)"
          >
            {{ t('notification.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.notification-inbox {
  padding: 16px;
}

.filter-card {
  margin-bottom: 16px;

  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .toolbar-left {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  .total-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.content-text {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.font-bold {
  font-weight: 600;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
