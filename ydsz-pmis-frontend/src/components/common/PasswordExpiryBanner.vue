<!--
  @file 密码过期预警横幅
  @description 当用户密码即将过期或已过期时，在页面顶部展示预警横幅。
  @module components/common/PasswordExpiryBanner
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePasswordExpiry } from '@/composables/usePasswordExpiry'

const router = useRouter()
const { passwordStatus, showWarning } = usePasswordExpiry()

/** 横幅类型 */
const alertType = computed(() => {
  const status = passwordStatus.value?.status
  if (status === 'EXPIRED' || status === 'INITIAL') return 'error'
  if (status === 'EXPIRING_SOON') return 'warning'
  return 'info'
})

/** 横幅标题 */
const title = computed(() => {
  return passwordStatus.value?.message || ''
})

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
