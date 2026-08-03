/**
 * 标签页保活与微前端内核联动
 *
 * 注册 tabClosed 回调：当用户关闭页签时，通知微前端内核卸载对应子应用。
 * lite-kernel 利用此回调实现：页签关闭 → 子应用完整卸载并释放内存。
 *
 * @path main/src/hooks/use-tabbar-micro-sync.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import { onTabClosed } from '@ydsz/stores';
import { microRuntime } from '../bootstrap';

/** 路由前缀 → 子应用名 映射（与 main/src/qiankun/index.ts 注册表一致） */
const PATH_TO_APP: Record<string, string> = {
  '/ydsz-user': 'userinfo-web',
  '/ydsz-sys': 'system-web',
  '/ydsz-proj': 'project-web',
  '/ydsz-msg': 'message-web',
  '/ydsz-cron': 'cronjob-web',
  '/ydsz-flow': 'workflow-web',
  '/ydsz-wiki': 'nextwiki-web',
  '/ydsz-rule': 'literule-web',
  '/ydsz-ai': 'agent-web',
};

/**
 * 根据路径前缀提取子应用名。
 * @example getAppFromPath('/ydsz-proj/execution/list') → 'project-web'
 */
function getAppFromPath(path: string): null | string {
  for (const [prefix, appName] of Object.entries(PATH_TO_APP)) {
    if (path.startsWith(prefix)) {
      return appName;
    }
  }
  return null;
}

/**
 * 启动标签页-微前端联动。
 * 在主布局 onMounted 中调用一次即可。
 */
export function useTabbarMicroSync(): void {
  onTabClosed((path) => {
    if (!microRuntime) return;

    const appName = getAppFromPath(path);
    if (appName) {
      // 标记不保活 + 卸载子应用（内核会在下一个页签关闭时执行完整卸载）
      microRuntime.setKeepAlive(appName, false);

      // 卸载该子应用（若内核支持；qiankun 不支持则静默跳过）
      void microRuntime.unmountApp(appName).then((result) => {
        if (result.success) {
          console.info(`[MicroSync] Unmounted ${appName} (tab closed)`);
        }
      });
    }
  });
}
