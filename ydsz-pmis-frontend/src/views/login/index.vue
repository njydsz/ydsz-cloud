<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = route
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123',
  rememberMe: true,
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
  ],
}

async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    loading.value = true
    await userStore.login({
      username: form.username,
      password: form.password,
      rememberMe: form.rememberMe,
    })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    await router.push(redirect)
  } catch (e) {
    // 错误已在 request 中处理
  } finally {
    loading.value = false
  }
}
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
  height: 540px;
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
