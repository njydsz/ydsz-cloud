<!--
  @fileoverview 通用状态标签
  @description 通过 status 编码与映射表自动渲染带色彩的 el-tag：
  - Props: value / map / fallbackType / label / type / size / effect / plain
  - 优先使用 map[value] 解析；未命中则回退到 fallbackType 与值本身
  - 场景: 状态/审批结果/工作流节点等枚举展示
  @module components/common/StatusTag
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 通用状态标签
 *
 * 通过传入的 status 编码和映射表，自动渲染带色彩的 el-tag。
 * 适用场景：状态/审批结果/工作流节点等枚举展示。
 *
 * 使用示例：
 *   <StatusTag :value="row.status" :map="statusMap" />
 *   <StatusTag :value="row.status" type="success" label="已通过" />
 */
import { computed } from 'vue'

interface StatusMapItem {
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  /** 自定义 tag 颜色（element-plus 不支持 hex 颜色，但可在此扩展） */
  text?: string
}

const props = defineProps<{
  /** 当前状态值 */
  value?: string | number | null | undefined
  /** 状态映射表 key=value 编码，value=item */
  map?: Record<string, StatusMapItem>
  /** 当 value 不在 map 中时回退的 type */
  fallbackType?: StatusMapItem['type']
  /** 直接传入文本（无需 map） */
  label?: string
  /** 直接传入 type（无需 map） */
  type?: StatusMapItem['type']
  /** 尺寸 */
  size?: 'small' | 'default' | 'large'
  /** 是否禁用 hover 效果 */
  effect?: 'light' | 'dark' | 'plain'
  /** 是否显示为 plain 效果 */
  plain?: boolean
}>()

const resolved = computed<StatusMapItem>(() => {
  if (props.label !== undefined) {
    return { label: props.label, type: props.type || 'info' }
  }
  const v = props.value === null || props.value === undefined ? '' : String(props.value)
  return (
    props.map?.[v] || {
      label: v || '-',
      type: props.fallbackType || 'info',
    }
  )
})
</script>

<template>
  <el-tag
    :type="resolved.type || 'info'"
    :size="size || 'default'"
    :effect="effect || (plain ? 'plain' : 'light')"
  >
    {{ resolved.text || resolved.label }}
  </el-tag>
</template>
