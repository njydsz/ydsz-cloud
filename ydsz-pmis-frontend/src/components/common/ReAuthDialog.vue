<!--
  @file 敏感操作二次认证弹窗
  @description 支持 PASSWORD / TOTP / BACKUP_CODE 三种凭据切换；可配合 useReAuth 或单独受控使用
  @module components/common/ReAuthDialog
-->
<script setup lang="ts">
/**
 * 敏感操作二次认证弹窗
 *
 * 支持三种凭据切换：当前密码 / TOTP 动态码 / 一次性备份码。
 * 由 useReAuth composable 控制状态；也可单独使用，通过 v-model:visible 绑定。
 *
 * 使用方式 1（推荐）：配合 useReAuth
 * ```ts
 * const { dialog, options, handleConfirm, handleCancel } = useReAuth({...})
 * ```
 *
 * 使用方式 2（受控）：
 * ```vue
 * <ReAuthDialog
 *   v-model:visible="visible"
 *   :operation-code="'USER_DELETE'"
 *   :operation-name="'删除用户'"
 *   :loading="loading"
 *   :error-message="errorMessage"
 *   @confirm="onConfirm"
 *   @cancel="onCancel"
 * />
 * ```
 */
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ReAuthMethod } from '@/api/user/reauth'

const { t } = useI18n()

interface Props {
  visible: boolean
  operationCode: string
  operationName: string
  loading?: boolean
  /** 优先显示的凭据类型；用户可切换 */
  method?: ReAuthMethod
  /** 错误提示（父组件传入） */
  errorMessage?: string
  /** 当前用户是否已绑定 2FA（用于决定是否展示 TOTP/BACKUP 选项） */
  has2fa?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  method: 'PASSWORD',
  errorMessage: '',
  has2fa: false,
})

const emit = defineEmits<{
  'update:visible': [v: boolean]
  'update:method': [m: ReAuthMethod]
  'confirm': [payload: { method: ReAuthMethod; password?: string; otp?: string; backupCode?: string }]
  'cancel': []
  'switch-method': [m: ReAuthMethod]
}>()

const password = ref('')
const otp = ref('')
const backupCode = ref('')

const visible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const method = computed<ReAuthMethod>({
  get: () => props.method,
  set: (v) => {
    emit('update:method', v)
    emit('switch-method', v)
    // 切换时清空输入与错误
    password.value = ''
    otp.value = ''
    backupCode.value = ''
  },
})

watch(
  () => props.visible,
  (v) => {
    if (v) {
      password.value = ''
      otp.value = ''
      backupCode.value = ''
    }
  },
)

function onConfirm() {
  if (props.loading) return
  const payload: {
    method: ReAuthMethod
    password?: string
    otp?: string
    backupCode?: string
  } = { method: method.value }
  if (method.value === 'PASSWORD') payload.password = password.value
  else if (method.value === 'TOTP') payload.otp = otp.value
  else if (method.value === 'BACKUP_CODE') payload.backupCode = backupCode.value
  emit('confirm', payload)
}

function onCancel() {
  emit('update:visible', false)
  emit('cancel')
}

function onClosed() {
  password.value = ''
  otp.value = ''
  backupCode.value = ''
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('common.reauth.title', { name: operationName, code: operationCode })"
    width="460px"
    :close-on-click-modal="false"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    align-center
    @cancel="onCancel"
    @closed="onClosed"
  >
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      :title="t('common.reauth.aboutToExecute', { name: operationName })"
      :description="t('common.reauth.description')"
      style="margin-bottom: 16px"
    />

    <el-form label-width="0" @submit.prevent>
      <el-form-item>
        <el-radio-group v-model="method">
          <el-radio-button value="PASSWORD">{{ t('common.reauth.currentPassword') }}</el-radio-button>
          <el-radio-button v-if="has2fa" value="TOTP">{{ t('common.reauth.totpCode') }}</el-radio-button>
          <el-radio-button v-if="has2fa" value="BACKUP_CODE">{{ t('common.reauth.backupCode') }}</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="method === 'PASSWORD'">
        <el-input
          v-model="password"
          type="password"
          show-password
          :placeholder="t('common.reauth.passwordPlaceholder')"
          :disabled="loading"
          @keyup.enter="onConfirm"
        />
      </el-form-item>

      <el-form-item v-else-if="method === 'TOTP'">
        <el-input
          v-model="otp"
          maxlength="6"
          :placeholder="t('common.reauth.totpPlaceholder')"
          :disabled="loading"
          style="letter-spacing: 4px; font-family: monospace"
          @keyup.enter="onConfirm"
        />
      </el-form-item>

      <el-form-item v-else>
        <el-input
          v-model="backupCode"
          :placeholder="t('common.reauth.backupPlaceholder')"
          :disabled="loading"
          style="font-family: monospace"
          @keyup.enter="onConfirm"
        />
        <div class="hint">{{ t('common.reauth.backupHint') }}</div>
      </el-form-item>

      <el-alert
        v-if="errorMessage"
        type="error"
        :closable="false"
        show-icon
        :title="errorMessage"
        style="margin-top: 4px"
      />
    </el-form>

    <template #footer>
      <el-button :disabled="loading" @click="onCancel">{{ t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :loading="loading"
        @click="onConfirm"
      >
        {{ t('common.confirmContinue') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.hint {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
