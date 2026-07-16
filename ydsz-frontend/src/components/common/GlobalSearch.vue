<!--
  @fileoverview 命令面板（Ctrl+K 唤起）
  @description P1-2: 从 GlobalSearch 升级为 Command Palette：
  - 空关键词：展示最近访问 + 快捷操作命令
  - 有关键词：命令 + 菜单导航 + 统一全文检索（项目/合同/审批/工单/人员/知识库）
  - 键盘导航：↑↓ 切换、Enter 打开、Esc 关闭
  - 打开页面后自动记录到最近访问
  - 最近使用命令 localStorage 持久化
  - 数据来源: @/api/search、@/api/favorite
  @module components/common/GlobalSearch
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Search,
  Clock,
  Folder,
  Document,
  Loading,
  ArrowRight,
  Tickets,
  Connection,
  Avatar,
  Collection,
  Checked,
  Sunny,
  Moon,
  Fold,
  Expand,
  Refresh,
  Plus,
  Bell,
  List,
} from '@element-plus/icons-vue'
import { useGlobalSearch } from '@/composables/useGlobalSearch'
import { usePermissionStore } from '@/store/modules/permission'
import { useAppStore } from '@/store/modules/app'
import {
  searchProjects,
  searchAll,
  type ProjectSearchDoc,
  type UniversalSearchDoc,
  type SearchEntityType,
} from '@/api/search'
import { getRecentAccess, recordAccess, type RecentAccessVO } from '@/api/favorite'
import { logger } from '@/utils/logger'
import type { RouteRecordRaw } from 'vue-router'

const { visible, close } = useGlobalSearch()
const router = useRouter()
const { t } = useI18n()
const permissionStore = usePermissionStore()
const appStore = useAppStore()

/** 搜索关键词 */
const keyword = ref('')
/** 原生 input ref（自动聚焦） */
const inputRef = ref<HTMLInputElement>()
/** 当前选中项索引（基于扁平结果列表） */
const activeIndex = ref(0)
/** 统一搜索结果 */
const universalResults = ref<UniversalSearchDoc[]>([])
/** 项目搜索结果（兼容旧端点降级） */
const projectResults = ref<ProjectSearchDoc[]>([])
/** 搜索加载中 */
const searchLoading = ref(false)
/** 最近访问记录 */
const recentAccess = ref<RecentAccessVO[]>([])

// ===========================================
// 一、菜单项提取与过滤
// ===========================================

/** 菜单导航项 */
interface MenuItem {
  type: 'menu'
  title: string
  path: string
}
/** 最近访问项 */
interface RecentItem {
  type: 'recent'
  title: string
  path: string
}
/** 实体搜索结果项 */
interface EntityItem {
  type: 'entity'
  entityType: SearchEntityType
  title: string
  subtitle: string
  path: string
}
/** P1-2: 快捷命令项 */
interface CommandItem {
  type: 'command'
  id: string
  title: string
  subtitle?: string
  icon: typeof Folder
  keywords: string
  handler: () => void
}

type SearchItem = MenuItem | RecentItem | EntityItem | CommandItem

/** 实体类型 → 图标组件 映射 */
const entityIconMap: Record<SearchEntityType, typeof Folder> = {
  project: Folder,
  contract: Document,
  approval: Checked,
  ticket: Tickets,
  employee: Avatar,
  knowledge: Collection,
}

/** 实体类型 → 分组标签 i18n key */
const entityLabelMap: Record<SearchEntityType, string> = {
  project: 'common.globalSearch.groupProject',
  contract: 'common.globalSearch.groupContract',
  approval: 'common.globalSearch.groupApproval',
  ticket: 'common.globalSearch.groupTicket',
  employee: 'common.globalSearch.groupEmployee',
  knowledge: 'common.globalSearch.groupKnowledge',
}

/**
 * 递归从路由树提取可搜索的叶子菜单
 *
 * @param routes    路由数组
 * @param parentPath 父级路径（用于拼接子路由相对路径）
 * @returns 可搜索菜单项列表
 */
