/**
 * @file Dashboard 拖拽布局 composable
 * @description 管理仪表盘小部件的排序、可见性与列宽。
 *              采用 localStorage 即时写入 + 后端 API 防抖同步的双层持久化策略，
 *              实现跨设备布局同步。
 *              支持原生 HTML5 拖拽 API 进行小部件重排序。
 *
 * 功能：
 *  - 小部件显隐切换（用户可自行关闭不需要的卡片）
 *  - 拖拽排序（HTML5 dragstart/dragover/drop）
 *  - 布局持久化（localStorage 即时写入 + 后端 API 防抖同步）
 *  - 一键重置默认布局
 *  - 跨设备布局同步
 *
 * @example
 * ```ts
 * const { widgets, dragStart, dragOver, drop, toggleVisible, resetLayout, isCustomizing } = useDashboardLayout('dashboard')
 * ```
 *
 * @module composables/useDashboardLayout
 * @author ydsz-team
 * @since 1.5.0
 */
import { ref, watch, onMounted, type Ref } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { logger } from '@/utils/logger'
import { request } from '@/utils/request'

/** 小部件定义 */
export interface WidgetConfig {
  /** 唯一标识 */
  id: string
  /** 显示名称（i18n key 或纯文本） */
  title: string
  /** 默认跨列数（1-24） */
  defaultSpan: number
  /** 默认是否可见 */
  defaultVisible: boolean
  /** 是否禁用隐藏（如 KPI 概览通常不可关闭） */
  disableHide?: boolean
}

/** 运行时小部件状态 */
export interface WidgetState extends WidgetConfig {
  /** 当前跨列数 */
  span: number
  /** 当前是否可见 */
  visible: boolean
  /** 当前排序索引 */
  order: number
}

/** localStorage 存储结构 */
interface PersistedLayout {
  [widgetId: string]: {
    span: number
    visible: boolean
    order: number
  }
}

/** 后端同步防抖定时器 */
let syncTimer: ReturnType<typeof setTimeout> | null = null

/** 防抖延迟（毫秒） */
const SYNC_DEBOUNCE_MS = 2000

/**
 * Dashboard 拖拽布局 composable
 *
 * @param storageKey localStorage 存储前缀（如 'dashboard'）
 * @param defaultWidgets 默认小部件配置列表
 * @returns 响应式小部件列表 + 拖拽操作方法
 */
