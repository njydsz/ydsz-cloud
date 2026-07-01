<!--
  @file 通用空状态组件
  @description 列表/表格/搜索结果为空时的展示组件，支持 4 种预设场景与自定义内容
  @module components/common/EmptyState
-->
<script setup lang="ts">
/**
 * 通用空状态组件
 *
 * 用于列表/表格/搜索结果为空时展示, 支持:
 *  - 4 种预设场景 (list / search / network / noPermission) 与自定义 description
 *  - 自定义图标 (element-plus icon 或 URL 图片)
 *  - 可选的 CTA 按钮 (如 "新建"/"清除筛选")
 *  - 嵌入额外说明插槽 (如帮助链接/操作指引)
 *
 * 使用示例:
 *   <EmptyState preset="search" @action="resetQuery">
 *     <template #action>
 *       <el-button type="primary" @click="reset">清除筛选</el-button>
 *     </template>
 *   </EmptyState>
 */
import { computed } from 'vue'

export type EmptyPreset = 'list' | 'search' | 'network' | 'noPermission' | 'custom'

interface PresetConfig {
  icon: string
  title: string
  description: string
}

const PRESETS: Record<Exclude<EmptyPreset, 'custom'>, PresetConfig> = {
  list: {
    icon: 'Document',
    title: '暂无数据',
    description: '当前列表为空, 可以点击下方按钮创建第一条记录',
  },
  search: {
    icon: 'Search',
    title: '未找到匹配的数据',
    description: '请尝试调整筛选条件或清空搜索关键字',
  },
  network: {
    icon: 'Warning',
    title: '数据加载失败',
    description: '网络异常或服务暂时不可用, 请稍后重试',
  },
  noPermission: {
    icon: 'Lock',
    title: '无访问权限',
    description: '当前账号没有访问该资源的权限, 如需访问请联系管理员',
  },
}

const props = withDefaults(
  defineProps<{
    /** 预设场景; 'custom' 时使用外部传入的 description / icon */
    preset?: EmptyPreset
    /** 自定义标题; 不传则使用 preset 默认 */
    title?: string
    /** 自定义描述; 不传则使用 preset 默认 */
    description?: string
    /** element-plus icon 名称 (preset='custom' 时生效) */
    icon?: string
    /** 图片 URL (优先于 icon) */
    imageUrl?: string
    /** 图标尺寸 */
    iconSize?: number
    /** 区块高度 (px), 0 表示由内容自适应 */
    blockHeight?: number
    /** CTA 按钮文本; 不传则隐藏 action 按钮 */
    actionText?: string
    /** CTA 按钮类型 */
    actionType?: 'primary' | 'success' | 'warning' | 'info' | 'danger'
  }>(),
  {
    preset: 'list',
    iconSize: 64,
    blockHeight: 0,
    actionType: 'primary',
  },
)

const emit = defineEmits<{
  (e: 'action'): void
}>()

const resolved = computed<PresetConfig>(() => {
  if (props.preset === 'custom') {
    return {
      icon: props.icon || 'Document',
      title: props.title || '暂无数据',
      description: props.description || '',
    }
  }
  const def = PRESETS[props.preset]
  return {
    icon: props.icon || def.icon,
    title: props.title || def.title,
    description: props.description || def.description,
  }
})

const containerStyle = computed(() => {
  if (!props.blockHeight) {
    return {}
  }
  return { height: `${props.blockHeight}px` }
})

function onAction() {
  emit('action')
}
</script>

<template>
  <div class="empty-state" :style="containerStyle">
    <div class="empty-state__inner">
      <img v-if="imageUrl" :src="imageUrl" class="empty-state__image" alt="empty" />
      <div v-else class="empty-state__body">
        <el-icon :size="iconSize" class="empty-state__icon">
          <component :is="resolved.icon" />
        </el-icon>
        <div v-if="resolved.description" class="empty-state__description">
          {{ resolved.description }}
        </div>
      </div>
      <div class="empty-state__title">{{ resolved.title }}</div>
      <slot name="extra" />
      <div v-if="actionText || $slots.action" class="empty-state__action">
        <slot name="action">
          <el-button v-if="actionText" :type="actionType" @click="onAction">
            {{ actionText }}
          </el-button>
        </slot>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.empty-state {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  &__inner {
    width: 100%;
    text-align: center;
  }
  &__body {
    margin-bottom: 12px;
  }
  &__image {
    max-width: 160px;
    margin: 0 auto 12px;
    display: block;
  }
  &__icon {
    color: var(--el-color-info-light-3, #c0c4cc);
  }
  &__description {
    margin-top: 8px;
    color: var(--el-text-color-secondary, #909399);
    font-size: 14px;
  }
  &__title {
    font-size: 16px;
    color: var(--el-text-color-primary, #303133);
    margin-bottom: 8px;
    font-weight: 500;
  }
  &__action {
    margin-top: 12px;
  }
}
</style>
