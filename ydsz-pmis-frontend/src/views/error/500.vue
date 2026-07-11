<!--
  @file 500 错误页
  @description 服务端异常或路由懒加载失败（chunk load error）时展示的兜底错误页，
               提供返回首页与刷新重试入口；由 router.onError 跳转或用户手动访问 /500。
  @module views/error/500
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HomeFilled, Back, Refresh } from '@element-plus/icons-vue'

const router = useRouter()
const { t } = useI18n()

/** 刷新当前页：通过 location.reload 触发整页重载，规避 chunk 缓存问题 */
function reload() {
  window.location.reload()
}
</script>

<template>
  <div class="error-page">
    <div class="error-container">
      <!-- 品牌 SVG 插画：故障齿轮 + 警告 -->
      <div class="error-illustration">
        <svg
          class="gear-svg"
          width="180"
          height="180"
          viewBox="0 0 180 180"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <!-- 齿轮主体 -->
          <g class="gear-rotate">
            <!-- 齿轮齿（8 个） -->
            <g fill="#cbd5e1">
              <rect x="84" y="8" width="12" height="20" rx="2" />
              <rect x="84" y="152" width="12" height="20" rx="2" />
              <rect x="8" y="84" width="20" height="12" rx="2" />
              <rect x="152" y="84" width="20" height="12" rx="2" />
              <rect x="28" y="28" width="12" height="20" rx="2" transform="rotate(-45 34 38)" />
              <rect x="140" y="28" width="12" height="20" rx="2" transform="rotate(45 146 38)" />
              <rect x="28" y="132" width="12" height="20" rx="2" transform="rotate(45 34 142)" />
              <rect x="140" y="132" width="12" height="20" rx="2" transform="rotate(-45 146 142)" />
            </g>
            <!-- 齿轮外圈 -->
            <circle cx="90" cy="90" r="58" fill="#f8fafc" stroke="#cbd5e1" stroke-width="2" />
            <!-- 齿轮内圈 -->
            <circle cx="90" cy="90" r="42" fill="none" stroke="#e2e8f0" stroke-width="1.5" stroke-dasharray="3 3" />
            <!-- 齿轮中心孔 -->
            <circle cx="90" cy="90" r="12" fill="#e2e8f0" stroke="#94a3b8" stroke-width="1.5" />
            <circle cx="90" cy="90" r="5" fill="#94a3b8" />
          </g>
          <!-- 警告三角 -->
          <g class="warning-triangle">
            <polygon points="90,52 118,100 62,100" fill="#fef3c7" stroke="#f59e0b" stroke-width="2.5" stroke-linejoin="round" />
            <rect x="87" y="66" width="6" height="16" rx="3" fill="#d97706" />
            <circle cx="90" cy="90" r="3.5" fill="#d97706" />
          </g>
        </svg>
      </div>

      <!-- 错误码大字展示 -->
      <div class="error-code">500</div>

      <!-- el-result 提供结构化布局 -->
      <el-result :sub-title="t('common.serverErrorSubtitle')">
        <template #extra>
          <div class="error-actions">
            <el-button type="primary" :icon="HomeFilled" @click="router.push('/')">
              {{ t('common.backHome') }}
            </el-button>
            <el-button :icon="Back" @click="router.back()">
              {{ t('common.backPrevious') }}
            </el-button>
            <el-button :icon="Refresh" @click="reload">
              {{ t('common.refreshRetry') }}
            </el-button>
          </div>
        </template>
      </el-result>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-page;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.error-illustration {
  margin-bottom: 8px;

  .gear-svg {
    display: block;
  }

  // 齿轮缓慢旋转
  .gear-rotate {
    transform-origin: 90px 90px;
    animation: gear-spin 8s linear infinite;
  }

  // 警告三角浮动
  .warning-triangle {
    animation: float 2s ease-in-out infinite;
    filter: drop-shadow(0 2px 4px rgba(245, 158, 11, 0.2));
  }
}

.error-code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -2px;
  background: linear-gradient(135deg, #ef4444 0%, #f59e0b 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 16px;
  animation: float 4s ease-in-out infinite;
}

.error-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

@keyframes gear-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

// 尊重用户的减少动画偏好
@media (prefers-reduced-motion: reduce) {
  .gear-rotate,
  .warning-triangle,
  .error-code {
    animation: none;
  }
}

// 移动端适配
@media (max-width: 768px) {
  .error-code {
    font-size: 64px;
  }

  .error-actions {
    flex-direction: column;
    width: 100%;
    max-width: 240px;

    .el-button {
      width: 100%;
    }
  }
}
</style>
