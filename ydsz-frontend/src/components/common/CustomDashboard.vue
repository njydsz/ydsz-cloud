<!--
  @fileoverview 可定制仪表盘组件 (P2-12)
  @description 基于 12 栅格 CSS Grid 的可定制仪表盘：
  - Props: preset(角色预设) / editable(是否可编辑)
  - Emits: layout-change(布局变化时)
  - 支持按角色预设加载、布局持久化、编辑/删除小部件
  - 各小部件内容通过具名插槽 widget.id 注入
  @module components/common/CustomDashboard
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 可定制仪表盘
 *
 * 使用原生 CSS Grid(12 列)实现栅格布局, 避免引入 vue-grid-layout 的额外体积.
 * - preset 指定角色预设模板; 不传则读取 localStorage 已保存布局, 兜底 PM 预设
 * - editable 开启编辑模式: 切换编辑、重置、删除小部件, 退出编辑时持久化
 * - 每个小部件通过具名插槽 #w1 / #w2 ... 注入业务内容
 */
import { ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import type { DashboardWidget } from '@/types/dashboard'
import { WIDGET_PRESETS } from '@/types/dashboard'

const props = defineProps<{
  /** 角色预设: PM / FINANCE / EXECUTIVE; 不传则读 localStorage */
  preset?: string
  /** 是否允许编辑布局 */
  editable?: boolean
}>()

const emit = defineEmits<{
  'layout-change': [widgets: DashboardWidget[]]
}>()

const widgets = ref<DashboardWidget[]>([])
const editing = ref(false)
const STORAGE_KEY = 'ydsz-dashboard-layout'

const loadLayout = () => {
  if (props.preset && WIDGET_PRESETS[props.preset]) {
    widgets.value = JSON.parse(JSON.stringify(WIDGET_PRESETS[props.preset]))
    return
  }
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) {
    try {
      widgets.value = JSON.parse(saved)
      return
    } catch {
      // 解析失败, 回退到默认预设
    }
  }
  widgets.value = JSON.parse(JSON.stringify(WIDGET_PRESETS.PM))
}

const saveLayout = () => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(widgets.value))
  emit('layout-change', widgets.value)
}

const toggleEdit = () => {
  editing.value = !editing.value
  if (!editing.value) {
    saveLayout()
  }
}

const resetLayout = () => {
  const preset = props.preset || 'PM'
  widgets.value = JSON.parse(
    JSON.stringify(WIDGET_PRESETS[preset] || WIDGET_PRESETS.PM),
  )
  saveLayout()
}

const removeWidget = (id: string) => {
  widgets.value = widgets.value.filter((w) => w.id !== id)
}

const getWidgetIcon = (type: string) => {
  const icons: Record<string, string> = {
    kpi: '📊',
    chart: '📈',
    table: '📋',
    todo: '✅',
    alert: '⚠️',
  }
  return icons[type] || '📦'
}

watch(() => props.preset, loadLayout, { immediate: true })
</script>

<template>
  <div class="custom-dashboard">
    <div class="dashboard-toolbar">
      <el-button
        v-if="editable"
        :type="editing ? 'success' : 'primary'"
        @click="toggleEdit"
      >
        {{ editing ? '保存布局' : '编辑布局' }}
      </el-button>
      <el-button v-if="editable && editing" link @click="resetLayout">
        重置
      </el-button>
    </div>
    <div class="dashboard-grid" :class="{ editing }">
      <div
        v-for="widget in widgets"
        :key="widget.id"
        class="dashboard-widget"
        :style="{
          gridColumn: `${widget.x + 1} / span ${widget.w}`,
          gridRow: `${widget.y + 1} / span ${widget.h}`,
        }"
      >
        <div class="widget-header">
          <span class="widget-icon">{{ getWidgetIcon(widget.type) }}</span>
          <span class="widget-title">{{ widget.title }}</span>
          <el-icon
            v-if="editing"
            class="widget-remove"
            @click="removeWidget(widget.id)"
          >
            <Close />
          </el-icon>
        </div>
        <div class="widget-body">
          <slot :name="widget.id" :widget="widget">
            <el-skeleton :rows="3" animated />
          </slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'CustomDashboard' }
</script>

<style lang="scss" scoped>
.dashboard-toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 16px;
  min-height: 400px;
}
.dashboard-widget {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.3s;
}
.dashboard-grid.editing .dashboard-widget {
  cursor: move;
  border-style: dashed;
  border-color: var(--el-color-primary);
}
.widget-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-weight: 600;
  font-size: 14px;
}
.widget-icon {
  font-size: 16px;
}
.widget-title {
  flex: 1;
}
.widget-remove {
  cursor: pointer;
  color: var(--el-color-danger);
}
.widget-body {
  padding: 16px;
}
</style>