function extractMenus(routes: RouteRecordRaw[], parentPath = ''): MenuItem[] {
  const items: MenuItem[] = []
  for (const r of routes) {
    if (r.meta?.hidden) continue
    const rawPath = r.path.startsWith('/') ? r.path : `${parentPath}/${r.path}`
    const fullPath = rawPath.replace(/\/+/g, '/')
    // 跳过非业务路由
    if (fullPath === '/login' || fullPath === '/' || fullPath.startsWith('/redirect')) continue
    if (r.children && r.children.length > 0) {
      items.push(...extractMenus(r.children, fullPath))
    } else if (r.meta?.title) {
      items.push({ type: 'menu', title: String(r.meta.title), path: fullPath })
    }
  }
  return items
}

/** 所有可搜索菜单（缓存 computed） */
const allMenus = computed(() => extractMenus(permissionStore.routes))

/** 关键词过滤后的菜单结果（最多 8 条） */
const menuResults = computed<MenuItem[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return []
  return allMenus.value
    .filter((m) => m.title.toLowerCase().includes(kw))
    .slice(0, 8)
})

/** 统一搜索结果转换为 EntityItem */
const entityItems = computed<EntityItem[]>(() =>
  universalResults.value.map((d) => ({
    type: 'entity',
    entityType: d.type,
    title: d.title,
    subtitle: d.subtitle,
    path: d.path,
  })),
)

/** 兼容降级：如果统一搜索返回空但项目搜索有结果 */
const fallbackProjectItems = computed<EntityItem[]>(() => {
  if (universalResults.value.length > 0) return []
  return projectResults.value.map((p) => ({
    type: 'entity' as const,
    entityType: 'project' as SearchEntityType,
    title: p.projectName,
    subtitle: [p.customerName, p.pmName].filter(Boolean).join(' · '),
    path: `/initiation?highlight=${p.id}`,
  }))
})

/** 合并后的实体搜索结果 */
const allEntityItems = computed<EntityItem[]>(() => [
  ...entityItems.value,
  ...fallbackProjectItems.value,
])

// ===========================================
// P1-2: 快捷命令定义
// ===========================================

/** 最近使用命令存储 key */
const RECENT_CMDS_KEY = 'command_palette_recent_cmds'
/** 最近使用命令 ID 列表（最多 5 条） */
const recentCmdIds = ref<string[]>([])

function loadRecentCmds() {
  try {
    const raw = localStorage.getItem(RECENT_CMDS_KEY)
    if (raw) recentCmdIds.value = JSON.parse(raw).slice(0, 5)
  } catch { /* 静默失败 */ }
}

function saveRecentCmd(id: string) {
  recentCmdIds.value = [id, ...recentCmdIds.value.filter((c) => c !== id)].slice(0, 5)
  try {
    localStorage.setItem(RECENT_CMDS_KEY, JSON.stringify(recentCmdIds.value))
  } catch { /* 静默失败 */ }
}

/** 所有预定义快捷命令 */
const allCommands = computed<CommandItem[]>(() => [
  {
    id: 'cmd:toggle-theme',
    type: 'command',
    title: t('common.commandPalette.toggleTheme'),
    subtitle: t('common.commandPalette.toggleThemeDesc'),
    icon: appStore.theme === 'dark' ? Sunny : Moon,
    keywords: 'theme dark light 主题 暗黑 明亮 切换',
    handler: () => appStore.toggleTheme(),
  },
  {
    id: 'cmd:toggle-sidebar',
    type: 'command',
    title: t('common.commandPalette.toggleSidebar'),
    subtitle: t('common.commandPalette.toggleSidebarDesc'),
    icon: appStore.sidebarCollapsed ? Expand : Fold,
    keywords: 'sidebar collapse expand 侧边栏 折叠 展开',
    handler: () => appStore.toggleSidebar(),
  },
  {
    id: 'cmd:refresh',
    type: 'command',
    title: t('common.commandPalette.refresh'),
    subtitle: t('common.commandPalette.refreshDesc'),
    icon: Refresh,
    keywords: 'refresh reload 刷新 重新加载',
    handler: () => window.location.reload(),
  },
  {
    id: 'cmd:new-invoice',
    type: 'command',
    title: t('common.commandPalette.newInvoice'),
    subtitle: t('common.commandPalette.newInvoiceDesc'),
    icon: Plus,
    keywords: 'invoice create new 发票 新建 创建',
    handler: () => router.push('/finance/invoice'),
  },
  {
    id: 'cmd:new-contract',
    type: 'command',
    title: t('common.commandPalette.newContract'),
    subtitle: t('common.commandPalette.newContractDesc'),
    icon: Plus,
    keywords: 'contract create new 合同 新建 创建',
    handler: () => router.push('/project/contract'),
  },
  {
    id: 'cmd:new-wbs-task',
    type: 'command',
    title: t('common.commandPalette.newWbsTask'),
    subtitle: t('common.commandPalette.newWbsTaskDesc'),
    icon: Plus,
    keywords: 'wbs task create new 任务 新建 创建',
    handler: () => router.push('/execution/wbs-task'),
  },
  {
    id: 'cmd:approval-center',
    type: 'command',
    title: t('common.commandPalette.approvalCenter'),
    subtitle: t('common.commandPalette.approvalCenterDesc'),
    icon: Bell,
    keywords: 'approval todo task center 审批 待办 中心',
    handler: () => router.push('/workflow/approval-center'),
  },
  {
    id: 'cmd:my-todos',
    type: 'command',
    title: t('common.commandPalette.myTodos'),
    subtitle: t('common.commandPalette.myTodosDesc'),
    icon: List,
    keywords: 'todo task my 我的 待办 任务',
    handler: () => router.push('/workflow/approval-center'),
  },
])

