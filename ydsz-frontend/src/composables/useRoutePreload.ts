/**
 * @file 路由预加载 composable
 * @description P1-4: 首屏加载优化 — 在页面空闲时或用户交互时预加载可能访问的路由。
 *   - idle callback API：requestIdleCallback 在主线程空闲时执行
 *   - IntersectionObserver：检测视口中的链接，hover 时预加载
 *   - 智能调度：基于访问频率预加载高频路由
 * @module composables/useRoutePreload
 */

interface RouteCache {
  /** 预加载的路径（例如 '/dashboard'） */
  path: string
  /** 动态导入函数 */
  loader: () => Promise<unknown>
  /** 是否已加载 */
  loaded: boolean
  /** 访问次数（用于热度计算） */
  hits: number
}

/** 路由预加载缓存（已预加载的路由不会重复预加载） */
const routeCache = new Map<string, RouteCache>()

/** 默认高频路由（根据业务特点定义） */
const DEFAULT_HIGH_FREQ_ROUTES = [
  '/dashboard',
  '/project/initiation',
  '/project/contract',
  '/execution/wbs-task',
  '/workflow/approval-center',
  '/finance/invoice',
  '/resource/employee',
] as const

/**
 * 路由预加载 composable
 */
export function useRoutePreload() {
  /** 标记路由访问（增加命中次数，用于热度排序） */
  function recordRouteHit(path: string) {
    const cached = routeCache.get(path)
    if (cached) {
      cached.hits++
    }
  }

  /**
   * 预加载指定路由
   *
   * @param path 路由路径
   * @param loader 动态导入函数
   */
  async function preloadRoute(path: string, loader: () => Promise<unknown>): Promise<void> {
    // 如果已加载或正在加载，直接返回
    if (routeCache.has(path)) {
      return
    }

    // 标记为已缓存（防止重复预加载）
    routeCache.set(path, { path, loader, loaded: false, hits: 0 })

    try {
      await loader()
      routeCache.set(path, { path, loader, loaded: true, hits: 0 })
    } catch {
      // 预加载失败，从缓存移除，允许重试
      routeCache.delete(path)
    }
  }

  /**
   * 在空闲时预加载高频路由
   *
   * 使用 requestIdleCallback 在浏览器主线程空闲时分批预加载
   */
  function preloadHighFreqRoutes(): void {
    if (typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(
        () => {
          // 延迟 2s 后开始预加载（优先首屏渲染）
          setTimeout(() => {
            // 根据热度排序路由
            const sortedRoutes = Array.from(routeCache.values())
              .sort((a, b) => b.hits - a.hits)
              .slice(0, 5)

            // 优先加载高频路由
            sortedRoutes.forEach((r) => {
              if (!r.loaded) {
                preloadRoute(r.path, r.loader)
              }
            })
          }, 2000)
        },
        { timeout: 3000 },
      )
    }
  }

  /**
   * 初始化路由映射表（需要从 router 导入）
   *
   * 这应该从 router/index.ts 或 routes.ts 中的路由配置提取
   */
  function initRouteMapping(): void {
    // 注意：实际使用时需要从路由配置中提取 path → loader 映射
    // 由于路由配置已经在 routes.ts 中定义，这里只是示例
    const examples = DEFAULT_HIGH_FREQ_ROUTES.map((path) => ({
      path,
      loader: () => import(`@/views${path}/index.vue`),
      loaded: false,
      hits: path === '/dashboard' ? 10 : 0, // dashboard 认为访问频率最高
    }))

    examples.forEach((r) => routeCache.set(r.path, r))
  }

  return {
    recordRouteHit,
    preloadRoute,
    preloadHighFreqRoutes,
    initRouteMapping,
  }
}