export function useDashboardLayout(
  storageKey: string,
  defaultWidgets: WidgetConfig[],
): {
  /** 响应式小部件状态列表（按 order 排序） */
  widgets: Ref<WidgetState[]>
  /** 是否处于自定义编辑模式 */
  isCustomizing: Ref<boolean>
  /** 拖拽起始索引 */
  dragIndex: Ref<number>
  /** 是否正在同步到后端 */
  isSyncing: Ref<boolean>
  /** 开始拖拽 */
  dragStart: (index: number) => void
  /** 拖拽经过 */
  dragOver: (e: DragEvent, index: number) => void
  /** 放置 */
  drop: (index: number) => void
  /** 切换小部件可见性 */
  toggleVisible: (id: string) => void
  /** 调整小部件列宽 */
  setSpan: (id: string, span: number) => void
  /** 重置为默认布局 */
  resetLayout: () => void
  /** 进入/退出自定义模式 */
  toggleCustomizing: () => void
} {
  const userStore = useUserStore()
  const userId = userStore.userInfo?.id || 'guest'
  const fullKey = `ydsz:dashboard-layout:${storageKey}:${userId}`

  /** 从 localStorage 加载持久化布局 */
  function loadPersisted(): PersistedLayout | null {
    try {
      const raw = localStorage.getItem(fullKey)
      return raw ? JSON.parse(raw) as PersistedLayout : null
    } catch (e) {
      logger.warn('[useDashboardLayout]', '加载布局失败', e)
      return null
    }
  }

  /** 持久化布局到 localStorage */
  function persist(layout: PersistedLayout) {
    try {
      localStorage.setItem(fullKey, JSON.stringify(layout))
    } catch (e) {
      logger.warn('[useDashboardLayout]', '保存布局失败', e)
    }
  }

  /** 从后端加载布局（跨设备同步） */
  async function loadFromBackend(): Promise<PersistedLayout | null> {
    try {
      const { data } = await request<{ layoutConfig: string }>({
        url: `/dashboard/layout/${storageKey}`,
        method: 'GET',
        silent: true,
      })
      if (data?.layoutConfig && data.layoutConfig !== '{}') {
        return JSON.parse(data.layoutConfig) as PersistedLayout
      }
    } catch (e) {
      // 后端加载失败静默处理，使用 localStorage 中的数据
      logger.debug('[useDashboardLayout]', '后端布局加载失败，使用本地数据', e)
    }
    return null
  }

  /** 防抖同步布局到后端 */
  function syncToBackend(layout: PersistedLayout) {
    if (syncTimer) {
      clearTimeout(syncTimer)
    }
    syncTimer = setTimeout(async () => {
      try {
        await request({
          url: `/dashboard/layout/${storageKey}`,
          method: 'PUT',
          data: { layoutConfig: JSON.stringify(layout) },
          silent: true,
        })
        logger.debug('[useDashboardLayout]', '布局已同步到后端')
      } catch (e) {
        logger.debug('[useDashboardLayout]', '后端同步失败，将在下次变更时重试', e)
      }
    }, SYNC_DEBOUNCE_MS)
  }

  /** 初始化小部件状态 */
  function initWidgets(): WidgetState[] {
    const persisted = loadPersisted()
    const states = defaultWidgets.map((w, idx) => {
      const p = persisted?.[w.id]
      return {
        ...w,
        span: p?.span ?? w.defaultSpan,
        visible: p?.visible ?? w.defaultVisible,
        order: p?.order ?? idx,
      }
    })
    return states.sort((a, b) => a.order - b.order)
  }

  const widgets = ref<WidgetState[]>(initWidgets())
  const isCustomizing = ref(false)
  const dragIndex = ref(-1)
  const isSyncing = ref(false)

  /** 持久化当前布局（localStorage 即时 + 后端防抖） */
  function saveLayout() {
    const layout: PersistedLayout = {}
    widgets.value.forEach((w, idx) => {
      layout[w.id] = { span: w.span, visible: w.visible, order: idx }
    })
    persist(layout)
    syncToBackend(layout)
  }

  /** 开始拖拽 */
  function dragStart(index: number) {
    dragIndex.value = index
  }

  /** 拖拽经过 */
  function dragOver(e: DragEvent, _index: number) {
    e.preventDefault()
    e.dataTransfer!.dropEffect = 'move'
  }

  /** 放置：交换位置 */
  function drop(targetIndex: number) {
    const from = dragIndex.value
    if (from < 0 || from === targetIndex) return
    const list = [...widgets.value]
    const [moved] = list.splice(from, 1)
    list.splice(targetIndex, 0, moved)
    // 重新分配 order
    list.forEach((w, idx) => { w.order = idx })
    widgets.value = list
    dragIndex.value = -1
    saveLayout()
  }

  /** 切换小部件可见性 */
  function toggleVisible(id: string) {
    const w = widgets.value.find((w) => w.id === id)
    if (w && !w.disableHide) {
      w.visible = !w.visible
      saveLayout()
    }
  }

  /** 调整小部件列宽 */
  function setSpan(id: string, span: number) {
    const w = widgets.value.find((w) => w.id === id)
    if (w) {
      w.span = Math.max(6, Math.min(24, span))
      saveLayout()
    }
  }

  /** 重置为默认布局 */
  function resetLayout() {
    widgets.value = defaultWidgets.map((w, idx) => ({
      ...w,
      span: w.defaultSpan,
      visible: w.defaultVisible,
      order: idx,
    }))
    saveLayout()
    // 同时清除后端布局
    request({
      url: `/dashboard/layout/${storageKey}`,
      method: 'DELETE',
      silent: true,
    }).catch(() => { /* 静默失败 */ })
  }

  /** 进入/退出自定义模式 */
  function toggleCustomizing() {
    isCustomizing.value = !isCustomizing.value
  }

  // 挂载时尝试从后端加载布局（跨设备同步）
  onMounted(async () => {
    const backendLayout = await loadFromBackend()
    if (backendLayout) {
      // 后端有布局数据，用后端数据覆盖本地
      const states = defaultWidgets.map((w, idx) => {
        const p = backendLayout[w.id]
        return {
          ...w,
          span: p?.span ?? w.defaultSpan,
          visible: p?.visible ?? w.defaultVisible,
          order: p?.order ?? idx,
        }
      })
      widgets.value = states.sort((a, b) => a.order - b.order)
      persist(backendLayout)
    }
  })

  // 自动持久化
  watch(widgets, saveLayout, { deep: true })

  return {
    widgets,
    isCustomizing,
    dragIndex,
    isSyncing,
    dragStart,
    dragOver,
    drop,
    toggleVisible,
    setSpan,
    resetLayout,
    toggleCustomizing,
  }
}

export default useDashboardLayout
