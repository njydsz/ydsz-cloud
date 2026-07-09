<!--
  @fileoverview 标签页视图（批次 30-1 增强）
  @description 类 Chrome Tab 的多标签页导航：
  - 进入路由自动添加标签，关闭标签自动切换至相邻标签
  - 标签状态持久化到 localStorage（STORAGE_KEY = 'pmis_tags_view'）
  - 支持 affix 固定标签（首页不可关闭）
  - 批次 30-1：右键菜单（关闭其他/关闭全部/刷新当前）+ 最大标签数限制
  @module layout/default/components/TagsView
  @author ydsz-pmis-team
  @since 1.4.0
-->
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Refresh, Close, CircleClose } from '@element-plus/icons-vue'

/** 标签项数据结构 */
interface TagItem {
  /** 路由 path（去 query 后的路径，用作唯一 key） */
  path: string
  /** 完整路径（含 query/hash，用于点击跳回原页面） */
  fullPath: string
  /** 路由 name */
  name: string
  /** 标签标题 */
  title: string
  /** 是否固定（不可关闭，如首页） */
  affix?: boolean
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
/** 已访问的标签列表 */
const tags = ref<TagItem[]>([])

/** localStorage 存储键 */
const STORAGE_KEY = 'pmis_tags_view'

/** 最大标签数（超出时自动关闭最早的非 affix 标签） */
const MAX_TAGS = 15

/** 从 localStorage 恢复标签列表 */
function restoreTags(): void {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const parsed = JSON.parse(stored) as TagItem[]
      if (Array.isArray(parsed) && parsed.length > 0) {
        tags.value = parsed
      }
    }
  } catch {
    // localStorage 读取失败时静默忽略
  }
}

/** 持久化标签列表到 localStorage */
function persistTags(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tags.value))
  } catch {
    // 存储空间不足时静默忽略
  }
}

/** 已访问视图（响应式别名） */
const visitedViews = computed(() => tags.value)

/** 添加当前路由到标签列表（去重 + 最大数量限制） */
function addTag(): void {
  const { name } = route
  if (!name) return
  if (tags.value.some((tag) => tag.path === route.path)) return

  tags.value.push({
    path: route.path,
    fullPath: route.fullPath,
    name: String(name),
    title: (() => {
      const raw = (route.meta?.title as string) || String(name)
      return raw.startsWith('route.') ? t(raw) : raw
    })(),
    affix: !!route.meta?.affix,
  })

  // 超过最大标签数时，关闭最早的非 affix 标签
  const nonAffixTags = tags.value.filter((tag) => !tag.affix)
  if (nonAffixTags.length > MAX_TAGS) {
    const oldest = nonAffixTags[0]
    const idx = tags.value.findIndex((tag) => tag.path === oldest.path)
    if (idx > -1) {
      tags.value.splice(idx, 1)
    }
  }

  persistTags()
}

/**
 * 关闭指定标签
 * @param tag - 待关闭的标签
 *  - affix 标签不可关闭
 *  - 若关闭的是当前激活标签，自动跳转到最后一个标签或首页
 */
function closeTag(tag: TagItem): void {
  if (tag.affix) return
  const idx = tags.value.findIndex((item) => item.path === tag.path)
  if (idx === -1) return
  tags.value.splice(idx, 1)
  persistTags()
  if (tag.path === route.path) {
    const last = tags.value[tags.value.length - 1]
    router.push(last ? last.fullPath : '/')
  }
}

/** 关闭其他标签（保留 affix 标签和当前标签） */
function closeOtherTags(): void {
  const currentPath = route.path
  tags.value = tags.value.filter((tag) => tag.affix || tag.path === currentPath)
  persistTags()
  hideContextMenu()
}

/** 关闭全部标签（保留 affix 标签，跳转到首页） */
function closeAllTags(): void {
  tags.value = tags.value.filter((tag) => tag.affix)
  persistTags()
  const last = tags.value[tags.value.length - 1]
  router.push(last ? last.fullPath : '/')
  hideContextMenu()
}

/** 刷新当前标签（通过 redirect 路由实现组件重载） */
function refreshCurrentTag(): void {
  const currentFullPath = route.fullPath
  // 先跳转到 redirect 路由，再跳回来触发组件重建
  router.replace({ path: '/redirect' + currentFullPath })
  hideContextMenu()
}

