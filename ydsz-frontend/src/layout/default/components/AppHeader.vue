<!--
  @fileoverview 顶部导航栏
  @description 后台布局顶部导航栏：
  - 包含侧边栏折叠按钮、面包屑、语言切换、主题切换、全屏、用户下拉菜单
  - 集成了全局搜索入口（Ctrl+K）和通知铃铛
  - 退出登录走二次确认 + userStore.logout
  @module layout/default/components/AppHeader
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import NotificationBell from '@/components/common/NotificationBell.vue'
import { useGlobalSearch } from '@/composables/useGlobalSearch'
import { useResponsive } from '@/composables/useResponsive'

const appStore = useAppStore()
const userStore = useUserStore()
const router = useRouter()
const { t } = useI18n()
const { open: openSearch } = useGlobalSearch()
const { isMobile } = useResponsive()

/** 是否处于全屏状态 */
const isFullscreen = ref(false)

/** 退出登录：二次确认后调用 userStore.logout 并跳登录页 */
async function handleLogout() {
  try {
    await ElMessageBox.confirm(t('common.confirmLogout'), t('common.tip'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await userStore.logout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}

/** 切换 light/dark 主题 */
function handleToggleTheme() {
  appStore.toggleTheme()
}

/** 切换全屏/退出全屏 */
function handleToggleFullscreen(): void {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {
      // 浏览器不支持或用户拒绝时静默忽略
    })
  } else {
    document.exitFullscreen().catch(() => {
      // 退出全屏失败时静默忽略
    })
  }
}

/** 监听全屏状态变化（支持 ESC 退出后同步按钮状态） */
function handleFullscreenChange(): void {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
})
</script>

<template>
  <header class="app-header" :aria-label="t('common.aria.appHeader')">
    <div class="header-left">
      <el-button text :aria-label="appStore.sidebarCollapsed ? t('common.aria.expandSidebar') : t('common.aria.collapseSidebar')" @click="appStore.toggleSidebar()">
        <el-icon :size="20">
          <Fold v-if="!appStore.sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </el-button>
      <button v-if="!isMobile" class="search-trigger" :title="t('common.globalSearch.title')" :aria-label="t('common.aria.globalSearch')" @click="openSearch">
        <el-icon :size="14"><Search /></el-icon>
        <span class="search-text">{{ t('common.globalSearch.shortcut') }}</span>
      </button>
      <Breadcrumb v-if="!isMobile" />
    </div>
    <div class="header-right">
      <NotificationBell />
      <LanguageSwitcher />
      <el-tooltip :content="t('common.theme')">
        <el-button text :aria-label="t('common.aria.toggleTheme')" @click="handleToggleTheme">
          <el-icon :size="18">
            <Sunny v-if="appStore.theme === 'light'" />
            <Moon v-else />
          </el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="isFullscreen ? t('common.exitFullscreen') : t('common.fullscreen')">
        <el-button text :aria-label="isFullscreen ? t('common.aria.exitFullscreen') : t('common.aria.enterFullscreen')" @click="handleToggleFullscreen">
          <el-icon :size="18">
            <Aim v-if="isFullscreen" />
            <FullScreen v-else />
          </el-icon>
        </el-button>
      </el-tooltip>
      <el-dropdown trigger="click" :aria-label="t('common.aria.userMenu')">
        <div class="user-info">
          <el-avatar :size="32" :src="userStore.userInfo?.avatar">
            {{ userStore.realName?.charAt(0) || 'U' }}
          </el-avatar>
          <span class="user-name">{{ userStore.realName || userStore.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/profile/security')">
              <el-icon><Lock /></el-icon>{{ t('common.securitySettings') }}
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>{{ t('common.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style lang="scss" scoped>
.app-header {
  height: $header-height;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 $spacing-md;
  background: $bg-white;
  border-bottom: 1px solid $border-extra-light;

  .header-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;

    .search-trigger {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border: 1px solid $border-extra-light;
      border-radius: 4px;
      background: $bg-page;
      color: $text-secondary;
      font-size: $font-size-xs;
      cursor: pointer;
      transition: all 0.2s;
      white-space: nowrap;

      &:hover {
        border-color: $border-light;
        color: $text-primary;
      }

      .search-text {
        font-family: monospace;
        opacity: 0.8;
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: $spacing-base;
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    cursor: pointer;
    padding: 0 $spacing-sm;

    .user-name {
      font-size: $font-size-base;
      color: $text-primary;
    }
  }

  // 移动端适配
  @media (max-width: $breakpoint-sm) {
    padding: 0 $spacing-sm;

    .header-right {
      gap: $spacing-xs;
    }

    .user-info .user-name {
      display: none;
    }
  }
}
</style>
