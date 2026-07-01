<!--
  @file 登录页
  @description 系统登录页，支持账号密码登录、图形验证码、记住我、2FA 二次验证（OTP/备份码），对接 @/api/user 与 @/api/user/two-factor 模块。
  @module views/login
-->
<script setup lang="ts">
/**
 * 登录页 - 支持 2FA 二步验证
 *
 * 流程：账号密码 -> 后端返回 mfaRequired -> 弹窗输入 OTP/备份码 -> 完成登录
 */
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getCaptchaApi } from '@/api/user'
import { verify2fa, verifyBackupCode } from '@/api/user/two-factor'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 登录表单引用 */
const formRef = ref<FormInstance>()
/** 登录提交中状态 */
const loading = ref(false)
/** 验证码加载中状态 */
const captchaLoading = ref(false)

/** 登录表单数据 */
const form = reactive({
  username: 'admin',
  password: 'admin123',
  captchaKey: '',
  captchaCode: '',
  rememberMe: true,
})

/** 图形验证码图片 Base64 */
const captchaImage = ref<string>('')

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
}

/** 刷新图形验证码，失败时全局提示 */
async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const { data } = await getCaptchaApi()
    form.captchaKey = data.captchaKey
    captchaImage.value = data.captchaImage
  } catch (e: any) {
    ElMessage.error(e?.message || '验证码加载失败')
  } finally {
    captchaLoading.value = false
  }
}

// ===== 2FA 弹窗 =====
/** 2FA 验证弹窗显隐 */
const mfaDialogVisible = ref(false)
/** 2FA 表单数据（otp 或 backupCode 二选一） */
const mfaForm = reactive({ otp: '', backupCode: '' })
/** 2FA 表单引用 */
const mfaFormRef = ref<FormInstance>()
/** 2FA 验证提交中状态 */
const mfaLoading = ref(false)
/** 2FA 验证模式：OTP-动态码 / BACKUP-备份码 */
const mfaMode = ref<'OTP' | 'BACKUP'>('OTP')
/** 待完成 2FA 的登录上下文（暂存用户名/密码/记住我，2FA 通过后重新登录换 token） */
const pendingMfa = ref<{ username: string; password: string; rememberMe: boolean } | null>(null)

/** 2FA 表单校验规则，根据 mfaMode 动态切换 */
const mfaRules = computed<FormRules>(() => ({
  otp: mfaMode.value === 'OTP' ? [
    { required: true, message: '请输入 6 位动态码', trigger: 'blur' },
    { len: 6, message: '动态码为 6 位数字', trigger: 'blur' },
  ] : [],
  backupCode: mfaMode.value === 'BACKUP' ? [
    { required: true, message: '请输入备份码', trigger: 'blur' },
  ] : [],
}))

