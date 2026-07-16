<!--
  @fileoverview 通用批量操作工具栏组件
  @description 列表选中行后顶部滑入展示的批量操作条：
  - Props: selectedCount(已选行数)、actions(批量操作按钮配置)
  - Emits: clear(清空选择)
  - 配合 v-permission 指令实现按钮级权限控制
  @module components/common/BatchToolbar
  @author ydsz-team
  @since 1.0.0
-->
<script lang="ts">
/**
 * 批量操作工具栏
 *
 * 当列表存在选中行时，从顶部滑入展示操作条：
 *  - 左侧显示已选数量与「清空选择」
 *  - 右侧渲染 actions 配置的批量操作按钮（支持权限指令）
 *
 * 使用示例：
 *   <BatchToolbar
 *     :selected-count="selectedRows.length"
 *     :actions="[
 *       { label: '批量审批', type: 'primary', permission: 'x:y:approve', handler: batchApprove },
 *       { label: '批量删除', type: 'danger', permission: 'x:y:delete', handler: batchDelete },
 *     ]"
 *     @clear="selectedRows = []"
 *   />
 */

/** 批量操作按钮配置 */
export interface BatchAction {
  /** 按钮文案 */
  label: string
  /** 按钮类型 */
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  /** 按钮图标（element-plus icon 名称） */
  icon?: string
  /** 权限码，配合 v-permission 指令做按钮级权限控制 */
  permission?: string
  /** 点击处理函数 */
  handler: () => void
}

export default { name: 'BatchToolbar' }
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check } from '@element-plus/icons-vue'

const { t } = useI18n()

const props = defineProps<{
  /** 当前选中行数 */
  selectedCount: number
  /** 批量操作配置 */
  actions: BatchAction[]
}>()

const emit = defineEmits<{
  /** 清空选择 */
  clear: []
}>()

const visible = computed(() => props.selectedCount > 0)
</script>

<template>
  <Transition name="batch-slide">
    <div v-if="visible" class="batch-toolbar">
      <div class="batch-info">
        <el-icon><Check /></el-icon>
        <span v-html="t('common.selectedItems', { n: '<strong>' + selectedCount + '</strong>' })"></span>
        <el-button link type="primary" @click="emit('clear')">{{ t('common.clearSelection') }}</el-button>
      </div>
      <div class="batch-actions">
        <el-button
          v-for="(action, idx) in actions"
          :key="idx"
          :type="action.type || 'default'"
          :icon="action.icon"
          v-permission="action.permission"
          @click="action.handler"
        >
          {{ action.label }}
        </el-button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.batch-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 4px;
  margin-bottom: 12px;
}
.batch-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.batch-actions {
  display: flex;
  gap: 8px;
}
.batch-slide-enter-active,
.batch-slide-leave-active {
  transition: all 0.3s ease;
}
.batch-slide-enter-from,
.batch-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
