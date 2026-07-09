<!--
  @fileoverview 通知中心铃铛组件（批次 30-4 增强）
  @description 顶栏通知入口：未读数角标、收件箱弹层、标记已读：
  - WebSocket 实时刷新未读数（P0-2），失败时 60s 轮询兜底
  - 与后端 NotificationController（/notifications）对接
  - 场景: 站内消息/通知中心快捷入口
  - 批次 30-4 增强：
    * 分类 tab（全部/系统/流程/预警/待办/公告）
    * 类型图标（按 level 渲染 INFO/WARN/ERROR/URGENT 图标与颜色）
    * 底部"查看全部"入口，跳转到 /notification/inbox
    * 点击通知项若有 actionUrl 则跳转
  @module components/common/NotificationBell
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, InfoFilled, WarningFilled, CircleCloseFilled, WarnFilled, ArrowRight } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  getUnreadCount,
  getInbox,
  markRead,
  markAllRead,
} from '@/api/notification'
import { useWebSocket } from '@/composables/useWebSocket'
import type { NotificationVO } from '@/api/notification/types'

const { t } = useI18n()
const router = useRouter()

/** 未读数 */
const unreadCount = ref(0)
/** 收件箱列表 */
const notifications = ref<NotificationVO[]>([])
/** 收件箱加载中标志 */
const inboxLoading = ref(false)
/** 轮询定时器（WebSocket 降级兜底） */
let pollTimer: ReturnType<typeof setInterval> | null = null

/** 当前选中的分类 tab：'' 表示全部 */
const activeCategory = ref('')

/** 分类 tab 选项 */
const categoryTabs = computed(() => [
  { label: t('notification.all'), value: '' },
  { label: t('notification.categorySystem'), value: 'SYSTEM' },
  { label: t('notification.categoryWorkflow'), value: 'WORKFLOW' },
  { label: t('notification.categoryAlert'), value: 'ALERT' },
  { label: t('notification.categoryTodo'), value: 'TODO' },
  { label: t('notification.categoryAnnounce'), value: 'ANNOUNCE' },
])

/**
 * 根据通知级别返回图标组件与颜色 class
 * @param level 通知级别 INFO/WARN/ERROR/URGENT
 */
function getLevelMeta(level: string | undefined): { icon: typeof InfoFilled; class: string } {
  switch (level) {
    case 'URGENT':
      return { icon: WarnFilled, class: 'level-urgent' }
    case 'ERROR':
      return { icon: CircleCloseFilled, class: 'level-error' }
    case 'WARN':
      return { icon: WarningFilled, class: 'level-warn' }
    case 'INFO':
    default:
      return { icon: InfoFilled, class: 'level-info' }
  }
}

/** 分类标签文案映射 */
const categoryLabelMap: Record<string, string> = {
  SYSTEM: 'notification.categorySystem',
  WORKFLOW: 'notification.categoryWorkflow',
  ALERT: 'notification.categoryAlert',
  TODO: 'notification.categoryTodo',
  ANNOUNCE: 'notification.categoryAnnounce',
}

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

/** 拉取收件箱（按当前分类 tab 过滤） */
const fetchInbox = async () => {
  inboxLoading.value = true
  try {
    const resp = await getInbox({
      page: 1,
      size: 10,
      category: activeCategory.value || undefined,
    })
    notifications.value = resp.data?.records ?? []
  } catch {
    // 静默失败
  } finally {
    inboxLoading.value = false
  }
}