/** 关键词过滤后的命令结果 */
const commandResults = computed<CommandItem[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return []
  return allCommands.value
    .filter((c) =>
      c.title.toLowerCase().includes(kw) ||
      c.keywords.toLowerCase().includes(kw),
    )
    .slice(0, 5)
})

/** 空关键词时展示最近使用的命令 */
const recentCommands = computed<CommandItem[]>(() => {
  if (keyword.value.trim()) return []
  return recentCmdIds.value
    .map((id) => allCommands.value.find((c) => c.id === id))
    .filter((c): c is CommandItem => !!c)
    .slice(0, 5)
})

// ===========================================
// 二、分组结果与扁平索引
// ===========================================

/** 分组结果（用于渲染） */
interface ResultGroup {
  label: string
  items: SearchItem[]
}

/** 按实体类型分组 */
const entityGroups = computed<{ label: string; items: EntityItem[] }[]>(() => {
  const groups: { label: string; items: EntityItem[] }[] = []
  const allItems = allEntityItems.value
  const typeOrder: SearchEntityType[] = ['project', 'contract', 'approval', 'ticket', 'employee', 'knowledge']

  for (const et of typeOrder) {
    const items = allItems.filter((i) => i.entityType === et)
    if (items.length > 0) {
      groups.push({ label: t(entityLabelMap[et]), items })
    }
  }
  return groups
})

/** 分组后的结果列表 */
const groupedResults = computed<ResultGroup[]>(() => {
  const groups: ResultGroup[] = []
  const kw = keyword.value.trim()

  if (!kw) {
    // 空关键词：展示最近访问 + 最近命令
    const recent: RecentItem[] = recentAccess.value.slice(0, 6).map((r) => ({
      type: 'recent',
      title: r.title,
      path: r.path,
    }))
    if (recent.length) {
      groups.push({ label: t('common.globalSearch.groupRecent'), items: recent })
    }
    // P1-2: 最近使用命令
    if (recentCommands.value.length) {
      groups.push({ label: t('common.commandPalette.groupRecentCommands'), items: recentCommands.value })
    }
    // P1-2: 全部快捷命令（空关键词时展示）
    if (allCommands.value.length) {
      groups.push({ label: t('common.commandPalette.groupQuickActions'), items: allCommands.value })
    }
  } else {
    // 有关键词：命令 + 菜单 + 实体搜索
    if (commandResults.value.length) {
      groups.push({ label: t('common.commandPalette.groupQuickActions'), items: commandResults.value })
    }
    if (menuResults.value.length) {
      groups.push({ label: t('common.globalSearch.groupMenu'), items: menuResults.value })
    }
    for (const eg of entityGroups.value) {
      groups.push({ label: eg.label, items: eg.items })
    }
  }
  return groups
})

/** 扁平结果列表（用于键盘导航索引） */
const flatResults = computed<SearchItem[]>(() =>
  groupedResults.value.flatMap((g) => g.items),
)

/** 是否有结果（含 loading） */
const hasContent = computed(
  () => groupedResults.value.length > 0 || searchLoading.value,
)

