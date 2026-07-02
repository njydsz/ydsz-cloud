<!--
  @file 通知中心铃铛组件
  @description 顶栏通知入口：未读数角标、收件箱弹层、标记已读。
    - WebSocket 实时刷新未读数（P0-2），失败时 60s 轮询兜底
    - 与后端 NotificationController（/api/v1/notifications）对接
  @module components/common/NotificationBell
-->
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import {
  getUnreadCount,
  getInbox,
  markRead,
  markAllRead,
} from '@/api/notification'
import { useWebSocket } from '@/composables/useWebSocket'
import type { NotificationVO } from '@/api/notification/types'

/** 未读数 */
const unreadCount = ref(0)
/** 收件箱列表 */
const notifications = ref<NotificationVO[]>([])
/** 轮询定时器（WebSocket 降级兜底） */
let pollTimer: ReturnType<typeof setInterval> | null = null

const { on } = useWebSocket()

/** 拉取未读数 */
const fetchUnreadCount = async () => {
  try {
    const resp = await getUnreadCount()
    unreadCount.value = resp.data ?? 0
  } catch {
    // 静默失败
  }
}

/** 拉取收件箱 */
const fetchInbox = async () => {
  try {
    const resp = await getInbox({ page: 1, size: 10 })
    notifications.value = resp.data?.records ?? []
  } catch {
    // 静默失败
  }
}

/** 标记单条已读 */
const handleMarkRead = async (id: number) => {
  await markRead(id)
  await fetchUnreadCount()
  await fetchInbox()
}

/** 全部已读 */
const handleMarkAllRead = async () => {
  await markAllRead()
  await fetchUnreadCount()
  await fetchInbox()
}

/** 弹层展开时拉取收件箱 */
const handleShow = () => {
  fetchInbox()
}

onMounted(() => {
  fetchUnreadCount()
  // WebSocket 推送到达时刷新未读数
  on('NOTIFICATION', () => {
    fetchUnreadCount()
  })
  // 定时轮询作为 WebSocket 降级方案
  pollTimer = setInterval(fetchUnreadCount, 60000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <el-popover :width="400" trigger="click" @show="handleShow">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
        <el-icon :size="20" class="bell-icon"><Bell /></el-icon>
      </el-badge>
    </template>
    <div class="notification-panel">
      <div class="notification-header">
        <span>通知中心</span>
        <el-button v-if="unreadCount > 0" link type="primary" @click="handleMarkAllRead">
          全部已读
        </el-button>
      </div>
      <el-scrollbar max-height="400px">
        <div v-if="notifications.length === 0" class="empty-tip">暂无通知</div>
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notification-item"
          :class="{ unread: n.readStatus === 0 }"
          @click="n.readStatus === 0 && handleMarkRead(n.id)"
        >
          <div class="notification-title">{{ n.title }}</div>
          <div class="notification-content">{{ n.content }}</div>
          <div class="notification-time">{{ n.createdAt }}</div>
        </div>
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<style lang="scss" scoped>
.notification-panel {
  padding: 0;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-weight: 600;
}

.notification-item {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &.unread {
    background: var(--el-color-primary-light-9);
  }
}

.notification-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
}

.notification-content {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.notification-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.empty-tip {
  text-align: center;
  padding: 40px;
  color: var(--el-text-color-placeholder);
}

.bell-icon {
  cursor: pointer;
}
</style>
