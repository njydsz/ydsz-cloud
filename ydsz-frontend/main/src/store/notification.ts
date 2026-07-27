import type { NotificationApi } from '#/api/core/notification';

import { ref, type Ref } from 'vue';

import { useAccessStore } from '@ydsz/stores';

import { ElNotification } from 'element-plus';

import {
  getNotificationsApi,
  getUnreadCountApi,
  markAllAsReadApi,
  markAsReadApi,
} from '#/api/core/notification';

/**
 * 全局通知 Store — 整合 REST API + WebSocket 实时推送
 */
class NotificationStore {
  notifications: Ref<NotificationApi.NotificationItem[]> = ref([]);
  unreadCount: Ref<number> = ref(0);
  wsConnected: Ref<boolean> = ref(false);

  private ws: null | WebSocket = null;
  private reconnectTimer: null | ReturnType<typeof setTimeout> = null;
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 10;
  private readonly reconnectDelay = 5000;

  /**
   * 从后端加载通知列表
   */
  async loadNotifications(pageNum = 1, pageSize = 20) {
    try {
      const res = await getNotificationsApi({ pageNum, pageSize });
      this.notifications.value = res.items;
      this.unreadCount.value = res.total;
    } catch {
      // 静默失败
    }
  }

  /**
   * 刷新未读计数
   */
  async refreshUnreadCount() {
    try {
      this.unreadCount.value = await getUnreadCountApi();
    } catch {
      // 静默失败
    }
  }

  /**
   * 标记单条已读
   */
  async markRead(id: string) {
    try {
      await markAsReadApi(id);
      const item = this.notifications.value.find((n) => n.id === id);
      if (item && !item.isRead) {
        item.isRead = true;
        this.unreadCount.value = Math.max(0, this.unreadCount.value - 1);
      }
    } catch {
      // 静默失败
    }
  }

  /**
   * 全部标记已读
   */
  async markAllRead() {
    try {
      await markAllAsReadApi();
      this.notifications.value.forEach((n) => (n.isRead = true));
      this.unreadCount.value = 0;
    } catch {
      // 静默失败
    }
  }

  /**
   * 连接 WebSocket 接收实时通知
   */
  connectWebSocket() {
    if (this.ws?.readyState === WebSocket.OPEN) return;

    const accessStore = useAccessStore();
    const token = accessStore.accessToken;
    if (!token) return;

    // 构建 WebSocket URL
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/ws/notification?token=${token}`;

    try {
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        console.info('[WS] Notification WebSocket connected');
        this.wsConnected.value = true;
        this.reconnectAttempts = 0;
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.handleWsMessage(data);
        } catch {
          // 非法 JSON 忽略
        }
      };

      this.ws.onclose = () => {
        console.info('[WS] Notification WebSocket closed');
        this.wsConnected.value = false;
        this.scheduleReconnect();
      };

      this.ws.onerror = (error) => {
        console.error('[WS] Notification WebSocket error:', error);
        this.wsConnected.value = false;
      };
    } catch (error) {
      console.error('[WS] Failed to connect WebSocket:', error);
      this.scheduleReconnect();
    }
  }

  /**
   * 处理 WebSocket 消息
   */
  private handleWsMessage(data: any) {
    switch (data.type) {
      case 'notification': {
        // 新通知
        const notification: NotificationApi.NotificationItem = data.payload;
        this.notifications.value.unshift(notification);
        this.unreadCount.value++;

        // 弹出桌面通知
        ElNotification({
          title: notification.title || '新通知',
          message: notification.message,
          type: 'info',
          duration: 5000,
        });
        break;
      }
      case 'unread_count': {
        // 未读数更新
        this.unreadCount.value = data.count ?? 0;
        break;
      }
      case 'mark_read': {
        // 标记已读（多端同步）
        const item = this.notifications.value.find(
          (n) => n.id === data.notificationId,
        );
        if (item) item.isRead = true;
        break;
      }
      default:
        // 未知消息类型忽略
        break;
    }
  }

  /**
   * 自动重连
   */
  private scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn(
        '[WS] Max reconnection attempts reached, giving up.',
      );
      return;
    }

    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);

    this.reconnectAttempts++;
    const delay = this.reconnectDelay * this.reconnectAttempts;

    this.reconnectTimer = setTimeout(() => {
      console.info(
        `[WS] Reconnecting (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})...`,
      );
      this.connectWebSocket();
    }, delay);
  }

  /**
   * 断开 WebSocket
   */
  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.reconnectAttempts = this.maxReconnectAttempts; // 阻止自动重连
    if (this.ws) {
      this.ws.onclose = null; // 防止触发重连
      this.ws.close();
      this.ws = null;
    }
    this.wsConnected.value = false;
  }
}

export const notificationStore = new NotificationStore();
