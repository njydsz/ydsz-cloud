<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

interface TagItem {
  path: string
  fullPath: string
  name: string
  title: string
  affix?: boolean
}

const route = useRoute()
const router = useRouter()
const tags = ref<TagItem[]>([])

const visitedViews = computed(() => tags.value)

function addTag(): void {
  const { name } = route
  if (name && !tags.value.some((t) => t.path === route.path)) {
    tags.value.push({
      path: route.path,
      fullPath: route.fullPath,
      name: String(name),
      title: (route.meta?.title as string) || String(name),
      affix: !!route.meta?.affix,
    })
  }
}

function closeTag(tag: TagItem): void {
  if (tag.affix) return
  const idx = tags.value.findIndex((t) => t.path === tag.path)
  if (idx === -1) return
  tags.value.splice(idx, 1)
  if (tag.path === route.path) {
    const last = tags.value[tags.value.length - 1]
    router.push(last ? last.fullPath : '/')
  }
}

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