/**
 * 计算分组内某项在扁平列表中的全局索引
 *
 * @param gi 分组索引
 * @param ii 分组内项索引
 * @returns 扁平列表中的全局索引
 */
function flatIndex(gi: number, ii: number): number {
  let idx = 0
  for (let i = 0; i < gi; i++) {
    idx += groupedResults.value[i].items.length
  }
  return idx + ii
}

// ===========================================
// 三、搜索（防抖 + 统一搜索优先 + 项目搜索降级）
// ===========================================

let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = val.trim()
  if (!kw) {
    universalResults.value = []
    projectResults.value = []
    searchLoading.value = false
    return
  }
  searchLoading.value = true
  searchTimer = setTimeout(async () => {
    try {
      // 优先调用统一搜索端点
      const res = await searchAll(kw, 5)
      const data = res?.data
      if (data && Array.isArray(data) && data.length > 0) {
        universalResults.value = data
        projectResults.value = []
      } else {
        // 降级：统一搜索无结果时调用项目搜索
        universalResults.value = []
        try {
          const pres = await searchProjects(kw, 1, 5)
          projectResults.value = pres?.data?.records ?? []
        } catch {
          projectResults.value = []
        }
      }
    } catch (e) {
      logger.warn('[GlobalSearch]', '统一搜索失败，降级为项目搜索', e)
      // 降级为项目搜索
      try {
        const pres = await searchProjects(kw, 1, 5)
        projectResults.value = pres?.data?.records ?? []
        universalResults.value = []
      } catch {
        universalResults.value = []
        projectResults.value = []
      }
    } finally {
      searchLoading.value = false
    }
  }, 300)
})

// 关键词或结果变化时重置选中索引
watch(flatResults, () => {
  activeIndex.value = 0
})

// ===========================================
// 四、弹窗打开/关闭
// ===========================================

watch(visible, async (val) => {
  if (val) {
    keyword.value = ''
    activeIndex.value = 0
    universalResults.value = []
    projectResults.value = []
    searchLoading.value = false
    loadRecentCmds()
    await nextTick()
    inputRef.value?.focus()
    // 加载最近访问
    try {
      const res = await getRecentAccess()
      recentAccess.value = res?.data ?? []
    } catch (e) {
      logger.warn('[GlobalSearch]', '加载最近访问失败', e)
      recentAccess.value = []
    }
  }
})

// ===========================================
// 五、键盘导航
// ===========================================

/** 键盘事件处理：↑↓ 导航、Enter 打开 */
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, flatResults.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    selectItem(activeIndex.value)
  }
}

/**
 * 选中某项：关闭弹窗 → 路由跳转/执行命令 → 记录访问
 *
 * @param index 扁平列表中的索引
 */
function selectItem(index: number) {
  const item = flatResults.value[index]
  if (!item) return
  close()
  if (item.type === 'command') {
    // P1-2: 执行快捷命令并记录使用
    saveRecentCmd(item.id)
    item.handler()
  } else {
    router.push(item.path).catch(() => {})
    recordAccess(item.path, item.title).catch(() => {})
  }
}

/** 滚动选中项到可视区域 */
function scrollToActive() {
  nextTick(() => {
    const el = document.querySelector('.gs-result-item.active')
    el?.scrollIntoView({ block: 'nearest' })
  })
}
watch(activeIndex, scrollToActive)
</script>

