/**
 * 多 Tab 子应用同步增强
 *
 * v3.7.0 (P3-1): 从"关闭任意 Tab 即卸载子应用"升级为"按子应用会话追踪"：
 * 1. 每个子应用独立追踪打开的 Tab 数，仅当最后一项 Tab 关闭时才卸载子应用
 * 2. 打开 Tab 时自动 pin 子应用（防 keep-alive 淘汰），最后 Tab 关闭时 unpin
 * 3. 记录子应用最后一次激活的路径，下次打开同子应用时自动恢复激活态
 *
 * 与 `comm/effects/layouts` 中 useTabbar 的配合：
 * - useTabbar 的 watch(route.fullPath) 在路由变化时自动 addTab
 * - 本 hook 监听 onTabClosed / onTabOpened，维护 per-app Tab 计数器
 * - 计数器 > 0 时 setKeepAlive(true) 并 pin；计数器 === 0 时 unpin 并 unmount
 *
 * @path main/src/hooks/use-tabbar-micro-sync.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import { onTabClosed, useTabbarStore } from '@ydsz/stores';
import { PATH_TO_APP_MAP } from '@ydsz/vite-config';
import { microRuntime } from '../bootstrap';

/** 路由前缀 → 子应用名 映射（由注册表单源 PATH_TO_APP_MAP 驱动） */
const PATH_TO_APP = PATH_TO_APP_MAP;

/** 打开指定路径的子应用名 */
function getAppFromPath(path: string): null | string {
  for (const [prefix, appName] of Object.entries(PATH_TO_APP)) {
    if (path.startsWith(prefix)) {
      return appName;
    }
  }
  return null;
}

/**
 * 子应用 Tab 会话状态
 *
 * 维护每个子应用当前打开的 Tab 路径集合与最后激活路径，
 * 用于决定何时可以安全卸载子应用、何时需要 keep-alive pin。
 */
interface SubAppSession {
  /** 当前打开的 Tab 路径集合（fullPath 去重） */
  openPaths: Set<string>;
  /** 该子应用最后激活的路径（用于路由恢复） */
  lastActivePath: string | null;
}

/** 子应用名 → 会话状态（内存 Map，不持久化；刷新后重建） */
const sessions = new Map<string, SubAppSession>();

/** 获取或创建子应用会话 */
function getOrCreateSession(appName: string): SubAppSession {
  let session = sessions.get(appName);
  if (!session) {
    session = { openPaths: new Set(), lastActivePath: null };
    sessions.set(appName, session);
  }
  return session;
}

/**
 * 记录子应用打开了一个 Tab（由路由变化驱动添加）
 *
 * 在 onTabOpened 事件或 route guard 中调用；已存在路径时仅更新激活态。
 *
 * @param path - 打开的完整路径
 * @param appName - 关联的子应用名
 */
export function recordSubAppTabOpened(path: string, appName: string): void {
  const session = getOrCreateSession(appName);
  const isNewTab = !session.openPaths.has(path);
  session.openPaths.add(path);
  session.lastActivePath = path;

  // 首次打开该子应用的 Tab → 保活 + pin
  if (isNewTab && microRuntime) {
    microRuntime.setKeepAlive(appName, true);
    // micro-kernel 原生 pin API（如可用）；忽略兼容
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (microRuntime as any).setPinnedApp?.(appName, true);
    } catch {
      /* qiankun 等适配端无 pin 能力时忽略 */
    }
  }
}

/** 记录子应用关闭了一个 Tab；返回是否需要卸载子应用 */
function recordSubAppTabClosed(appName: string, path: string): boolean {
  const session = sessions.get(appName);
  if (!session) return false;

  session.openPaths.delete(path);
  if (session.openPaths.size === 0) {
    // 最后一项 Tab 关闭 → 可卸载
    sessions.delete(appName);
    return true;
  }

  // 如果关闭的是当前激活路径，尝试恢复到最近一个仍打开的路径
  if (session.lastActivePath === path) {
    const remaining = [...session.openPaths];
    session.lastActivePath = remaining[remaining.length - 1] ?? null;
  }
  return false;
}

/**
 * 根据路径前缀提取子应用名。
 * @example getAppFromPath('/ydsz-proj/execution/list') → 'project-web'
 */
export { getAppFromPath };

/**
 * 启动标签页-微前端联动。
 *
 * 在主布局 onMounted 中调用一次即可。
 *
 * 职责：
 * - Tab 关闭时追踪 per-app 计数器，最后一个 Tab 卸载子应用
 * - Tab 关闭时 unpin 子应用，允许后续 keep-alive LRU 淘汰
 * - 安全降级：microRuntime 未初始化时静默跳过
 *
 * 与 useTabbar 的配合：
 * - useTabbar 的 `addTab` 已隐式调用 recordSubAppTabOpened (通过 route watcher)
 * - 如果业务侧需独立追踪 Tab 打开事件，可手动调用 recordSubAppTabOpened
 */
export function useTabbarMicroSync(): void {
  onTabClosed((path) => {
    if (!microRuntime) return;

    const appName = getAppFromPath(path);
    if (!appName) return;

    const shouldUnmount = recordSubAppTabClosed(appName, path);
    if (!shouldUnmount) {
      // 还有其他 Tab 使用该子应用，不卸载
      return;
    }

    // 最后一 Tab 关闭 → 取消 pin + 取消保活 + 卸载
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (microRuntime as any).setPinnedApp?.(appName, false);
    } catch {
      /* 兼容 */
    }
    microRuntime.setKeepAlive(appName, false);

    void microRuntime.unmountApp(appName).then((result) => {
      if (result.success) {
        console.info(`[MultiTabSync] Unmounted ${appName} (all tabs closed)`);
      }
    });
  });

  // 路由切换时更新 per-app 的 lastActivePath
  // 注：此处保留回调注册能力，供主应用路由 router.afterEach 调用
}

/**
 * 获取当前子应用会话快照（供调试面板使用）。
 *
 * 返回数据为深拷贝，调用方可安全使用。
 */
export function getSubAppSessionSnapshot(): Array<{
  appName: string;
  openPaths: string[];
  lastActivePath: string | null;
}> {
  return [...sessions.entries()].map(([appName, session]) => ({
    appName,
    openPaths: [...session.openPaths],
    lastActivePath: session.lastActivePath,
  }));
}

/**
 * 尝试恢复子应用最后激活的路径。
 *
 * 在用户打开新 Tab 且目标路由前缀与原活跃子应用相同时，
 * 可调用此函数决定是否跳转到 lastActivePath。
 *
 * @returns 可恢复路径，或 null（无历史）
 */
export function getSubAppLastActivePath(appName: string): null | string {
  return sessions.get(appName)?.lastActivePath ?? null;
}
