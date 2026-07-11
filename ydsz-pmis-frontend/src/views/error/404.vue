<!--
  @file 404 错误页
  @description 404 路由未匹配时展示的兜底错误页，提供返回首页入口，对应路由 * 兜底匹配。
  @module views/error/404
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HomeFilled, Back, Refresh } from '@element-plus/icons-vue'

const router = useRouter()
const { t } = useI18n()

/** 刷新当前页 */
function reload() {
  window.location.reload()
}
</script>

<template>
  <div class="error-page">
    <div class="error-container">
      <!-- 品牌 SVG 插画：迷路指南针 -->
      <div class="error-illustration">
        <svg
          class="compass-svg"
          width="180"
          height="180"
          viewBox="0 0 180 180"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <!-- 外圈 -->
          <circle cx="90" cy="90" r="78" stroke="#e2e8f0" stroke-width="2" fill="#f8fafc" />
          <circle cx="90" cy="90" r="68" stroke="#cbd5e1" stroke-width="1.5" stroke-dasharray="4 4" />
          <!-- 方位刻度 -->
          <line x1="90" y1="16" x2="90" y2="28" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" />
          <line x1="90" y1="152" x2="90" y2="164" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" />
          <line x1="16" y1="90" x2="28" y2="90" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" />
          <line x1="152" y1="90" x2="164" y2="90" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" />
          <!-- 方位字母 N/E/S/W -->
          <text x="90" y="13" text-anchor="middle" font-size="11" font-weight="600" fill="#64748b">N</text>
          <text x="90" y="177" text-anchor="middle" font-size="11" font-weight="600" fill="#94a3b8">S</text>
          <text x="10" y="94" text-anchor="middle" font-size="11" font-weight="600" fill="#94a3b8">W</text>
          <text x="172" y="94" text-anchor="middle" font-size="11" font-weight="600" fill="#94a3b8">E</text>
          <!-- 指针（偏转，表示迷失方向） -->
          <g class="compass-needle">
            <polygon points="90,35 82,90 90,82 98,90" fill="#1890ff" />
            <polygon points="90,145 82,90 90,98 98,90" fill="#e2e8f0" />
            <circle cx="90" cy="90" r="5" fill="#1e293b" />
            <circle cx="90" cy="90" r="2" fill="#fff" />
          </g>
          <!-- 迷茫问号气泡 -->
          <g class="question-bubble">
            <circle cx="138" cy="48" r="16" fill="#fef3c7" stroke="#f59e0b" stroke-width="1.5" />
            <text x="138" y="54" text-anchor="middle" font-size="18" font-weight="700" fill="#d97706">?</text>
          </g>
        </svg>
      </div>

      <!-- 错误码大字展示 -->
      <div class="error-code">404</div>

      <!-- el-result 提供结构化布局 -->
      <el-result :sub-title="t('common.notFoundSubtitle')">
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

  .compass-svg {
    display: block;
  }

  // 指针轻微摆动动画
  .compass-needle {
    transform-origin: 90px 90px;
    animation: needle-sway 3s ease-in-out infinite;
  }

  // 问号气泡浮动
  .question-bubble {
    animation: float 2.5s ease-in-out infinite;
  }
}

.error-code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -2px;
  background: linear-gradient(135deg, #1890ff 0%, #6366f1 100%);
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

@keyframes needle-sway {
  0%,
  100% {
    transform: rotate(-12deg);
  }
  50% {
    transform: rotate(8deg);
  }
}

// 尊重用户的减少动画偏好
@media (prefers-reduced-motion: reduce) {
  .compass-needle,
  .question-bubble,
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