/** 提交登录，若后端要求 2FA 则弹出验证窗 */
async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const result = await userStore.login({
      username: form.username,
      password: form.password,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode,
      rememberMe: form.rememberMe,
    })
    if (result.mfaRequired && !result.mfaPassed) {
      // 进入 2FA 二次验证
      pendingMfa.value = {
        username: form.username,
        password: form.password,
        rememberMe: form.rememberMe,
      }
      mfaMode.value = 'OTP'
      mfaForm.otp = ''
      mfaForm.backupCode = ''
      mfaDialogVisible.value = true
      ElMessage.warning('该账号已开启双因素认证，请输入 6 位动态码')
      return
    }
    await onLoginSuccess()
  } catch {
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

/** 提交 2FA 验证，先调用 verify 接口完成 2FA，再重新登录换取完整 token */
async function submitMfa() {
  if (!mfaFormRef.value) return
  try {
    await mfaFormRef.value.validate()
  } catch {
    return
  }
  mfaLoading.value = true
  try {
    // 1) 调用后端 verify 完成 2FA（后端会校验 otp / backup）
    if (mfaMode.value === 'OTP') {
      await verify2fa(mfaForm.otp)
    } else {
      await verifyBackupCode(mfaForm.backupCode)
    }
    // 2) 重新登录带上 otp，拿到完整 token
    if (!pendingMfa.value) return
    const result = await userStore.login({
      username: pendingMfa.value.username,
      password: pendingMfa.value.password,
      rememberMe: pendingMfa.value.rememberMe,
      otp: mfaMode.value === 'OTP' ? mfaForm.otp : undefined,
      backupCode: mfaMode.value === 'BACKUP' ? mfaForm.backupCode : undefined,
    })
    if (result.mfaRequired && !result.mfaPassed) {
      ElMessage.error('2FA 验证失败，请重试')
      return
    }
    mfaDialogVisible.value = false
    await onLoginSuccess()
  } catch (e: any) {
    ElMessage.error(e?.message || '2FA 验证失败')
  } finally {
    mfaLoading.value = false
  }
}

/** 登录成功后处理：拉取用户信息并跳转到 redirect 或首页 */
async function onLoginSuccess() {
  await userStore.fetchUserInfo()
  ElMessage.success('登录成功')
  const redirect = (route.query.redirect as string) || '/'
  await router.push(redirect)
}

/**
 * 切换 2FA 验证模式
 * @param mode OTP-动态码 / BACKUP-备份码
 */
function switchMfaMode(mode: string | number) {
  if (mode === 'OTP' || mode === 'BACKUP') {
    mfaMode.value = mode
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<template>
  <div class="login-wrap">
    <div class="login-container">
      <div class="login-left">
        <h1 class="login-brand">PMIS</h1>
        <p class="login-slogan">项目运营管理系统</p>
        <ul class="login-features">
          <li>业财一体化 · 全生命周期管控</li>
          <li>L1-L18 职级费率 · EVM 挣值管理</li>
          <li>WBS 锚点 · 利润精细化核算</li>
          <li>双因素认证 · 等保 2.0 安全基线</li>
        </ul>
      </div>
      <div class="login-right">
        <h2 class="login-title">用户登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="'User'" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="'Lock'"
              show-password
            />
          </el-form-item>
          <el-form-item prop="captchaCode">
            <div class="captcha-row">
              <el-input v-model="form.captchaCode" placeholder="请输入图形验证码" :prefix-icon="'Picture'" />
              <div class="captcha-img" :class="{ loading: captchaLoading }" @click="refreshCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="captcha" />
                <span v-else class="captcha-placeholder">{{ captchaLoading ? '加载中…' : '点击加载' }}</span>
              </div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="form.rememberMe">记住我</el-checkbox>
          </el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
            登 录
          </el-button>
        </el-form>
        <p class="login-tip">默认账号: admin / admin123（演示）</p>
      </div>
    </div>

    <!-- 2FA 二次验证弹窗 -->
    <el-dialog
      v-model="mfaDialogVisible"
      title="双因素认证"
      width="420px"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="您的账号已开启双因素认证"
        description="请打开手机 Google Authenticator / 微软 Authenticator，扫描或输入 6 位动态码。"
        style="margin-bottom: 16px"
      />
      <el-tabs v-model="mfaMode" @tab-change="switchMfaMode">
        <el-tab-pane label="动态码" name="OTP" />
        <el-tab-pane label="备份码" name="BACKUP" />
      </el-tabs>
      <el-form ref="mfaFormRef" :model="mfaForm" :rules="mfaRules" @keyup.enter="submitMfa">
        <el-form-item v-if="mfaMode === 'OTP'" prop="otp">
          <el-input
            v-model="mfaForm.otp"
            placeholder="请输入 6 位动态码"
            maxlength="6"
            size="large"
            style="letter-spacing: 4px; font-size: 20px; text-align: center"
          />
        </el-form-item>
        <el-form-item v-else prop="backupCode">
          <el-input
            v-model="mfaForm.backupCode"
            placeholder="请输入一次性备份码"
            size="large"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mfaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="mfaLoading" @click="submitMfa">验证</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.login-wrap {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
}

.login-container {
  display: flex;
  width: 880px;
  height: 600px;
  background: $bg-white;
  border-radius: $border-radius-lg;
  box-shadow: $shadow-light;
  overflow: hidden;
}

.login-left {
  flex: 1;
  padding: $spacing-xl $spacing-xl;
  background: linear-gradient(135deg, #1890ff 0%, #722ed1 100%);
  color: $bg-white;
  display: flex;
  flex-direction: column;
  justify-content: center;

  .login-brand {
    font-size: 48px;
    font-weight: 700;
    margin-bottom: $spacing-sm;
  }

  .login-slogan {
    font-size: $font-size-lg;
    margin-bottom: $spacing-xl;
    opacity: 0.95;
  }

  .login-features {
    list-style: none;

    li {
      padding: $spacing-sm 0;
      font-size: $font-size-base;
      opacity: 0.9;
    }
  }
}

.login-right {
  flex: 1;
  padding: $spacing-xl;
  display: flex;
  flex-direction: column;
  justify-content: center;

  .login-title {
    font-size: 24px;
    font-weight: 600;
    margin-bottom: $spacing-xl;
    color: $text-primary;
  }

  .captcha-row {
    display: flex;
    align-items: center;
    width: 100%;
    gap: 12px;
  }

  .captcha-img {
    width: 130px;
    height: 40px;
    border: 1px solid $border-base;
    border-radius: 4px;
    overflow: hidden;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    background: $bg-base;

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }

    .captcha-placeholder {
      font-size: 12px;
      color: $text-secondary;
    }
  }

  .login-btn {
    width: 100%;
    height: 44px;
    font-size: $font-size-md;
  }

  .login-tip {
    margin-top: $spacing-md;
    font-size: $font-size-sm;
    color: $text-placeholder;
    text-align: center;
  }
}
</style>
