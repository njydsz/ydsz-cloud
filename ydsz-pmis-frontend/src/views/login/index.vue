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
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getCaptchaApi } from '@/api/user'
import { verify2fa, verifyBackupCode } from '@/api/user/two-factor'
import { handleError, showSuccess } from '@/utils/error'
import { useResponsive } from '@/composables/useResponsive'
import { onKeyActivate } from '@/composables/useKeyboardA11y'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()
const { isMobile } = useResponsive()

/** 登录表单引用 */
const formRef = ref<FormInstance>()
/** 登录提交中状态 */
const loading = ref(false)
/** 验证码加载中状态 */
const captchaLoading = ref(false)

/** 登录表单数据 */
const form = reactive({
  username: '',
  password: '',
  captchaKey: '',
  captchaCode: '',
  rememberMe: false,
})

/** 图形验证码图片 Base64 */
const captchaImage = ref<string>('')

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('login.rules.usernameRequired'), trigger: 'blur' }],
  password: [
    { required: true, message: t('login.rules.passwordRequired'), trigger: 'blur' },
    { min: 6, message: t('login.rules.passwordMinLength'), trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: t('login.rules.captchaRequired'), trigger: 'blur' }],
}))

/** 刷新图形验证码，失败时全局提示 */
async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const { data } = await getCaptchaApi()
    form.captchaKey = data.captchaKey
    captchaImage.value = data.captchaImage
  } catch (e) {
    handleError(e, 'refreshCaptcha')
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
    { required: true, message: t('login.mfa.rules.otpRequired'), trigger: 'blur' },
    { len: 6, message: t('login.mfa.rules.otpLength'), trigger: 'blur' },
  ] : [],
  backupCode: mfaMode.value === 'BACKUP' ? [
    { required: true, message: t('login.mfa.rules.backupRequired'), trigger: 'blur' },
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
      ElMessage.warning(t('login.mfa.enabledTip'))
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
      ElMessage.error(t('login.mfa.verifyFailedRetry'))
      return
    }
    mfaDialogVisible.value = false
    await onLoginSuccess()
  } catch (e) {
    handleError(e, 'submitMfa')
  } finally {
    mfaLoading.value = false
  }
}

