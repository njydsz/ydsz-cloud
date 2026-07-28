<!--
 * basic 布局组件
 *
 * @path main\src\layouts\basic.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { NotificationItem } from '@ydsz/layouts';

import { computed, onMounted, onUnmounted, watch } from 'vue';

import { AuthenticationLoginExpiredModal } from '@ydsz/common-ui';
import { useWatermark } from '@ydsz/hooks';
import {
  BasicLayout,
  LockScreen,
  Notification,
  UserDropdown,
} from '@ydsz/layouts';
import { preferences } from '@ydsz/preferences';
import { useAccessStore, useUserStore } from '@ydsz/stores';

import { useAuthStore } from '#/store';
import { notificationStore } from '#/store/notification';
import LoginForm from '#/views/_core/authentication/login.vue';

const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

// 将后端通知格式转为 UI 组件需要的格式
const notifications = computed<NotificationItem[]>(() =>
  notificationStore.notifications.value.map((n) => ({
    avatar: n.avatar || 'https://avatar.vercel.sh/1',
    date: n.createdAt,
    isRead: n.isRead,
    message: n.message,
    title: n.title,
  })),
);

const showDot = computed(() => notificationStore.unreadCount.value > 0);

async function handleLogout() {
  notificationStore.disconnect();
  await authStore.logout(false);
}

function handleNoticeClear() {
  notificationStore.notifications.value = [];
}

async function handleMakeAll() {
  await notificationStore.markAllRead();
}

// 登录后连接 WebSocket 并加载通知
watch(
  () => accessStore.accessToken,
  (token) => {
    if (token) {
      notificationStore.loadNotifications();
      notificationStore.refreshUnreadCount();
      notificationStore.connectWebSocket();
    } else {
      notificationStore.disconnect();
    }
  },
  { immediate: true },
);

watch(
  () => preferences.app.watermark,
  async (enable) => {
    if (enable) {
      await updateWatermark({
        content: `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);

onMounted(() => {
  if (accessStore.accessToken) {
    notificationStore.loadNotifications();
    notificationStore.refreshUnreadCount();
    notificationStore.connectWebSocket();
  }
});

onUnmounted(() => {
  notificationStore.disconnect();
});
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :text="userStore.userInfo?.realName"
        description="ydsz-pmis-team@njydsz.com"
        tag-text="Pro"
        @logout="handleLogout"
      />
    </template>
    <template #notification>
      <Notification
        :dot="showDot"
        :notifications="notifications"
        @clear="handleNoticeClear"
        @make-all="handleMakeAll"
      />
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>