// ===== 右键菜单（批次 30-1） =====
/** 右键菜单是否可见 */
const contextMenuVisible = ref(false)
/** 右键菜单位置 */
const contextMenuX = ref(0)
/** 右键菜单位置 */
const contextMenuY = ref(0)

/** 显示右键菜单 */
function showContextMenu(event: MouseEvent): void {
  event.preventDefault()
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuVisible.value = true
}

/** 隐藏右键菜单 */
function hideContextMenu(): void {
  contextMenuVisible.value = false
}

/** 点击页面其他位置时隐藏右键菜单 */
function onDocumentClick(): void {
  hideContextMenu()
}

/** 右键菜单项是否禁用 */
const contextMenuDisabled = computed(() => ({
  // 没有可关闭的其他标签时禁用"关闭其他"
  closeOthers: tags.value.filter((tag) => !tag.affix && tag.path !== route.path).length === 0,
  // 没有可关闭的标签时禁用"关闭全部"
  closeAll: tags.value.filter((tag) => !tag.affix).length === 0,
}))

// 初始化时恢复持久化的标签
restoreTags()

// 路由变化时自动添加标签
watch(route, addTag, { immediate: true })

// 组件挂载后监听全局点击事件以关闭右键菜单
onMounted(() => {
  document.addEventListener('click', onDocumentClick)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
})
</script>

<template>
  <div class="tags-view-wrap">
    <el-scrollbar>
      <div class="tags-list">
        <el-tag
          v-for="tag in visitedViews"
          :key="tag.path"
          :closable="!tag.affix"
          :effect="route.path === tag.path ? 'dark' : 'light'"
          :type="route.path === tag.path ? 'primary' : 'info'"
          class="tag-item"
          @click="router.push(tag.fullPath)"
          @close="closeTag(tag)"
          @contextmenu="showContextMenu($event)"
        >
          {{ tag.title }}
        </el-tag>
      </div>
    </el-scrollbar>

    <!-- 右键菜单（批次 30-1） -->
    <Teleport to="body">
      <div
        v-if="contextMenuVisible"
        class="tags-context-menu"
        :style="{ left: contextMenuX + 'px', top: contextMenuY + 'px' }"
        @click.stop
      >
        <div
          class="tags-context-menu__item"
          @click="refreshCurrentTag"
        >
          <el-icon><Refresh /></el-icon>
          <span>{{ t('common.refreshCurrent') }}</span>
        </div>
        <div
          class="tags-context-menu__item"
          :class="{ 'is-disabled': contextMenuDisabled.closeOthers }"
          @click="!contextMenuDisabled.closeOthers && closeOtherTags()"
        >
          <el-icon><Close /></el-icon>
          <span>{{ t('common.closeOthers') }}</span>
        </div>
        <div
          class="tags-context-menu__item"
          :class="{ 'is-disabled': contextMenuDisabled.closeAll }"
          @click="!contextMenuDisabled.closeAll && closeAllTags()"
        >
          <el-icon><CircleClose /></el-icon>
          <span>{{ t('common.closeAll') }}</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style lang="scss" scoped>
.tags-view-wrap {
  height: $tags-view-height;
  display: flex;
  align-items: center;
  padding: 0 $spacing-sm;

  .tags-list {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
  }

  .tag-item {
    cursor: pointer;
    user-select: none;
  }
}

/* 右键菜单（批次 30-1） */
.tags-context-menu {
  position: fixed;
  z-index: $z-index-modal + 1;
  min-width: 160px;
  padding: $spacing-xs 0;
  background: $bg-white;
  border-radius: $border-radius-base;
  box-shadow: $box-shadow;
  border: 1px solid $border-lighter;

  &__item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-md;
    cursor: pointer;
    font-size: $font-size-base;
    color: $text-regular;
    transition: background 0.15s;

    &:hover {
      background: $bg-page;
      color: $primary-color;
    }

    &.is-disabled {
      color: $text-placeholder;
      cursor: not-allowed;

      &:hover {
        background: transparent;
        color: $text-placeholder;
      }
    }

    .el-icon {
      font-size: 14px;
    }
  }
}
</style>
