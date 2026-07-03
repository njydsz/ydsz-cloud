<!--
  @file 通用虚拟滚动表格组件
  @description 基于 vxe-table 封装的大数据量虚拟滚动表格，数据量超过阈值自动启用虚拟滚动
  @module components/common/VirtualTable
-->
<script setup lang="ts">
/**
 * 虚拟滚动大数据表格
 *
 * 基于 vxe-table 封装，当数据量超过 50 行时自动启用纵向虚拟滚动，
 * 保证万级数据渲染流畅。支持复选框选择与选择事件回传。
 *
 * 使用示例：
 *   <VirtualTable
 *     :data="largeList"
 *     :columns="columns"
 *     :height="600"
 *     checkbox
 *     @selection-change="onSelectionChange"
 *   />
 */
import { ref, computed } from 'vue'
import { VxeTable, VxeColumn } from 'vxe-table'
import type { VxeTableProps } from 'vxe-table'

/** 列配置 */
interface ColumnConfig {
  field: string
  title: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  formatter?: (row: Record<string, unknown>, column: ColumnConfig) => string
}

interface Props {
  /** 表格数据 */
  data: Record<string, unknown>[]
  /** 列配置 */
  columns: ColumnConfig[]
  /** 表格高度 */
  height?: number | string
  /** 行主键字段 */
  rowKey?: string
  /** 是否开启复选框选择列 */
  checkbox?: boolean
  /** 加载态 */
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: 500,
  rowKey: 'id',
  checkbox: false,
  loading: false,
})

const emit = defineEmits<{
  'selection-change': [selected: Record<string, unknown>[]]
}>()

const selectedRows = ref<Record<string, unknown>[]>([])

const tableConfig = computed<VxeTableProps>(() => ({
  height: props.height,
  rowConfig: { keyField: props.rowKey, isHover: true },
  scrollY: { enabled: true, gt: 50 },
  columnConfig: { resizable: true },
  radioConfig: { highlight: true },
  checkboxConfig: props.checkbox ? { highlight: true, range: true } : undefined,
  loading: props.loading,
}))

const handleSelectionChange = ({ records }: { records: Record<string, unknown>[] }) => {
  selectedRows.value = records
  emit('selection-change', records)
}

// 暴露给父组件 / 单元测试访问
defineExpose({ tableConfig, handleSelectionChange, selectedRows })
</script>

<template>
  <vxe-table
    v-bind="tableConfig"
    :data="data"
    @checkbox-change="handleSelectionChange"
    @checkbox-all="handleSelectionChange"
  >
    <vxe-column v-if="checkbox" type="checkbox" width="50" fixed="left" />
    <vxe-column
      v-for="col in columns"
      :key="col.field"
      :field="col.field"
      :title="col.title"
      :width="col.width"
      :align="col.align || 'left'"
      :formatter="col.formatter"
      show-overflow
    />
  </vxe-table>
</template>
