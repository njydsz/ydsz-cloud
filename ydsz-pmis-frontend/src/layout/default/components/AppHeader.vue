<script setup lang="ts">
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

const appStore = useAppStore()
const userStore = useUserStore()
const router = useRouter()

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userStore.logout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}

function handleToggleTheme() {
  appStore.toggleTheme()
}
</script>

<template>
  <header class="app-header">
    <div class="header-left">
      <el-button text @click="appStore.toggleSidebar()">
        <el-icon :size="20">
          <Fold v-if="!appStore.sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </el-button>
      <Breadcrumb />
    </div>
    <div class="header-right">
      <el-tooltip content="主题切换">
        <el-button text @click="handleToggleTheme">
          <el-icon :size="18">
            <Sunny v-if="appStore.theme === 'light'" />
            <Moon v-else />
          </el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip content="全屏">
        <el-button text>
          <el-icon :size="18"><FullScreen /></el-icon>
        </el-button>
      </el-tooltip>
      <el-dropdown trigger="click">
        <div class="user-info">
          <el-avatar :size="32" :src="userStore.userInfo?.avatar">
            {{ userStore.realName?.charAt(0) || 'U' }}
          </el-avatar>
          <span class="user-name">{{ userStore.realName || userStore.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/profile/security')">
              <el-icon><Lock /></el-icon>安全设置
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>退出登录
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
}
</style>
