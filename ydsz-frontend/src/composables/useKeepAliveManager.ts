/**
 * @file KeepAlive 精细化管理 composable
 * @description P1-5: 控制 KeepAlive 缓存数量，防止内存膨胀。
 *   - 限制最大缓存页面数（默认 10 个）
 *   - LRU 淘汰策略：超过上限时淘汰最久未访问的缓存
 *   - 与 <keep-alive :include="cachedPageNames"> 配合使用
 *
 * 使用方式：
 *   const { cachedPageNames, addCache, removeCache, clearCache } = useKeepAliveManager()
 *   // 在 App.vue 或 Layout.vue 中：
 *   <keep-alive :include="cachedPageNames">
 *     <router-view />
 *   </keep-alive>
 *
 * @module composables/useKeepAliveManager
 */
import { ref, onMounted, onUnmounted } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

/** 默认最大缓存页面数 */
const DEFAULT_MAX_CACHE = 10

/** 全局缓存状态（跨组件共享） */
const cachedNames = ref<string[]>([])
const accessOrder = new Map<string, number>()

let maxCache = DEFAULT_MAX_CACHE
let routerHookInstalled = false

/** 添加缓存 */
function addCache(name: string): void {
  if (!name || cachedNames.value.includes(name)) {
    // 更新访问时间
    accessOrder.set(name, Date.now())
    return
  }

  if (cachedNames.value.length >= maxCache) {
    // LRU 淘汰：找到最久未访问的
    let oldestName = ''
    let oldestTime = Infinity
    for (const [n, t] of accessOrder.entries()) {
      if (cachedNames.value.includes(n) && t < oldestTime) {
        oldestTime = t
        oldestName = n
      }
    }
    if (oldestName) {
      cachedNames.value = cachedNames.value.filter((n) => n !== oldestName)
      accessOrder.delete(oldestName)
    }
  }

  cachedNames.value.push(name)
  accessOrder.set(name, Date.now())
}

/** 移除缓存 */
function removeCache(name: string): void {
  cachedNames.value = cachedNames.value.filter((n) => n !== name)
  accessOrder.delete(name)
}

/** 清空所有缓存 */
function clearCache(): void {
  cachedNames.value = []
  accessOrder.clear()
}

/** 设置最大缓存数 */
function setMaxCache(max: number): void {
  maxCache = Math.max(1, max)
  // 如果当前缓存超过新上限，淘汰多余的
  while (cachedNames.value.length > maxCache) {
    let oldestName = ''
    let oldestTime = Infinity
    for (const [n, t] of accessOrder.entries()) {
      if (cachedNames.value.includes(n) && t < oldestTime) {
        oldestTime = t
        oldestName = n
      }
    }
    if (oldestName) {
      removeCache(oldestName)
    } else {
      break
    }
  }
}

export function useKeepAliveManager() {
  /**
   * 安装路由钩子，自动管理缓存
   * 应在 Layout 组件的 onMounted 中调用一次
   */
  function installRouteHook(router: {
    beforeEach: (guard: (to: RouteLocationNormalized, from: RouteLocationNormalized) => void) => void
  }) {
    if (routerHookInstalled) return
    routerHookInstalled = true

    router.beforeEach((to: RouteLocationNormalized) => {
      // 如果目标路由配置了 keepAlive，自动添加到缓存
      if (to.meta?.keepAlive && to.name) {
        addCache(String(to.name))
      }
    })
  }

  return {
    cachedPageNames: cachedNames,
    addCache,
    removeCache,
    clearCache,
    setMaxCache,
    installRouteHook,
  }
}
