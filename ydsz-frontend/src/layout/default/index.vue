<!--
  @fileoverview 默认布局
  @description 后台主框架布局：
  - 左侧 Sidebar + 顶部 AppHeader + TagsView + MainContent
  - 监听 Ctrl+K / Cmd+K 唤起全局搜索
  - 响应式：小屏自动收起侧边栏
  - MainContent 内部由 ErrorBoundary 包裹，捕获渲染异常
  @module layout/default
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { watch } from 'vue'
import { useMagicKeys } from '@vueuse/core'
import Sidebar from './components/Sidebar.vue'
import AppHeader from './components/AppHeader.vue'
import TagsView from './components/TagsView.vue'
import MainContent from './components/MainContent.vue'
import RouteSkeleton from './components/RouteSkeleton.vue'
import GlobalSearch from '@/components/common/GlobalSearch.vue'
import ErrorBoundary from '@/components/common/ErrorBoundary.vue'
import PasswordExpiryBanner from '@/components/common/PasswordExpiryBanner.vue'
import { useAppStore } from '@/store/modules/app'
import { useGlobalSearch } from '@/composables/useGlobalSearch'
import { useResponsive } from '@/composables/useResponsive'
import { useDevWarmup } from '@/composables/useDevWarmup'
import { logger } from '@/utils/logger'

const appStore = useAppStore()
const { open } = useGlobalSearch()

// 开发环境自动预热
useDevWarmup()

// 响应式：小屏幕时自动收起侧边栏
const { isMobile } = useResponsive()
watch(isMobile, (mobile) => {
  if (mobile && !appStore.sidebarCollapsed) {
    appStore.toggleSidebar()
  }
}, { immediate: true })

// Ctrl+K / Cmd+K 快捷键唤起全局搜索
const { Ctrl_K, Meta_K } = useMagicKeys()
watch([Ctrl_K, Meta_K], ([ctrlK, metaK]) => {
  if (ctrlK || metaK) open()
})

/**
 * ErrorBoundary 捕获页面级渲染异常时的回调
 * ErrorBoundary 内部已处理 Sentry 上报，此处仅做开发环境日志输出
 */
function onError(err: unknown, info: string) {
  logger.error('[Layout ErrorBoundary]', err, { info })
}
</script>

<template>
  <div class="default-layout" :class="{ collapsed: appStore.sidebarCollapsed }">
    <aside class="layout-sidebar" aria-label="侧边栏导航">
      <Sidebar />
    </aside>
    <section class="layout-container" aria-label="主内容区域">
      <AppHeader />
      <PasswordExpiryBanner />
      <div class="layout-tags">
        <TagsView />
      </div>
      <MainContent>
        <ErrorBoundary @error="onError">
          <RouterView v-slot="{ Component, route }">
            <Transition name="route" mode="out-in">
              <KeepAlive>
                <Suspense>
                  <component :is="Component" :key="route.fullPath" />
                  <template #fallback>
                    <RouteSkeleton />
                  </template>
                </Suspense>
              </KeepAlive>
            </Transition>
          </RouterView>
        </ErrorBoundary>
      </MainContent>
    </section>
    <GlobalSearch />
  </div>
</template>

<style lang="scss" scoped>
.default-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;

  .layout-sidebar {
    width: $sidebar-width;
    flex-shrink: 0;
    background: $bg-white;
    border-right: 1px solid $border-extra-light;
    transition: width 0.3s;
  }

  &.collapsed .layout-sidebar {
    width: $sidebar-collapse-width;
  }

  .layout-container {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .layout-tags {
      height: $tags-view-height;
      background: $bg-white;
      border-bottom: 1px solid $border-extra-light;
    }
  }
}
</style>