/** 登录成功后处理：拉取用户信息并跳转到 redirect 或首页 */
async function onLoginSuccess() {
  await userStore.fetchUserInfo()
  showSuccess(t('login.messages.loginSuccess'))
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

/** 功能特性列表（配合 i18n） */
const features = computed(() => [
  { icon: 'Connection', text: t('login.features.integration') },
  { icon: 'TrendCharts', text: t('login.features.rate') },
  { icon: 'Aim', text: t('login.features.wbs') },
  { icon: 'Lock', text: t('login.features.security') },
])

onMounted(() => {
  refreshCaptcha()
})
</script>

<template>
  <div class="login-wrap">
    <!-- 动态背景装饰 -->
    <div class="login-bg-decor">
      <span class="orb orb-1" />
      <span class="orb orb-2" />
      <span class="orb orb-3" />
    </div>

    <div class="login-container">
      <!-- 左侧品牌展示区 -->
      <div class="login-left">
        <div class="login-left-content">
          <!-- 品牌 Logo -->
          <div class="brand-logo">
            <svg viewBox="0 0 48 48" fill="none" class="brand-logo-icon" aria-hidden="true">
              <rect x="4" y="4" width="40" height="40" rx="10" fill="rgba(255,255,255,0.15)" />
              <path d="M14 16h20M14 24h14M14 32h8" stroke="white" stroke-width="3" stroke-linecap="round" />
              <circle cx="36" cy="32" r="5" fill="white" opacity="0.9" />
            </svg>
            <span class="brand-name">PMIS</span>
          </div>

          <h1 class="login-slogan">{{ t('login.slogan') }}</h1>

          <ul class="login-features">
            <li v-for="feat in features" :key="feat.icon">
              <el-icon class="feature-icon"><component :is="feat.icon" /></el-icon>
              <span>{{ feat.text }}</span>
            </li>
          </ul>

          <div class="login-footer-brand">
            <span>{{ t('login.copyright') }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="login-right">
        <div class="login-form-inner">
          <h2 class="login-title">{{ t('login.title') }}</h2>
          <p class="login-subtitle">{{ t('login.subtitle') }}</p>

          <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" :placeholder="t('login.usernamePlaceholder')" :prefix-icon="'User'" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                :placeholder="t('login.passwordPlaceholder')"
                :prefix-icon="'Lock'"
                show-password
              />
            </el-form-item>
            <el-form-item prop="captchaCode">
              <div class="captcha-row">
                <el-input v-model="form.captchaCode" :placeholder="t('login.captchaPlaceholder')" :prefix-icon="'Picture'" />
                <div
                  class="captcha-img"
                  :class="{ loading: captchaLoading }"
                  role="button"
                  tabindex="0"
                  aria-label="点击刷新验证码"
                  @click="refreshCaptcha"
                  @keydown="onKeyActivate(refreshCaptcha)"
                >
                  <img v-if="captchaImage" :src="captchaImage" alt="captcha" loading="lazy" />
                  <span v-else class="captcha-placeholder">{{ captchaLoading ? t('login.captchaLoading') : t('login.captchaClickToLoad') }}</span>
                </div>
              </div>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="form.rememberMe">{{ t('login.rememberMe') }}</el-checkbox>
            </el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
              {{ t('login.submit') }}
            </el-button>
          </el-form>
          <p class="login-tip">{{ t('login.defaultAccountTip') }}</p>
        </div>
      </div>
    </div>

    <!-- 2FA 二次验证弹窗 -->
    <el-dialog
      v-model="mfaDialogVisible"
      :title="t('login.mfa.title')"
      :width="isMobile ? '90%' : '420px'"
      :fullscreen="isMobile"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="t('login.mfa.alertTitle')"
        :description="t('login.mfa.alertDescription')"
        style="margin-bottom: 16px"
      />
      <el-tabs v-model="mfaMode" @tab-change="switchMfaMode">
        <el-tab-pane :label="t('login.mfa.tabOtp')" name="OTP" />
        <el-tab-pane :label="t('login.mfa.tabBackup')" name="BACKUP" />
      </el-tabs>
      <el-form ref="mfaFormRef" :model="mfaForm" :rules="mfaRules" @keyup.enter="submitMfa">
        <el-form-item v-if="mfaMode === 'OTP'" prop="otp">
          <el-input
            v-model="mfaForm.otp"
            :placeholder="t('login.mfa.otpPlaceholder')"
            maxlength="6"
            size="large"
            style="letter-spacing: 4px; font-size: 20px; text-align: center"
          />
        </el-form-item>
        <el-form-item v-else prop="backupCode">
          <el-input
            v-model="mfaForm.backupCode"
            :placeholder="t('login.mfa.backupPlaceholder')"
            size="large"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mfaDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="mfaLoading" @click="submitMfa">{{ t('login.mfa.verify') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.login-wrap {
  position: relative;
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $gradient-login-bg;
  overflow: hidden;
}

// ===== 动态背景装饰 =====
.login-bg-decor {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;

  .orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(60px);
    opacity: 0.4;
    animation: orb-float 20s ease-in-out infinite;
  }

  .orb-1 {
    width: 400px;
    height: 400px;
    background: rgba(114, 46, 209, 0.3);
    top: -100px;
    left: -100px;
    animation-delay: 0s;
  }

  .orb-2 {
    width: 300px;
    height: 300px;
    background: rgba(22, 119, 255, 0.25);
    bottom: -80px;
    right: 10%;
    animation-delay: -7s;
  }

  .orb-3 {
    width: 250px;
    height: 250px;
    background: rgba(82, 196, 26, 0.15);
    top: 40%;
    right: -50px;
    animation-delay: -14s;
  }
}

@keyframes orb-float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -40px) scale(1.05); }
  66% { transform: translate(-20px, 30px) scale(0.95); }
}

// ===== 登录卡片 =====
.login-container {
  position: relative;
  z-index: 1;
  display: flex;
  width: 900px;
  max-height: 90vh;
  min-height: 560px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: $border-radius-xl;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(255, 255, 255, 0.1);
  overflow: hidden;
  animation: card-enter 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

// ===== 左侧品牌区 =====
.login-left {
  flex: 1;
  background: $gradient-login-left;
  color: $bg-white;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;

  // 左侧内层装饰光斑
  &::before {
    content: '';
    position: absolute;
    top: -50%;
    right: -30%;
    width: 300px;
    height: 300px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.06);
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    bottom: -40%;
    left: -20%;
    width: 250px;
    height: 250px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.04);
    pointer-events: none;
  }
}

