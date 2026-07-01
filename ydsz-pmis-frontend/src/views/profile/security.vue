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
import { ElMessage, ElMessageBox } from 'element-plus'
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

// ============= 2FA =============
const twoFAStatus = ref<{ enabled: boolean; boundAt?: string; lastUsedAt?: string; backupCodeCount?: number }>({ enabled: false })
const twoFABound = ref(false)
const bindResult = ref<{ secret: string; otpauthUri: string } | null>(null)
const bindForm = reactive({ otp: '' })
const bindFormRef = ref<any>()
const backupCodes = ref<string[]>([])

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

async function startBind() {
  try {
    const { data } = await bind2fa()
    bindResult.value = data
    ElMessage.success('已生成密钥，请使用 Authenticator 扫码绑定')
  } catch (e: any) {
    ElMessage.error(e?.message || '生成绑定信息失败')
  }
}

async function confirmBind() {
  if (!bindFormRef.value) return
  try {
    await bindFormRef.value.validate()
  } catch {
    return
  }
  try {
    await confirm2fa(bindForm.otp)
    ElMessage.success('绑定成功！建议立即保存下方备份码')
    bindResult.value = null
    bindForm.otp = ''
    await fetch2faStatus()
    await fetchBackupCodes()
  } catch (e: any) {
    ElMessage.error(e?.message || '校验失败')
  }
}

async function cancelBind() {
  bindResult.value = null
  bindForm.otp = ''
}

async function fetchBackupCodes() {
  try {
    const { data } = await listBackupCodes()
    backupCodes.value = data || []
  } catch {
    backupCodes.value = []
  }
}

async function onDisable() {
  try {
    await ElMessageBox.confirm(
      '关闭 2FA 将降低账号安全等级，确定继续？',
      '关闭双因素认证',
      { type: 'warning', confirmButtonText: '关闭', cancelButtonText: '取消' },
    )
    await disable2fa()
    ElMessage.success('已关闭 2FA')
    await fetch2faStatus()
  } catch { /* 用户取消 */ }
}

// ============= 会话管理 =============
const sessions = ref<UserSessionVO[]>([])
const sessionLoading = ref(false)
async function fetchSessions() {
  sessionLoading.value = true
  try {
    const { data } = await listMyActiveSessions()
    sessions.value = data || []
  } finally {
    sessionLoading.value = false
  }
}

async function onKick(row: UserSessionVO) {
  const dev = parseUserAgent(row.userAgent)
  try {
    await ElMessageBox.confirm(
      `确认下线设备 [${dev.os} · ${dev.browser}]？下线后该设备需重新登录。`,
      '提示',
      { type: 'warning' },
    )
    await invalidateSession(row.sessionId)
    ElMessage.success('已下线')
    await fetchSessions()
  } catch { /* 用户取消 */ }
}

async function onKickOthers() {
  try {
    await ElMessageBox.confirm('确认下线其他所有设备？仅保留当前会话。', '提示', { type: 'warning' })
    await kickOtherSessions()
    ElMessage.success('已下线其他设备')
    await fetchSessions()
  } catch { /* 用户取消 */ }
}

