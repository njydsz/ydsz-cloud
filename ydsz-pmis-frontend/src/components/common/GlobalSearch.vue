<!--
  @fileoverview 全局搜索弹窗（Ctrl+K 唤起）
  @description 顶部全局搜索入口：
  - 空关键词：展示最近访问
  - 有关键词：菜单导航本地过滤 + 项目全文检索
  - 键盘导航：↑↓ 切换、Enter 打开、Esc 关闭
  - 打开页面后自动记录到最近访问
  - 数据来源: @/api/search、@/api/favorite
  @module components/common/GlobalSearch
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Search, Clock, Folder, Document, Loading, ArrowRight } from '@element-plus/icons-vue'
import { useGlobalSearch } from '@/composables/useGlobalSearch'
import { usePermissionStore } from '@/store/modules/permission'
import { searchProjects, type ProjectSearchDoc } from '@/api/search'
import { getRecentAccess, recordAccess, type RecentAccessVO } from '@/api/favorite'
import { logger } from '@/utils/logger'
import type { RouteRecordRaw } from 'vue-router'

const { visible, close } = useGlobalSearch()
const router = useRouter()
const { t } = useI18n()
const permissionStore = usePermissionStore()

/** 搜索关键词 */
const keyword = ref('')
/** 原生 input ref（自动聚焦） */
const inputRef = ref<HTMLInputElement>()
/** 当前选中项索引（基于扁平结果列表） */
const activeIndex = ref(0)
/** 项目搜索结果 */
const projectResults = ref<ProjectSearchDoc[]>([])
/** 项目搜索加载中 */
const projectLoading = ref(false)
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
/** 项目搜索结果项 */
interface ProjectItem {
  type: 'project'
  title: string
  subtitle: string
  path: string
}

type SearchItem = MenuItem | RecentItem | ProjectItem

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

/** 项目结果转换为搜索项 */
const projectItems = computed<ProjectItem[]>(() =>
  projectResults.value.map((p) => ({
    type: 'project',
    title: p.projectName,
    subtitle: [p.customerName, p.pmName].filter(Boolean).join(' · '),
    path: `/project/initiation?highlight=${p.id}`,
  })),
)

// ===========================================
// 二、分组结果与扁平索引
// ===========================================

/** 分组结果（用于渲染） */
interface ResultGroup {
  label: string
  items: SearchItem[]
}

/** 分组后的结果列表 */
const groupedResults = computed<ResultGroup[]>(() => {
  const groups: ResultGroup[] = []
  const kw = keyword.value.trim()

  if (!kw) {
    // 空关键词：展示最近访问
    const recent: RecentItem[] = recentAccess.value.slice(0, 6).map((r) => ({
      type: 'recent',
      title: r.title,
      path: r.path,
    }))
    if (recent.length) {
      groups.push({ label: t('common.globalSearch.groupRecent'), items: recent })
    }
  } else {
    // 有关键词：菜单 + 项目
    if (menuResults.value.length) {
      groups.push({ label: t('common.globalSearch.groupMenu'), items: menuResults.value })
    }
    if (projectItems.value.length || projectLoading.value) {
      groups.push({ label: t('common.globalSearch.groupProject'), items: projectItems.value })
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
  () => groupedResults.value.length > 0 || projectLoading.value,
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
// 三、项目搜索（防抖）
// ===========================================

let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = val.trim()
  if (!kw) {
    projectResults.value = []
    projectLoading.value = false
    return
  }
  projectLoading.value = true
  searchTimer = setTimeout(async () => {
    try {
      const res = await searchProjects(kw, 1, 8)
      projectResults.value = res?.data?.records ?? []
    } catch (e) {
      logger.warn('[GlobalSearch]', '项目搜索失败', e)
      projectResults.value = []
    } finally {
      projectLoading.value = false
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
    projectResults.value = []
    projectLoading.value = false
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
 * 选中某项：关闭弹窗 → 路由跳转 → 记录访问
 *
 * @param index 扁平列表中的索引
 */
function selectItem(index: number) {
  const item = flatResults.value[index]
  if (!item) return
  close()
  router.push(item.path).catch(() => {})
  recordAccess(item.path, item.title).catch(() => {})
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
              <Folder v-else-if="item.type === 'project'" />
              <Document v-else />
            </el-icon>
            <div class="gs-item-content">
              <span class="gs-item-title">{{ item.title }}</span>
              <span v-if="item.type === 'project' && item.subtitle" class="gs-item-subtitle">
                {{ item.subtitle }}
              </span>
            </div>
            <el-icon v-if="item.type !== 'recent'" class="gs-item-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <!-- 项目搜索 loading 占位 -->
        <div v-if="projectLoading && projectItems.length === 0" class="gs-loading">
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
