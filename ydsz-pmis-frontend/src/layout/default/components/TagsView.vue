<!--
  @fileoverview 标签页视图
  @description 类 Chrome Tab 的多标签页导航：
  - 进入路由自动添加标签，关闭标签自动切换至相邻标签
  - 标签状态持久化到 localStorage（STORAGE_KEY = 'pmis_tags_view'）
  - 支持 affix 固定标签（首页不可关闭）
  @module layout/default/components/TagsView
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import i18n from '@/locales'

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

const route = useRoute()
const router = useRouter()
/** 已访问的标签列表 */
const tags = ref<TagItem[]>([])

/** localStorage 存储键 */
const STORAGE_KEY = 'pmis_tags_view'

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

/** 持久化标签列表到 localStorage（仅非固定标签） */
function persistTags(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tags.value))
  } catch {
    // 存储空间不足时静默忽略
  }
}

/** 已访问视图（响应式别名） */
const visitedViews = computed(() => tags.value)

/** 添加当前路由到标签列表（去重） */
function addTag(): void {
  const { name } = route
  if (name && !tags.value.some((t) => t.path === route.path)) {
    tags.value.push({
      path: route.path,
      fullPath: route.fullPath,
      name: String(name),
      title: (() => {
        const t = (route.meta?.title as string) || String(name)
        return t.startsWith('route.') ? i18n.global.t(t) : t
      })(),
      affix: !!route.meta?.affix,
    })
    persistTags()
  }
}

/**
 * 关闭指定标签
 * @param tag - 待关闭的标签
 *  - affix 标签不可关闭
 *  - 若关闭的是当前激活标签，自动跳转到最后一个标签或首页
 */
function closeTag(tag: TagItem): void {
  if (tag.affix) return
  const idx = tags.value.findIndex((t) => t.path === tag.path)
  if (idx === -1) return
  tags.value.splice(idx, 1)
  persistTags()
  if (tag.path === route.path) {
    const last = tags.value[tags.value.length - 1]
    router.push(last ? last.fullPath : '/')
  }
}

// 初始化时恢复持久化的标签
restoreTags()

// 路由变化时自动添加标签
watch(route, addTag, { immediate: true })
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
        >
          {{ tag.title }}
        </el-tag>
      </div>
    </el-scrollbar>
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
  }
}
</style>
