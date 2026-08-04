/**
 * 用户行为面包屑（Breadcrumb）— 错误发生前的用户操作轨迹
 *
 * 记录最近 N 条用户行为（点击、路由跳转、HTTP 请求、自定义事件），
 * 在错误上报时附带，便于复现错误发生路径。对标 Sentry breadcrumb。
 *
 * @path comm/effects/monitor/src/breadcrumb.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/** 面包屑类别 */
export type BreadcrumbCategory =
  | 'click'
  | 'navigation'
  | 'http'
  | 'console'
  | 'custom'
  | 'error';

/** 面包屑级别 */
export type BreadcrumbLevel = 'info' | 'warning' | 'error';

/** 单条面包屑记录 */
export interface Breadcrumb {
  /** 时间戳（ms） */
  timestamp: number;
  /** 类别 */
  category: BreadcrumbCategory;
  /** 级别 */
  level: BreadcrumbLevel;
  /** 消息文本 */
  message: string;
  /** 附加数据 */
  data?: Record<string, unknown>;
}

/** 面包屑最大容量（环形缓冲，超出后丢弃最旧的） */
const MAX_BREADCRUMBS = 30;

/** 面包屑环形缓冲队列 */
const breadcrumbs: Breadcrumb[] = [];

/**
 * 添加一条面包屑。
 *
 * @param category 类别
 * @param message 消息
 * @param options 可选级别与附加数据
 */
export function addBreadcrumb(
  category: BreadcrumbCategory,
  message: string,
  options: { data?: Record<string, unknown>; level?: BreadcrumbLevel } = {},
): void {
  const crumb: Breadcrumb = {
    timestamp: Date.now(),
    category,
    level: options.level ?? 'info',
    message,
    data: options.data,
  };

  breadcrumbs.push(crumb);

  // 环形缓冲：超出容量丢弃最旧的
  if (breadcrumbs.length > MAX_BREADCRUMBS) {
    breadcrumbs.shift();
  }
}

/**
 * 获取当前所有面包屑的快照（调用方通常在错误上报时读取）。
 *
 * 返回数组副本，避免外部修改内部队列。
 */
export function getBreadcrumbs(): Breadcrumb[] {
  return [...breadcrumbs];
}

/**
 * 清空面包屑队列（一般在错误上报完成后调用）。
 */
export function clearBreadcrumbs(): void {
  breadcrumbs.length = 0;
}

/**
 * 安装自动面包屑采集：
 * - click：捕获点击事件目标元素的简短描述
 * - navigation：监听 popstate / hashchange 记录路由变化
 * - console：拦截 console.warn / console.error 记录日志
 *
 * http 面包屑由 request 模块主动调用 addBreadcrumb 添加（避免双重拦截）。
 *
 * @returns 卸载函数，调用后移除所有监听
 */
export function setupBreadcrumbAutoCapture(): () => void {
  // 1. 点击事件（捕获阶段，记录最近一次点击目标）
  const onClick = (event: MouseEvent): void => {
    const target = event.target as HTMLElement | null;
    if (!target) return;
    // 生成简短描述：优先 id，其次 class，最后 tagName
    const desc =
      target.id
        ? `#${target.id}`
        : target.getAttribute('data-breadcrumb')
          ? `[${target.getAttribute('data-breadcrumb')}]`
          : target.tagName.toLowerCase();
    addBreadcrumb('click', desc, {
      data: { x: event.clientX, y: event.clientY },
    });
  };
  // 使用 capture + passive，降低对交互性能的影响
  window.addEventListener('click', onClick, { capture: true, passive: true });

  // 2. 路由变化
  const onNavigation = (): void => {
    addBreadcrumb('navigation', window.location.pathname + window.location.hash);
  };
  window.addEventListener('popstate', onNavigation);
  window.addEventListener('hashchange', onNavigation);

  // 3. console.warn / console.error
  const originalWarn = console.warn;
  const originalError = console.error;
  console.warn = (...args: unknown[]): void => {
    addBreadcrumb('console', args.map(String).join(' '), { level: 'warning' });
    originalWarn.apply(console, args);
  };
  console.error = (...args: unknown[]): void => {
    addBreadcrumb('console', args.map(String).join(' '), { level: 'error' });
    originalError.apply(console, args);
  };

  // 返回卸载函数
  return () => {
    window.removeEventListener('click', onClick, { capture: true } as EventListenerOptions);
    window.removeEventListener('popstate', onNavigation);
    window.removeEventListener('hashchange', onNavigation);
    console.warn = originalWarn;
    console.error = originalError;
  };
}
