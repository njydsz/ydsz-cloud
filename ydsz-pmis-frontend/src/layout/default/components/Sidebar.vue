<!--
  @file 侧边栏菜单
  @description 基于 permissionStore.sidebarRoutes 渲染 el-menu，支持折叠/展开与子菜单嵌套
  @module layout/default/components/Sidebar
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import { usePermissionStore } from '@/store/modules/permission'
import i18n from '@/locales'
import type { RouteRecordRaw } from 'vue-router'

const route = useRoute()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

/** 菜单项数据结构 */
interface MenuItem {
  /** 路由路径 */
  path: string
  /** 菜单标题 */
  title: string
  /** 图标名（Element Plus 图标） */
  icon?: string
  /** 子菜单 */
  children?: MenuItem[]
}

/**
 * 将 RouteRecordRaw 转换为菜单项
 * @param routes - 路由记录
 * @returns 过滤 hidden 后的菜单项列表
 */
function convertRoutes(routes: RouteRecordRaw[]): MenuItem[] {
  return routes
    .filter((r) => !r.meta?.hidden)
    .map((r) => {
      const rawTitle = (r.meta?.title as string) || ''
      return {
        path: r.path,
        title: rawTitle.startsWith('route.') ? i18n.global.t(rawTitle) : rawTitle,
        icon: (r.meta?.icon as string) || '',
        children: r.children ? convertRoutes(r.children) : undefined,
      }
    })
}

/** 当前可见菜单（响应式，路由变化时自动更新） */
const menus = computed(() => convertRoutes(permissionStore.sidebarRoutes as RouteRecordRaw[]))

/** 当前激活菜单（支持 meta.activeMenu 自定义高亮） */
const activeMenu = computed<string>(() => {
  const meta = route.meta as { activeMenu?: string } | undefined
  return meta?.activeMenu || route.path
})
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
