<script setup lang="ts">
/**
 * 敏感操作二次认证弹窗
 *
 * <p>支持三种凭据切换：当前密码 / TOTP 动态码 / 一次性备份码。
 * <p>由 useReAuth composable 控制状态；也可单独使用，通过 v-model:visible 绑定。
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
import type { ReAuthMethod } from '@/api/user/reauth'

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
    :title="`敏感操作二次认证 — ${operationName} (${operationCode})`"
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
      :title="`即将执行：${operationName}`"
      description="为保障系统安全，本次操作需要二次认证。认证通过后 5 分钟内 token 有效，且只能使用一次。"
      style="margin-bottom: 16px"
    />

    <el-form label-width="0" @submit.prevent>
      <el-form-item>
        <el-radio-group v-model="method">
          <el-radio-button value="PASSWORD">当前密码</el-radio-button>
          <el-radio-button v-if="has2fa" value="TOTP">TOTP 动态码</el-radio-button>
          <el-radio-button v-if="has2fa" value="BACKUP_CODE">备份码</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="method === 'PASSWORD'">
        <el-input
          v-model="password"
          type="password"
          show-password
          placeholder="请输入当前登录密码"
          :disabled="loading"
          @keyup.enter="onConfirm"
        />
      </el-form-item>

      <el-form-item v-else-if="method === 'TOTP'">
        <el-input
          v-model="otp"
          maxlength="6"
          placeholder="6 位 TOTP 动态码"
          :disabled="loading"
          style="letter-spacing: 4px; font-family: monospace"
          @keyup.enter="onConfirm"
        />
      </el-form-item>

      <el-form-item v-else>
        <el-input
          v-model="backupCode"
          placeholder="请输入 8 位一次性备份码"
          :disabled="loading"
          style="font-family: monospace"
          @keyup.enter="onConfirm"
        />
        <div class="hint">备份码使用后将立即失效，每个备份码只能使用一次</div>
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
      <el-button :disabled="loading" @click="onCancel">取消</el-button>
      <el-button
        type="primary"
        :loading="loading"
        @click="onConfirm"
      >
        确认并继续
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