/** 标记单条已读 */
const handleMarkRead = async (id: string) => {
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

/** 切换分类 tab */
const handleTabChange = () => {
  fetchInbox()
}

/**
 * 点击通知项：
 * 1. 若未读则标记已读
 * 2. 若有 actionUrl 则跳转
 * @param n 通知对象
 */
const handleClickItem = async (n: NotificationVO) => {
  if (n.readStatus === 0) {
    await handleMarkRead(n.id)
  }
  if (n.actionUrl) {
    router.push(n.actionUrl).catch(() => { /* 路由跳转失败已被全局拦截 */ })
  }
}

/** 跳转到通知中心收件箱 */
const handleViewAll = () => {
  router.push('/notification/inbox').catch(() => { /* 路由跳转失败已被全局拦截 */ })
}

/** 弹层展开时拉取收件箱 */
const handleShow = () => {
  activeCategory.value = ''
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
  <el-popover :width="420" trigger="click" @show="handleShow">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
        <el-icon :size="20" class="bell-icon"><Bell /></el-icon>
      </el-badge>
    </template>
    <div class="notification-panel">
      <!-- 头部：标题 + 全部已读 -->
      <div class="notification-header">
        <span class="notification-title">{{ t('notification.title') }}</span>
        <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="handleMarkAllRead">
          {{ t('notification.markAllRead') }}
        </el-button>
      </div>

      <!-- 分类 tab（批次 30-4） -->
      <div class="notification-tabs">
        <span
          v-for="tab in categoryTabs"
          :key="tab.value"
          class="notification-tab"
          :class="{ 'is-active': activeCategory === tab.value }"
          @click="activeCategory = tab.value; handleTabChange()"
        >
          {{ tab.label }}
        </span>
      </div>

      <!-- 通知列表 -->
      <el-scrollbar max-height="360px">
        <div v-loading="inboxLoading" class="inbox-body">
          <el-empty v-if="!inboxLoading && notifications.length === 0" :description="t('notification.empty')" :image-size="80" />
          <div
            v-for="n in notifications"
            :key="n.id"
            class="notification-item"
            :class="{ unread: n.readStatus === 0 }"
            @click="handleClickItem(n)"
          >
            <!-- 类型图标（批次 30-4） -->
            <el-icon :size="18" class="notification-level-icon" :class="getLevelMeta(n.level).class">
              <component :is="getLevelMeta(n.level).icon" />
            </el-icon>
            <div class="notification-main">
              <div class="notification-item-title">
                {{ n.title }}
                <el-tag
                  v-if="n.category && categoryLabelMap[n.category]"
                  size="small"
                  effect="plain"
                  class="notification-category-tag"
                >
                  {{ t(categoryLabelMap[n.category]) }}
                </el-tag>
              </div>
              <div class="notification-content">{{ n.content }}</div>
              <div class="notification-time">{{ n.createdAt }}</div>
            </div>
          </div>
        </div>
      </el-scrollbar>

      <!-- 底部：查看全部入口（批次 30-4） -->
      <div class="notification-footer" @click="handleViewAll">
        <span>{{ t('notification.viewAll') }}</span>
        <el-icon :size="12"><ArrowRight /></el-icon>
      </div>
    </div>
  </el-popover>
</template>

<style lang="scss" scoped>
.notification-panel {
  padding: 0;
  display: flex;
  flex-direction: column;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .notification-title {
    font-weight: 600;
    font-size: 14px;
  }
}

/* 分类 tab（批次 30-4） */
.notification-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-x: auto;

  .notification-tab {
    flex-shrink: 0;
    padding: 4px 10px;
    font-size: 12px;
    color: var(--el-text-color-regular);
    cursor: pointer;
    border-radius: 4px;
    transition: all 0.15s;
    white-space: nowrap;

    &:hover {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }

    &.is-active {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
      font-weight: 600;
    }
  }
}

.notification-item {
  display: flex;
  gap: 10px;
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

  .notification-level-icon {
    flex-shrink: 0;
    margin-top: 2px;

    &.level-info {
      color: var(--el-color-info);
    }

    &.level-warn {
      color: var(--el-color-warning);
    }

    &.level-error {
      color: var(--el-color-danger);
    }

    &.level-urgent {
      color: var(--el-color-danger);
      animation: pulse 1.5s ease-in-out infinite;
    }
  }

  .notification-main {
    flex: 1;
    min-width: 0;
  }

  .notification-item-title {
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 4px;
    display: flex;
    align-items: center;
    gap: 6px;

    .notification-category-tag {
      flex-shrink: 0;
      transform: scale(0.85);
      transform-origin: left center;
    }
  }

  .notification-content {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .notification-time {
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }
}

/* 底部查看全部（批次 30-4） */
.notification-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: var(--el-color-primary-light-9);
  }
}

.bell-icon {
  cursor: pointer;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
</style>
