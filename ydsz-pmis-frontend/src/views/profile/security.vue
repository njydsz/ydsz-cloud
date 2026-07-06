<!--
  @file 个人安全中心
  @description 个人安全设置页面，提供 2FA 绑定/关闭、备份码查看、活跃会话管理（下线/批量下线）、修改密码（含密码强度校验），对接 @/api/user/two-factor 与 @/api/user/session 模块。
  @module views/profile/security
-->
<script setup lang="ts">
/**
 * 个人中心 - 安全设置
 *
 * 1) 2FA 绑定 / 关闭
 * 2) 备份码查看
 * 3) 活跃会话管理
 * 4) 修改密码
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  bind2fa,
  confirm2fa,
  disable2fa,
  get2faStatus,
  listBackupCodes,
} from '@/api/user/two-factor'
import {
  listMyActiveSessions,
  invalidateSession,
  kickOtherSessions,
} from '@/api/user/session'
import type { UserSessionVO } from '@/api/user/session'
import { parseUserAgent } from '@/utils/device'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'
import { usePasswordStrength } from '@/composables/usePasswordStrength'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

const { t } = useI18n()

// ============= 2FA =============
/** 2FA 状态信息（是否开启、绑定时间、上次使用时间、备份码剩余数） */
const twoFAStatus = ref<{ enabled: boolean; boundAt?: string; lastUsedAt?: string; backupCodeCount?: number }>({ enabled: false })
/** 2FA 是否已绑定（用于 UI 切换） */
const twoFABound = ref(false)
/** 调用 bind2fa 返回的 secret 与 otpauthUri（扫码绑定阶段） */
const bindResult = ref<{ secret: string; otpauthUri: string } | null>(null)
/** 绑定确认表单（输入 6 位动态码） */
const bindForm = reactive({ otp: '' })
/** 绑定确认表单引用 */
const bindFormRef = ref<FormInstance>()
/** 备份码列表 */
const backupCodes = ref<string[]>([])

/** 拉取 2FA 状态信息，失败时降级为未开启 */
async function fetch2faStatus() {
  try {
    const { data } = await get2faStatus()
    twoFAStatus.value = {
      enabled: data?.enabled || false,
      boundAt: data?.boundAt,
      lastUsedAt: data?.lastUsedAt,
      backupCodeCount: data?.backupCodeCount ?? 0,
    }
    twoFABound.value = data?.enabled || false
  } catch {
    twoFAStatus.value = { enabled: false }
  }
}

/** 发起 2FA 绑定，后端返回 secret 与 otpauthUri 供前端展示二维码 */
async function startBind() {
  try {
    const { data } = await bind2fa()
    bindResult.value = data
    ElMessage.success(t('profile.security.twoFA.messages.bindGenerated'))
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('profile.security.twoFA.messages.bindFailed'))
  }
}

/** 提交 6 位动态码确认绑定，绑定成功后拉取备份码 */
async function confirmBind() {
  if (!bindFormRef.value) return
  try {
    await bindFormRef.value.validate()
  } catch {
    return
  }
  try {
    await confirm2fa(bindForm.otp)
    ElMessage.success(t('profile.security.twoFA.messages.bindSuccess'))
    bindResult.value = null
    bindForm.otp = ''
    await fetch2faStatus()
    await fetchBackupCodes()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('profile.security.twoFA.messages.confirmFailed'))
  }
}

/** 取消绑定，清空绑定结果与表单 */
async function cancelBind() {
  bindResult.value = null
  bindForm.otp = ''
}

/** 拉取备份码列表，失败时降级为空数组 */
async function fetchBackupCodes() {
  try {
    const { data } = await listBackupCodes()
    backupCodes.value = data || []
  } catch {
    backupCodes.value = []
  }
}

/** 关闭 2FA，需二次确认，关闭后降低账号安全等级 */
async function onDisable() {
  try {
    await ElMessageBox.confirm(
      t('profile.security.twoFA.messages.disableTip'),
      t('profile.security.twoFA.messages.disableTitle'),
      { type: 'warning', confirmButtonText: t('profile.security.twoFA.messages.disableConfirm'), cancelButtonText: t('profile.security.twoFA.messages.disableCancel') },
    )
    await disable2fa()
    ElMessage.success(t('profile.security.twoFA.messages.disabled'))
    await fetch2faStatus()
  } catch { /* 用户取消 */ }
}

// ============= 会话管理 =============
/** 当前账号活跃会话列表 */
const sessions = ref<UserSessionVO[]>([])
/** 会话列表加载状态 */
const sessionLoading = ref(false)
/** 拉取当前账号活跃会话列表 */
async function fetchSessions() {
  sessionLoading.value = true
  try {
    const { data } = await listMyActiveSessions()
    sessions.value = data || []
  } finally {
    sessionLoading.value = false
  }
}

/**
 * 下线指定会话，需二次确认
 * @param row 当前行会话数据
 */
