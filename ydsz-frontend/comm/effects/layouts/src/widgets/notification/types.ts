/**
 * types 模块
 *
 * @path comm\effects\layouts\src\widgets\notification\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
interface NotificationItem {
  avatar: string;
  date: string;
  isRead?: boolean;
  message: string;
  title: string;
}

export type { NotificationItem };