<template>
  <el-dialog
    v-model="visible"
    width="600px"
    top="15vh"
    :show-close="false"
    :close-on-click-modal="true"
    append-to-body
    class="gs-dialog"
  >
    <!-- 搜索输入框 -->
    <template #header>
      <div class="gs-input-wrapper">
        <el-icon class="gs-input-icon"><Search /></el-icon>
        <input
          ref="inputRef"
          v-model="keyword"
          class="gs-input"
          :placeholder="t('common.globalSearch.placeholder')"
          autocomplete="off"
          @keydown="handleKeydown"
        />
      </div>
    </template>

    <!-- 结果列表 -->
    <div class="gs-body">
      <template v-if="hasContent">
        <div
          v-for="(group, gi) in groupedResults"
          :key="gi"
          class="gs-group"
        >
          <div class="gs-group-title">{{ group.label }}</div>
          <div
            v-for="(item, ii) in group.items"
            :key="`${gi}-${ii}`"
            class="gs-result-item"
            :class="{ active: flatIndex(gi, ii) === activeIndex }"
            @click="selectItem(flatIndex(gi, ii))"
            @mouseenter="activeIndex = flatIndex(gi, ii)"
          >
            <el-icon class="gs-item-icon">
              <Clock v-if="item.type === 'recent'" />
              <component
                v-else-if="item.type === 'entity'"
                :is="entityIconMap[item.entityType] || Connection"
              />
              <component
                v-else-if="item.type === 'command'"
                :is="item.icon"
              />
              <Document v-else />
            </el-icon>
            <div class="gs-item-content">
              <span class="gs-item-title">{{ item.title }}</span>
              <span
                v-if="(item.type === 'entity' || item.type === 'command') && item.subtitle"
                class="gs-item-subtitle"
              >
                {{ item.subtitle }}
              </span>
            </div>
            <el-icon v-if="item.type !== 'recent'" class="gs-item-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <!-- 搜索 loading 占位 -->
        <div v-if="searchLoading && allEntityItems.length === 0" class="gs-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ t('common.globalSearch.searching') }}</span>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-else class="gs-empty">
        {{ t('common.globalSearch.noResults') }}
      </div>
    </div>

    <!-- 底部快捷键提示 -->
    <template #footer>
      <div class="gs-footer">
        <span class="gs-hint">
          <kbd>↑</kbd><kbd>↓</kbd>{{ t('common.globalSearch.hintNavigate') }}
        </span>
        <span class="gs-hint"><kbd>Enter</kbd>{{ t('common.globalSearch.hintEnter') }}</span>
        <span class="gs-hint"><kbd>Esc</kbd>{{ t('common.globalSearch.hintEsc') }}</span>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.gs-dialog {
  :deep(.el-dialog__header) {
    padding: 0;
    margin-right: 0;
    border-bottom: 1px solid var(--el-border-color-light);
  }
  :deep(.el-dialog__body) {
    padding: 0;
    max-height: 400px;
    overflow-y: auto;
  }
  :deep(.el-dialog__footer) {
    padding: $spacing-sm $spacing-base;
    border-top: 1px solid var(--el-border-color-light);
  }
}

.gs-input-wrapper {
  display: flex;
  align-items: center;
  padding: $spacing-base $spacing-md;
  gap: $spacing-sm;

  .gs-input-icon {
    font-size: 20px;
    color: var(--el-text-color-secondary);
    flex-shrink: 0;
  }

  .gs-input {
    flex: 1;
    border: none;
    outline: none;
    font-size: 16px;
    background: transparent;
    color: var(--el-text-color-primary);

    &::placeholder {
      color: var(--el-text-color-placeholder);
    }
  }
}

.gs-body {
  min-height: 120px;
}

.gs-group {
  padding: $spacing-sm 0;

  .gs-group-title {
    padding: $spacing-xs $spacing-md;
    font-size: $font-size-xs;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
}

.gs-result-item {
  display: flex;
  align-items: center;
  padding: $spacing-sm $spacing-md;
  cursor: pointer;
  gap: $spacing-sm;
  transition: background 0.15s;

  &:hover,
  &.active {
    background: var(--el-fill-color-light);
  }

  .gs-item-icon {
    font-size: 16px;
    color: var(--el-text-color-secondary);
    flex-shrink: 0;
  }

  .gs-item-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
    overflow: hidden;

    .gs-item-title {
      font-size: $font-size-base;
      color: var(--el-text-color-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .gs-item-subtitle {
      font-size: $font-size-xs;
      color: var(--el-text-color-secondary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  .gs-item-arrow {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    flex-shrink: 0;
  }
}

.gs-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-md;
  color: var(--el-text-color-secondary);
  font-size: $font-size-sm;

  .is-loading {
    animation: rotating 1.5s linear infinite;
  }
}

.gs-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl $spacing-md;
  color: var(--el-text-color-placeholder);
  font-size: $font-size-sm;
}

.gs-footer {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  font-size: $font-size-xs;
  color: var(--el-text-color-secondary);

  .gs-hint {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  kbd {
    display: inline-block;
    padding: 2px 6px;
    font-size: $font-size-xs;
    line-height: 1;
    color: var(--el-text-color-primary);
    background: var(--el-fill-color);
    border: 1px solid var(--el-border-color);
    border-radius: 3px;
    font-family: monospace;
  }
}
</style>
