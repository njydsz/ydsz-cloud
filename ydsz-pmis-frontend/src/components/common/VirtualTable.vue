<!--
  @file 通用虚拟滚动表格组件
  @description 基于 vxe-table 封装的大数据量虚拟滚动表格，数据量超过阈值自动启用虚拟滚动
  @module components/common/VirtualTable
-->
<script lang="ts">
/** 列配置（导出供外部使用） */
export interface ColumnConfig {
  field: string
  title: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  /** 纯文本格式化器（与 slot 二选一，签名兼容 vxe-table） */
  formatter?: (params: { row: Record<string, unknown>; column: unknown; cellValue: unknown }) => string
  /** 是否启用自定义插槽渲染（父组件通过 #col-{field} 传入） */
  slot?: boolean
  /** 是否固定列（left/right） */
  fixed?: 'left' | 'right'
  /** 是否可排序 */
  sortable?: boolean
}
</script>

<script setup lang="ts">
/**
 * 虚拟滚动大数据表格
 *
 * 基于 vxe-table 封装，当数据量超过 50 行时自动启用纵向虚拟滚动，
 * 保证万级数据渲染流畅。支持复选框选择、formatter 文本格式化、自定义插槽渲染。
 *
 * 自定义插槽用法（P3-1 扩展）：
 *   在 ColumnConfig 中设置 `slot: true`，然后通过 `#col-{field}` 传递自定义渲染：
 *   <VirtualTable :data="list" :columns="cols">
 *     <template #col-status="{ row }">
 *       <el-tag>{{ row.status }}</el-tag>
 *     </template>
 *     <template #col-actions="{ row }">
 *       <el-button @click="edit(row)">编辑</el-button>
 *     </template>
 *   </VirtualTable>
 */
import { ref, computed } from 'vue'
import { VxeTable, VxeColumn } from 'vxe-table'
import type { VxeTableProps } from 'vxe-table'

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
  /** 复选框可选判断方法：返回 false 则该行不可勾选（对应 el-table :selectable） */
  checkMethod?: (row: Record<string, unknown>) => boolean
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
  checkboxConfig: props.checkbox
    ? { highlight: true, range: true, checkMethod: props.checkMethod }
    : undefined,
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
      :fixed="col.fixed"
      :sortable="col.sortable"
      show-overflow
    >
      <!-- 自定义插槽：父组件通过 #col-{field} 传入渲染内容 -->
      <template v-if="col.slot" #default="slotData">
        <slot :name="`col-${col.field}`" v-bind="slotData" />
      </template>
    </vxe-column>
  </vxe-table>
</template>
