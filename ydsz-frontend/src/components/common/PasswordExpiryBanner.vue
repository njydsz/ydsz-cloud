<!--
  @fileoverview 密码过期预警横幅组件
  @description 当用户密码即将过期或已过期时，在页面顶部展示预警横幅。
  - 根据过期状态自动切换 alert 类型（error/warning/info）
  - 提供"立即修改"按钮跳转至安全设置页
  @module components/common/PasswordExpiryBanner
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 密码过期预警横幅
 *
 * 在页面顶部以 el-alert 形式展示密码过期/即将过期的预警信息，
 * 根据过期状态自动切换 alert 类型，并提供"立即修改"按钮。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePasswordExpiry } from '@/composables/usePasswordExpiry'

const router = useRouter()
const { passwordStatus, showWarning } = usePasswordExpiry()

/** 根据过期状态返回对应 alert 类型：EXPIRED/INITIAL=error，EXPIRING_SOON=warning，其他=info */
const alertType = computed(() => {
  const status = passwordStatus.value?.status
  if (status === 'EXPIRED' || status === 'INITIAL') return 'error'
  if (status === 'EXPIRING_SOON') return 'warning'
  return 'info'
})

/** 横幅提示文案（由后端返回） */
const title = computed(() => {
  return passwordStatus.value?.message || ''
})

/** 跳转至安全设置页修改密码 */
function handleChangePassword() {
  router.push('/profile/security')
}
</script>

<template>
  <el-alert
    v-if="showWarning"
    :type="alertType"
    :title="title"
    show-icon
    :closable="false"
    style="border-radius: 0; margin-bottom: 0"
  >
    <template #default>
      <div class="flex items-center justify-between w-full">
        <span>{{ title }}</span>
        <el-button
          type="primary"
          size="small"
          plain
          @click="handleChangePassword"
        >
          立即修改
        </el-button>
      </div>
    </template>
  </el-alert>
</template>
