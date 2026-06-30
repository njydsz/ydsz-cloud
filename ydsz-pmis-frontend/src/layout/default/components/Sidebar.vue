<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import { usePermissionStore } from '@/store/modules/permission'
import type { RouteRecordRaw } from 'vue-router'

const route = useRoute()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

interface MenuItem {
  path: string
  title: string
  icon?: string
  children?: MenuItem[]
}

function convertRoutes(routes: RouteRecordRaw[]): MenuItem[] {
  return routes
    .filter((r) => !r.meta?.hidden)
    .map((r) => ({
      path: r.path,
      title: (r.meta?.title as string) || '',
      icon: (r.meta?.icon as string) || '',
      children: r.children ? convertRoutes(r.children) : undefined,
    }))
}

const menus = computed(() => convertRoutes(permissionStore.sidebarRoutes as RouteRecordRaw[]))

const activeMenu = computed(() => route.meta?.activeMenu || route.path)
</script>

<template>
  <div class="sidebar-wrap">
    <div class="sidebar-logo">
      <span v-if="!appStore.sidebarCollapsed">PMIS</span>
      <span v-else>P</span>
    </div>
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :unique-opened="true"
        background-color="#ffffff"
        text-color="#303133"
        active-text-color="#1890ff"
        router
      >
        <template v-for="menu in menus" :key="menu.path">
          <!-- 子菜单 -->
          <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.path">
            <template #title>
              <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
              <template #title>{{ child.title }}</template>
            </el-menu-item>
          </el-sub-menu>
          <!-- 单个菜单 -->
          <el-menu-item v-else :index="menu.path">
            <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
            <template #title>{{ menu.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<style lang="scss" scoped>
.sidebar-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;

  .sidebar-logo {
    height: $header-height;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    font-weight: 700;
    color: $primary-color;
    border-bottom: 1px solid $border-extra-light;
  }

  :deep(.el-menu) {
    border-right: none;
  }
}
</style>