// ============= 修改密码 =============
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdFormRef = ref<any>()
const { result: pwdStrength } = usePasswordStrength(
  computed(() => pwdForm.newPassword),
)
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度 8-32 位', trigger: 'blur' },
    {
      validator: (_: any, v: string, cb: any) => {
        if (!v) return cb()
        if (pwdStrength.value.score < 3) {
          return cb(new Error(`密码强度不足（当前：${pwdStrength.value.text}，至少需 3 类规则）`))
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_: any, v: string, cb: any) => {
        if (v !== pwdForm.newPassword) return cb(new Error('两次密码不一致'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

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
    ElMessage.success('密码修改成功，请重新登录')
    setTimeout(async () => {
      await userStore.logout()
      location.href = '/#/login'
    }, 1500)
  } catch (e: any) {
    ElMessage.error(e?.message || '密码修改失败')
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
              <span>双因素认证 (2FA / TOTP)</span>
              <el-tag v-if="twoFABound" type="success" size="small">已开启</el-tag>
              <el-tag v-else type="info" size="small">未开启</el-tag>
            </div>
          </template>
          <el-alert
            v-if="!twoFABound"
            type="info"
            :closable="false"
            show-icon
            title="开启双因素认证可大幅提升账号安全"
            description="使用 Google Authenticator / 微软 Authenticator 扫码绑定，登录时需输入 6 位动态码。"
            style="margin-bottom: 16px"
          />
          <div v-if="twoFABound">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="绑定时间">{{ twoFAStatus.boundAt || '-' }}</el-descriptions-item>
              <el-descriptions-item label="上次使用">{{ twoFAStatus.lastUsedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item label="备份码剩余">{{ twoFAStatus.backupCodeCount ?? 0 }} 个</el-descriptions-item>
            </el-descriptions>
            <div class="actions">
              <el-button @click="fetchBackupCodes">查看备份码</el-button>
              <el-button type="danger" @click="onDisable">关闭 2FA</el-button>
            </div>
            <el-collapse v-if="backupCodes.length" style="margin-top: 12px">
              <el-collapse-item title="备份码（脱敏）" name="codes">
                <el-tag v-for="c in backupCodes" :key="c" style="margin: 2px">{{ c }}</el-tag>
              </el-collapse-item>
            </el-collapse>
          </div>
          <div v-else-if="!bindResult">
            <el-button type="primary" :icon="'Plus'" @click="startBind">开启 2FA</el-button>
          </div>
          <div v-else>
            <el-steps :active="1" finish-status="success" simple style="margin-bottom: 16px">
              <el-step title="扫码" />
              <el-step title="输入 6 位动态码" />
              <el-step title="完成" />
            </el-steps>
            <el-alert
              type="success"
              :closable="false"
              show-icon
              title="使用 Authenticator 扫描下方 otpauth URI"
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
              <template #prepend>密钥</template>
            </el-input>
            <el-form ref="bindFormRef" :model="bindForm" inline>
              <el-form-item prop="otp" :rules="[{ required: true, len: 6, message: '请输入 6 位动态码' }]">
                <el-input v-model="bindForm.otp" maxlength="6" placeholder="6 位动态码" style="width: 160px; letter-spacing: 4px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="confirmBind">确认绑定</el-button>
                <el-button @click="cancelBind">取消</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>

        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-title">
              <el-icon><Key /></el-icon>
              <span>修改密码</span>
            </div>
          </template>
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px">
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
              <PasswordStrengthBar
                :password="pwdForm.newPassword"
                :show-input="false"
                :show-rules="true"
                style="margin-top: 6px"
              />
              <div class="hint">8-32 位，包含大小写/数字/特殊字符中至少 3 类</div>
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onChangePwd">保存</el-button>
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
              <span>活跃会话</span>
              <el-button link type="primary" :icon="'Refresh'" :loading="sessionLoading" @click="fetchSessions" />
            </div>
          </template>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="同账号最多允许 1 个活跃会话，新登录会踢出旧设备"
            style="margin-bottom: 12px"
          />
          <vxe-table :data="sessions" :loading="sessionLoading" border height="auto">
            <vxe-column title="设备" width="120">
              <template #default="{ row }">
                <el-icon v-if="parseUserAgent(row.userAgent).device === 'DESKTOP'"><Monitor /></el-icon>
                <el-icon v-else-if="parseUserAgent(row.userAgent).device === 'MOBILE'"><Iphone /></el-icon>
                <el-icon v-else-if="parseUserAgent(row.userAgent).device === 'TABLET'"><Tablet /></el-icon>
                <el-icon v-else><QuestionFilled /></el-icon>
                <span style="margin-left: 4px; font-size: 12px">{{ parseUserAgent(row.userAgent).os }}</span>
              </template>
            </vxe-column>
            <vxe-column title="浏览器" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ parseUserAgent(row.userAgent).browser }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="clientIp" title="IP" width="120" />
            <vxe-column field="userAgent" title="客户端" min-width="200" show-overflow />
            <vxe-column field="loginAt" title="登录时间" width="160" />
            <vxe-column field="lastActiveAt" title="最近活跃" width="160" />
            <vxe-column field="status" title="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '当前' : (row.status || '-') }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column title="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="onKick(row)">下线</el-button>
              </template>
            </vxe-column>
            <template #empty><el-empty description="暂无活跃会话" /></template>
          </vxe-table>
          <div class="actions" style="margin-top: 12px">
            <el-button type="warning" @click="onKickOthers">下线其他设备</el-button>
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