.login-left-content {
  position: relative;
  z-index: 1;
  padding: $spacing-xl $spacing-xl;
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;

  animation: content-slide-in 0.8s 0.2s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes content-slide-in {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;

  .brand-logo-icon {
    width: 48px;
    height: 48px;
    flex-shrink: 0;
  }

  .brand-name {
    font-size: 36px;
    font-weight: 800;
    letter-spacing: 2px;
    color: #fff;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }
}

.login-slogan {
  font-size: $font-size-xl;
  font-weight: 500;
  margin-bottom: $spacing-xl;
  opacity: 0.95;
  line-height: 1.5;
}

.login-features {
  list-style: none;
  padding: 0;
  margin: 0;
  flex: 1;

  li {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm 0;
    font-size: $font-size-base;
    opacity: 0;
    animation: feature-fade-in 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;

    &:nth-child(1) { animation-delay: 0.4s; }
    &:nth-child(2) { animation-delay: 0.5s; }
    &:nth-child(3) { animation-delay: 0.6s; }
    &:nth-child(4) { animation-delay: 0.7s; }

    .feature-icon {
      font-size: 18px;
      opacity: 0.9;
      flex-shrink: 0;
    }

    span {
      opacity: 0.9;
    }
  }
}

@keyframes feature-fade-in {
  from {
    opacity: 0;
    transform: translateX(-12px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.login-footer-brand {
  margin-top: auto;
  padding-top: $spacing-lg;
  font-size: $font-size-xs;
  opacity: 0.6;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

// ===== 右侧表单区 =====
.login-right {
  flex: 1;
  padding: $spacing-xl $spacing-xl;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow-y: auto;

  .login-form-inner {
    width: 100%;
    max-width: 360px;
    margin: 0 auto;
    animation: form-slide-in 0.8s 0.3s cubic-bezier(0.16, 1, 0.3, 1) both;
  }
}

@keyframes form-slide-in {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.login-title {
  font-size: $font-size-2xl;
  font-weight: 700;
  margin-bottom: $spacing-xs;
  color: $text-primary;
}

.login-subtitle {
  font-size: $font-size-sm;
  color: $text-secondary;
  margin-bottom: $spacing-xl;
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
  border-radius: $border-radius-sm;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $bg-base;
  transition: border-color 0.2s ease;

  &:hover {
    border-color: $primary-color;
  }

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
  height: 46px;
  font-size: $font-size-md;
  font-weight: 600;
  border-radius: $border-radius-base;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(22, 119, 255, 0.35);
  }

  &:active {
    transform: translateY(0);
  }
}

.login-tip {
  margin-top: $spacing-md;
  font-size: $font-size-sm;
  color: $text-placeholder;
  text-align: center;
}

// ===== 移动端适配 =====
@media (max-width: $breakpoint-sm) {
  .login-container {
    width: 90%;
    max-width: 400px;
    height: auto;
    min-height: 500px;
    flex-direction: column;
  }

  .login-left {
    display: none;
  }

  .login-right {
    padding: $spacing-lg $spacing-md;

    .login-form-inner {
      max-width: 100%;
    }

    .login-title {
      font-size: $font-size-xl;
      text-align: center;
      margin-bottom: $spacing-xs;
    }

    .login-subtitle {
      text-align: center;
      margin-bottom: $spacing-lg;
    }

    .captcha-row {
      flex-direction: column;
      align-items: stretch;
      gap: $spacing-sm;

      .captcha-img {
        width: 100%;
        height: 50px;
      }
    }
  }
}

// ===== 平板适配 =====
@media (min-width: $breakpoint-sm) and (max-width: $breakpoint-md) {
  .login-container {
    width: 95%;
    max-width: 720px;
    min-height: 480px;
  }

  .login-left-content {
    padding: $spacing-lg;
  }

  .brand-logo .brand-name {
    font-size: 28px;
  }

  .login-slogan {
    font-size: $font-size-md;
  }
}
</style>