async function onKick(row: UserSessionVO) {
  const dev = parseUserAgent(row.userAgent)
  try {
    await ElMessageBox.confirm(
      t('profile.security.session.messages.kickConfirm', { os: dev.os, browser: dev.browser }),
      t('profile.security.session.messages.kickTitle'),
      { type: 'warning' },
    )
    await invalidateSession(row.sessionId)
    ElMessage.success(t('profile.security.session.messages.kicked'))
    await fetchSessions()
  } catch { /* 用户取消 */ }
}

/** 下线其他所有设备，仅保留当前会话，需二次确认 */
async function onKickOthers() {
  try {
    await ElMessageBox.confirm(
      t('profile.security.session.messages.kickOthersConfirm'),
      t('profile.security.session.messages.kickTitle'),
      { type: 'warning' },
    )
    await kickOtherSessions()
    ElMessage.success(t('profile.security.session.messages.kickOthersSuccess'))
    await fetchSessions()
  } catch { /* 用户取消 */ }
}

// ============= 修改密码 =============
/** 修改密码表单数据 */
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
/** 修改密码表单引用 */
const pwdFormRef = ref<FormInstance>()
/** 新密码强度评估结果 */
const { result: pwdStrength } = usePasswordStrength(
  computed(() => pwdForm.newPassword),
)
const pwdRules = computed(() => ({
  oldPassword: [{ required: true, message: t('profile.security.password.rules.oldRequired'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('profile.security.password.rules.newRequired'), trigger: 'blur' },
    { min: 8, max: 32, message: t('profile.security.password.rules.newLength'), trigger: 'blur' },
    {
      validator: (_rule: unknown, v: string, cb: (error?: Error) => void) => {
        if (!v) return cb()
        if (pwdStrength.value.score < 3) {
          return cb(new Error(t('profile.security.password.rules.newWeak', { text: pwdStrength.value.text })))
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: t('profile.security.password.rules.confirmRequired'), trigger: 'blur' },
    {
      validator: (_rule: unknown, v: string, cb: (error?: Error) => void) => {
        if (v !== pwdForm.newPassword) return cb(new Error(t('profile.security.password.rules.confirmMismatch')))
        cb()
      },
      trigger: 'blur',
    },
  ],
}))

/** 提交修改密码，校验通过后调用后端接口，成功后延时登出并跳转登录页 */
async function onChangePwd() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  // 调用后端修改密码 API（auth/change-password）
  try {
    await import('@/api/user').then(({ changePasswordApi }) =>
      changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword }),
    )
    ElMessage.success(t('profile.security.password.messages.changed'))
    setTimeout(async () => {
      await userStore.logout()
      location.href = '/#/login'
    }, 1500)
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || t('profile.security.password.messages.changeFailed'))
  }
}

onMounted(async () => {
  await fetch2faStatus()
  await fetchBackupCodes()
  await fetchSessions()
})
</script>

