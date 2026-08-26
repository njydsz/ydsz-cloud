<template>
  <div class="schedule-calendar-container">
    <!-- 顶部控制栏 -->
    <div class="calendar-toolbar">
      <el-date-picker
        v-model="selectedDate"
        type="date"
        placeholder="选择日期"
        format="YYYY-MM-DD"
        value-format="YYYY-MM-DD"
        @change="handleDateChange"
      />
      <el-button type="primary" :icon="Refresh" @click="fetchScheduleData">刷新</el-button>
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button label="day">日视图</el-radio-button>
        <el-radio-button label="week">周视图</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 日历网格 -->
    <el-calendar v-model="selectedDate" class="schedule-calendar">
      <template #date-cell="{ data }">
        <div class="calendar-cell">
          <span class="cell-day">{{ data.day.split('-')[2] }}</span>
          <div v-if="getTasksForDate(data.day).length > 0" class="task-dots">
            <el-tooltip
              v-for="task in getTasksForDate(data.day).slice(0, 3)"
              :key="task.jobKey + task.fireTime"
              :content="`${task.jobName} - ${formatTime(task.fireTime)}`"
              placement="top"
            >
              <span class="task-dot" :class="getTaskStatusClass(task)"></span>
            </el-tooltip>
            <span v-if="getTasksForDate(data.day).length > 3" class="more-tag">
              +{{ getTasksForDate(data.day).length - 3 }}
            </span>
          </div>
        </div>
      </template>
    </el-calendar>

    <!-- 选中日期的任务详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`${selectedDateDetail} 调度任务`"
      direction="rtl"
      size="400px"
    >
      <el-timeline>
        <el-timeline-item
          v-for="task in selectedDateTasks"
          :key="task.jobKey + task.fireTime"
          :timestamp="formatTime(task.fireTime)"
          :type="getTaskType(task)"
          placement="top"
        >
          <el-card>
            <template #header>
              <span class="task-title">{{ task.jobName }}</span>
              <el-tag size="small" effect="plain">{{ task.jobKey }}</el-tag>
            </template>
            <p><b>Cron 表达式：</b>{{ task.cron }}</p>
            <p><b>分组：</b>{{ task.group || '默认' }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="selectedDateTasks.length === 0" description="当日无调度任务" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import axios from 'axios'

// ==================== 响应式状态 ====================

/** 当前选中日期 */
const selectedDate = ref(new Date())

/** 视图模式：day / week */
const viewMode = ref('day')

/** 调度任务数据 */
const scheduleItems = ref([])

/** 详情抽屉可见性 */
const drawnerVisible = ref(false)

/** 选中日期详情 */
const selectedDateDetail = ref('')

// ==================== 计算属性 ====================

/** 选中日期的任务列表 */
const selectedDateTasks = computed(() => {
  return getTasksForDate(selectedDateDetail.value)
})

// ==================== 方法 ====================

/**
 * 获取指定日期的任务列表。
 *
 * @param {string} date 日期字符串（YYYY-MM-DD）
 * @returns {Array} 该日期的调度任务列表
 */
function getTasksForDate(date) {
  if (!date || scheduleItems.value.length === 0) return []
  return scheduleItems.value.filter(item => {
    return item.fireTime && item.fireTime.startsWith(date)
  })
}

/**
 * 格式化时间显示。
 *
 * @param {string} fireTime ISO-8601 时间字符串
 * @returns {string} 格式化后的时间（HH:mm:ss）
 */
function formatTime(fireTime) {
  if (!fireTime) return ''
  return fireTime.substring(11, 19)
}

/**
 * 获取任务状态样式类。
 * 基于任务 group 分配不同颜色，便于视觉区分。
 *
 * @param {object} task 调度任务项
 * @returns {string} CSS 类名
 */
function getTaskStatusClass(task) {
  const group = task.group || 'default'
  const hash = group.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0)
  const classes = ['dot-primary', 'dot-success', 'dot-warning', 'dot-danger']
  return classes[hash % classes.length]
}

/**
 * 获取任务时间线类型。
 *
 * @param {object} task 调度任务项
 * @returns {string} Element Plus Timeline 类型
 */
function getTaskType(task) {
  const hour = parseInt(task.fireTime?.substring(12, 14) || '0', 10)
  if (hour < 6) return 'info'
  if (hour < 12) return 'primary'
  if (hour < 18) return 'success'
  return 'warning'
}

/**
 * 处理日期变化事件。
 *
 * @param {Date} val 新选中的日期
 */
function handleDateChange(val) {
  if (val) {
    const dateStr = typeof val === 'string' ? val : val.toISOString().split('T')[0]
    selectedDateDetail.value = dateStr
    drawnerVisible.value = true
  }
}

/**
 * 从后端获取调度日历数据。
 */
async function fetchScheduleData() {
  try {
    const response = await axios.get('/api/v1/cronjob/calendar/schedule', {
      params: {
        hours: viewMode.value === 'week' ? 168 : 24,
        maxPerJob: 50
      }
    })
    if (response.data && response.data.code === 0) {
      scheduleItems.value = response.data.data.map(item => ({
        ...item,
        fireTime: item.fireTime ? item.fireTime.replace('T', ' ').substring(0, 19) : ''
      }))
      ElMessage.success(`已加载 ${scheduleItems.value.length} 条调度记录`)
    }
  } catch (error) {
    ElMessage.error('获取调度日历数据失败：' + (error.message || '未知错误'))
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  fetchScheduleData()
})
</script>

<style scoped>
.schedule-calendar-container {
  padding: 16px;
}

.calendar-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--el-bg-color-page);
  border-radius: 8px;
}

.schedule-calendar :deep(.el-calendar-table .el-calendar-day) {
  height: 80px;
  vertical-align: top;
}

.calendar-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  height: 100%;
}

.cell-day {
  font-weight: 600;
  margin-bottom: 4px;
}

.task-dots {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
  align-items: center;
}

.task-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  cursor: pointer;
}

.dot-primary { background-color: var(--el-color-primary); }
.dot-success { background-color: var(--el-color-success); }
.dot-warning { background-color: var(--el-color-warning); }
.dot-danger { background-color: var(--el-color-danger); }

.more-tag {
  font-size: 10px;
  color: var(--el-color-info);
  cursor: pointer;
}

.task-title {
  font-weight: 600;
  margin-right: 8px;
}
</style>
