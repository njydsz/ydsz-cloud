<!--
  @file 默认布局
  @description 后台主框架布局：左侧 Sidebar + 顶部 AppHeader + TagsView + MainContent（RouterView）
  @module layout/default
-->
<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import AppHeader from './components/AppHeader.vue'
import TagsView from './components/TagsView.vue'
import MainContent from './components/MainContent.vue'
import { useAppStore } from '@/store/modules/app'

const appStore = useAppStore()
</script>

<template>
  <div class="default-layout" :class="{ collapsed: appStore.sidebarCollapsed }">
    <aside class="layout-sidebar">
      <Sidebar />
    </aside>
    <section class="layout-container">
      <AppHeader />
      <div class="layout-tags">
        <TagsView />
      </div>
      <MainContent>
        <RouterView v-slot="{ Component, route }">
          <Transition name="route" mode="out-in">
            <KeepAlive>
              <component :is="Component" :key="route.fullPath" />
            </KeepAlive>
          </Transition>
        </RouterView>
      </MainContent>
    </section>
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