<template>
  <div class="profile-security">
    <el-row :gutter="16">
      <!-- 左侧：2FA -->
      <el-col :span="12">
        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-title">
              <el-icon><Lock /></el-icon>
              <span>{{ t('profile.security.twoFA.title') }}</span>
              <el-tag v-if="twoFABound" type="success" size="small">{{ t('profile.security.twoFA.enabled') }}</el-tag>
              <el-tag v-else type="info" size="small">{{ t('profile.security.twoFA.notEnabled') }}</el-tag>
            </div>
          </template>
          <el-alert
            v-if="!twoFABound"
            type="info"
            :closable="false"
            show-icon
            :title="t('profile.security.twoFA.alertTitle')"
            :description="t('profile.security.twoFA.alertDesc')"
            style="margin-bottom: 16px"
          />
          <div v-if="twoFABound">
            <el-descriptions :column="1" border>
              <el-descriptions-item :label="t('profile.security.twoFA.fields.boundAt')">{{ twoFAStatus.boundAt || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('profile.security.twoFA.fields.lastUsedAt')">{{ twoFAStatus.lastUsedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('profile.security.twoFA.fields.backupCodeCount')">{{ twoFAStatus.backupCodeCount ?? 0 }} {{ t('profile.security.twoFA.fields.backupCodeUnit') }}</el-descriptions-item>
            </el-descriptions>
            <div class="actions">
              <el-button @click="fetchBackupCodes">{{ t('profile.security.twoFA.buttons.viewBackup') }}</el-button>
              <el-button type="danger" @click="onDisable">{{ t('profile.security.twoFA.buttons.disable') }}</el-button>
            </div>
            <el-collapse v-if="backupCodes.length" style="margin-top: 12px">
              <el-collapse-item :title="t('profile.security.twoFA.backupCodeTitle')" name="codes">
                <el-tag v-for="c in backupCodes" :key="c" style="margin: 2px">{{ c }}</el-tag>
              </el-collapse-item>
            </el-collapse>
          </div>
          <div v-else-if="!bindResult">
            <el-button type="primary" :icon="'Plus'" @click="startBind">{{ t('profile.security.twoFA.buttons.enable') }}</el-button>
          </div>
          <div v-else>
            <el-steps :active="1" finish-status="success" simple style="margin-bottom: 16px">
              <el-step :title="t('profile.security.twoFA.steps.scan')" />
              <el-step :title="t('profile.security.twoFA.steps.inputCode')" />
              <el-step :title="t('profile.security.twoFA.steps.complete')" />
            </el-steps>
            <el-alert
              type="success"
              :closable="false"
              show-icon
              :title="t('profile.security.twoFA.bind.scanAlert')"
            />
            <el-input
              :model-value="bindResult.otpauthUri"
              type="textarea"
              :rows="2"
              readonly
              style="margin: 8px 0; font-family: monospace"
            />
            <el-input
              :model-value="bindResult.secret"
              readonly
              style="margin-bottom: 12px; font-family: monospace"
            >
              <template #prepend>{{ t('profile.security.twoFA.bind.secret') }}</template>
            </el-input>
            <el-form ref="bindFormRef" :model="bindForm" inline>
              <el-form-item prop="otp" :rules="[{ required: true, len: 6, message: t('profile.security.twoFA.bind.otpRequired') }]">
                <el-input v-model="bindForm.otp" maxlength="6" :placeholder="t('profile.security.twoFA.bind.otpPlaceholder')" style="width: 160px; letter-spacing: 4px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="confirmBind">{{ t('profile.security.twoFA.buttons.confirmBind') }}</el-button>
                <el-button @click="cancelBind">{{ t('profile.security.twoFA.buttons.cancel') }}</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>

        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-title">
              <el-icon><Key /></el-icon>
              <span>{{ t('profile.security.password.title') }}</span>
            </div>
          </template>
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px">
            <el-form-item :label="t('profile.security.password.fields.oldPassword')" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('profile.security.password.fields.newPassword')" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
              <PasswordStrengthBar
                :password="pwdForm.newPassword"
                :show-input="false"
                :show-rules="true"
                style="margin-top: 6px"
              />
              <div class="hint">{{ t('profile.security.password.hint') }}</div>
            </el-form-item>
            <el-form-item :label="t('profile.security.password.fields.confirmPassword')" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onChangePwd">{{ t('profile.security.password.buttons.save') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：会话 -->
      <el-col :span="12">
        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-title">
              <el-icon><Monitor /></el-icon>
              <span>{{ t('profile.security.session.title') }}</span>
              <el-button link type="primary" :icon="'Refresh'" :loading="sessionLoading" @click="fetchSessions" />
            </div>
          </template>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            :title="t('profile.security.session.alert')"
            style="margin-bottom: 12px"
          />
          <vxe-table :data="sessions" :loading="sessionLoading" border height="auto">
            <vxe-column :title="t('profile.security.session.columns.device')" width="120">
              <template #default="{ row }">
                <el-icon v-if="parseUserAgent(row.userAgent).device === 'DESKTOP'"><Monitor /></el-icon>
                <el-icon v-else-if="parseUserAgent(row.userAgent).device === 'MOBILE'"><Iphone /></el-icon>
                <el-icon v-else-if="parseUserAgent(row.userAgent).device === 'TABLET'"><Tablet /></el-icon>
                <el-icon v-else><QuestionFilled /></el-icon>
                <span style="margin-left: 4px; font-size: 12px">{{ parseUserAgent(row.userAgent).os }}</span>
              </template>
            </vxe-column>
            <vxe-column :title="t('profile.security.session.columns.browser')" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ parseUserAgent(row.userAgent).browser }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="clientIp" :title="t('profile.security.session.columns.ip')" width="120" />
            <vxe-column field="userAgent" :title="t('profile.security.session.columns.userAgent')" min-width="200" show-overflow />
            <vxe-column field="loginAt" :title="t('profile.security.session.columns.loginAt')" width="160" />
            <vxe-column field="lastActiveAt" :title="t('profile.security.session.columns.lastActiveAt')" width="160" />
            <vxe-column field="status" :title="t('profile.security.session.columns.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? t('profile.security.session.status.current') : (row.status || '-') }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column :title="t('profile.security.session.columns.action')" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="onKick(row)">{{ t('profile.security.session.buttons.kick') }}</el-button>
              </template>
            </vxe-column>
            <template #empty><el-empty :description="t('profile.security.session.messages.empty')" /></template>
          </vxe-table>
          <div class="actions" style="margin-top: 12px">
            <el-button type="warning" @click="onKickOthers">{{ t('profile.security.session.buttons.kickOthers') }}</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.profile-security {
  .card {
    margin-bottom: 16px;
    .card-title {
      display: flex; align-items: center; gap: 6px; font-weight: 600;
    }
    .actions {
      margin-top: 12px;
      display: flex; gap: 8px;
    }
    .hint { color: #909399; font-size: 12px; line-height: 1.5; margin-top: 4px; }
  }
}
</style>
