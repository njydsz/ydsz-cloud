<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { formatDate } from '@/utils/format'

const userStore = useUserStore()

const metrics = ref([
  { title: '在执行项目', value: 28, unit: '个', trend: '+3', color: '#1890ff', icon: 'Document' },
  { title: '本月合同额', value: 1248.6, unit: '万', trend: '+12.5%', color: '#52c41a', icon: 'Money' },
  { title: '已确认收入', value: 685.2, unit: '万', trend: '+8.3%', color: '#722ed1', icon: 'TrendCharts' },
  { title: '本月毛利', value: 198.7, unit: '万', trend: '+5.6%', color: '#fa8c16', icon: 'DataAnalysis' },
])

const todoList = ref([
  { id: 1, title: '审批 PMIS-2024-001 项目立项申请', priority: 'high', time: '2026-06-30 14:30' },
  { id: 2, title: '审核张三提交的 2026-06 工时', priority: 'medium', time: '2026-06-30 16:00' },
  { id: 3, title: '回复客户关于合同变更的咨询', priority: 'medium', time: '2026-06-30 17:30' },
  { id: 4, title: '参加周一项目复盘会', priority: 'low', time: '2026-07-01 09:00' },
])

const newsList = ref([
  { id: 1, title: '【公司公告】2026 年度 H1 优秀员工评选启动', date: '2026-06-29' },
  { id: 2, title: '【系统公告】PMIS V1.0 正式发布，全员启用', date: '2026-06-30' },
  { id: 3, title: 【制度更新】《项目财务核算管理制度》修订发布', date: '2026-06-28' },
])

onMounted(() => {
  if (!userStore.userInfo) {
    userStore.fetchUserInfo().catch(() => {
      /* 已由全局拦截 */
    })
  }
})
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎卡片 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div>
          <h2>下午好，{{ userStore.realName || userStore.username }}！</h2>
          <p>欢迎使用 PMIS 项目运营管理系统 · 当前时间：{{ formatDate(new Date(), 'YYYY-MM-DD HH:mm') }}</p>
        </div>
        <el-icon class="welcome-icon" :size="60"><Sunny /></el-icon>
      </div>
    </el-card>

    <!-- 关键指标 -->
    <el-row :gutter="16" class="metric-row">
      <el-col v-for="m in metrics" :key="m.title" :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="metric-content">
            <div class="metric-icon" :style="{ background: m.color }">
              <el-icon :size="24"><component :is="m.icon" /></el-icon>
            </div>
            <div class="metric-info">
              <p class="metric-title">{{ m.title }}</p>
              <p class="metric-value">
                <span class="value">{{ m.value }}</span>
                <span class="unit">{{ m.unit }}</span>
              </p>
              <p class="metric-trend" :class="{ up: m.trend.startsWith('+') }">{{ m.trend }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>项目健康度</span>
              <el-link type="primary" :underline="false">查看详情</el-link>
            </div>
          </template>
          <div class="chart-placeholder">
            <el-icon :size="60" color="#909399"><PieChart /></el-icon>
            <p>项目健康度分布（绿/黄/红）</p>
            <p class="chart-hint">图表组件占位，实际项目接入 ECharts</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>待办事项</span>
              <el-badge :value="todoList.length" type="warning" />
            </div>
          </template>
          <el-scrollbar height="280px">
            <div v-for="todo in todoList" :key="todo.id" class="todo-item">
              <el-tag :type="todo.priority === 'high' ? 'danger' : todo.priority === 'medium' ? 'warning' : 'info'" size="small">
                {{ todo.priority === 'high' ? '紧急' : todo.priority === 'medium' ? '普通' : '低' }}
              </el-tag>
              <span class="todo-title">{{ todo.title }}</span>
              <span class="todo-time">{{ todo.time }}</span>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <span>系统公告</span>
          </template>
          <el-scrollbar height="200px">
            <div v-for="news in newsList" :key="news.id" class="news-item">
              <el-icon color="#1890ff"><Bell /></el-icon>
              <span class="news-title">{{ news.title }}</span>
              <span class="news-date">{{ news.date }}</span>
            </div>
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.welcome-card {
  background: linear-gradient(135deg, #1890ff 0%, #722ed1 100%);
  color: $bg-white;
  border: none;

  :deep(.el-card__body) {
    padding: $spacing-lg;
  }

  .welcome-content {
    display: flex;
    align-items: center;
    justify-content: space-between;

    h2 {
      font-size: 24px;
      margin-bottom: $spacing-sm;
    }

    p {
      opacity: 0.9;
    }
  }

  .welcome-icon {
    opacity: 0.6;
  }
}

.metric-row {
  margin-bottom: $spacing-md;
}

.metric-card {
  .metric-content {
    display: flex;
    align-items: center;
    gap: $spacing-md;
  }

  .metric-icon {
    width: 56px;
    height: 56px;
    border-radius: $border-radius-lg;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $bg-white;
  }

  .metric-info {
    flex: 1;
  }

  .metric-title {
    font-size: $font-size-sm;
    color: $text-secondary;
    margin-bottom: $spacing-xs;
  }

  .metric-value {
    display: flex;
    align-items: baseline;
    margin-bottom: $spacing-xs;

    .value {
      font-size: 26px;
      font-weight: 600;
      color: $text-primary;
    }

    .unit {
      margin-left: $spacing-xs;
      font-size: $font-size-sm;
      color: $text-secondary;
    }
  }

  .metric-trend {
    font-size: $font-size-xs;
    color: $text-placeholder;

    &.up {
      color: $success-color;
    }
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-placeholder {
  height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: $text-secondary;

  p {
    margin-top: $spacing-sm;
  }

  .chart-hint {
    font-size: $font-size-sm;
    color: $text-placeholder;
  }
}

.todo-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;

  .todo-title {
    flex: 1;
    color: $text-regular;
  }

  .todo-time {
    font-size: $font-size-sm;
    color: $text-placeholder;
  }
}

.news-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm 0;
  border-bottom: 1px dashed $border-extra-light;

  .news-title {
    flex: 1;
    color: $text-regular;
  }

  .news-date {
    font-size: $font-size-sm;
    color: $text-placeholder;
  }
}
</style>
