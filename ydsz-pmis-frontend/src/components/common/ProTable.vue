<!--
  @fileoverview ProTable 通用高级表格组件
  @description 集成 el-table / el-pagination / 搜索表单 / 工具栏 / 空状态：
  - Props: columns / data / loading / total / page / size / selection / toolbar
            / paginationLayout / pageSizes / maxHeight / height / rowKey
  - Emits: update:page / update:size / selection-change / sort-change
  - 泛型 T 为行数据类型；配合 useTable composable 使用
  @module components/common/ProTable
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts" generic="T extends Record<string, unknown> = Record<string, unknown>">
/**
 * ProTable 通用表格组件
 *
 * 集成搜索表单、工具栏、分页、行选择、排序、空状态等功能，
 * 配合 useTable composable 使用，减少页面样板代码。
 *
 * 泛型参数 T 为行数据类型，默认 Record<string, unknown>，使用时可传入具体类型以获得类型提示：
 * ```vue
 * <ProTable<UserVO> :data="users" :columns="cols" />
 * ```
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Document } from '@element-plus/icons-vue'
import type { VNode } from 'vue'
import type { TableInstance } from 'element-plus'

/** 列定义 */
export interface ProTableColumn<R = Record<string, unknown>> {
  /** 字段名 */
  prop: string
  /** 列标题 */
  label: string
  /** 列宽 */
  width?: number | string
  /** 是否可排序 */
  sortable?: boolean | 'custom'
  /** 固定列 */
  fixed?: 'left' | 'right'
  /** 格式化函数 */
  formatter?: (row: R) => string | VNode
  /** 自定义列插槽名（对应 template #[slot]） */
  slot?: string
  /** 最小宽度 */
  minWidth?: number | string
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 表头对齐方式 */
  headerAlign?: 'left' | 'center' | 'right'
  /** 是否显示溢出提示 */
  showOverflowTooltip?: boolean
}

const { t } = useI18n()

withDefaults(
  defineProps<{
    /** 列配置 */
    columns: ProTableColumn<T>[]
    /** 表格数据 */
    data: T[]
    /** 加载状态 */
    loading?: boolean
    /** 总条数 */
    total?: number
    /** 当前页码（支持 v-model:page） */
    page?: number
    /** 每页条数（支持 v-model:size） */
    size?: number
    /** 是否显示选择列 */
    selection?: boolean
    /** 是否显示工具栏区域 */
    toolbar?: boolean
    /** 分页布局 */
    paginationLayout?: string
    /** 每页条数选项 */
    pageSizes?: number[]
    /** 表格最大高度 */
    maxHeight?: number | string
    /** 表格高度 */
    height?: number | string
    /** 行 key 字段（用于 row-key） */
    rowKey?: string
    /** 是否显示分页 */
    showPagination?: boolean
    /** 空状态预设 */
    emptyPreset?: 'list' | 'search' | 'network' | 'noPermission' | 'custom'
    /** 是否显示边框 */
    border?: boolean
    /** 是否斑马纹 */
    stripe?: boolean
  }>(),
  {
    loading: false,
    total: 0,
    page: 1,
    size: 10,
    selection: false,
    toolbar: true,
    paginationLayout: 'total, sizes, prev, pager, next, jumper',
    pageSizes: () => [10, 20, 50, 100],
    rowKey: 'id',
    showPagination: true,
    emptyPreset: 'list',
    border: true,
    stripe: false,
  },
)

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'update:size', size: number): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
  (e: 'selection-change', selection: T[]): void
  (e: 'sort-change', payload: { prop: string; order: string }): void
}>()

const tableRef = ref<TableInstance | null>(null)

/** 当前选中行 */
const selectedRows = ref<T[]>([])

/** 页码变化 */
function handlePageChange(page: number) {
  emit('update:page', page)
  emit('page-change', page)
}

/** 每页条数变化 */
function handleSizeChange(size: number) {
  emit('update:size', size)
  emit('size-change', size)
}

/** 选择变化 */
function handleSelectionChange(selection: T[]) {
  selectedRows.value = selection
  emit('selection-change', selection)
}

/** 排序变化 */
function handleSortChange({ prop, order }: { prop: string | null; order: string | null }) {
  if (prop) {
    emit('sort-change', { prop, order: order || '' })
  }
}

/** 清空选择 */
function clearSelection() {
  tableRef.value?.clearSelection?.()
  selectedRows.value = []
}

/** 获取选中行 */
function getSelectedRows() {
  return selectedRows.value
}

/** 获取表格 ref */
function getTableRef() {
  return tableRef.value
}

defineExpose({
  clearSelection,
  getSelectedRows,
  getTableRef,
})
</script>

<template>
  <div class="pro-table">
    <!-- 搜索表单区域 -->
    <div v-if="$slots.search" class="pro-table__search">
      <slot name="search" />
    </div>

    <!-- 工具栏区域 -->
    <div v-if="toolbar && ($slots.toolbar || $slots['toolbar-left'] || $slots['toolbar-right'])" class="pro-table__toolbar">
      <div class="pro-table__toolbar-left">
        <slot name="toolbar-left" />
        <slot name="toolbar" />
      </div>
      <div class="pro-table__toolbar-right">
        <slot name="toolbar-right" />
      </div>
    </div>

    <!-- 表格主体 -->
    <div class="pro-table__body">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="data"
        :row-key="rowKey"
        :height="height"
        :max-height="maxHeight"
        :border="border"
        :stripe="stripe"
        style="width: 100%"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <!-- 选择列 -->
        <el-table-column
          v-if="selection"
          type="selection"
          width="55"
          fixed="left"
        />

        <!-- 数据列 -->
        <template v-for="col in columns" :key="col.prop">
          <!-- 有自定义插槽的列 -->
          <el-table-column
            v-if="col.slot"
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :fixed="col.fixed"
            :sortable="col.sortable"
            :align="col.align"
            :header-align="col.headerAlign"
            :show-overflow-tooltip="col.showOverflowTooltip ?? true"
          >
            <template #default="scope">
              <slot :name="col.slot" v-bind="scope" />
            </template>
          </el-table-column>

          <!-- 有 formatter 的列 -->
          <el-table-column
            v-else-if="col.formatter"
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :fixed="col.fixed"
            :sortable="col.sortable"
            :align="col.align"
            :header-align="col.headerAlign"
            :show-overflow-tooltip="col.showOverflowTooltip ?? true"
          >
            <template #default="{ row }">
              <template v-if="typeof col.formatter!(row) === 'string'">
                {{ col.formatter!(row) }}
              </template>
              <component :is="col.formatter!(row)" v-else />
            </template>
          </el-table-column>

          <!-- 普通列 -->
          <el-table-column
            v-else
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :fixed="col.fixed"
            :sortable="col.sortable"
            :align="col.align"
            :header-align="col.headerAlign"
            :show-overflow-tooltip="col.showOverflowTooltip ?? true"
          />
        </template>

        <!-- 空状态 -->
        <template #empty>
          <div class="pro-table__empty">
            <slot name="empty">
              <div class="pro-table__empty-default">
                <el-icon :size="48" class="pro-table__empty-icon">
                  <Document />
                </el-icon>
                <span>{{ t('common.empty') }}</span>
              </div>
            </slot>
          </div>
        </template>

        <!-- 透传 append 插槽 -->
        <template v-if="$slots.append" #append>
          <slot name="append" />
        </template>
      </el-table>
    </div>

    <!-- 分页 -->
    <div v-if="showPagination && total > 0" class="pro-table__pagination">
      <slot name="pagination">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          :page-sizes="pageSizes"
          :layout="paginationLayout"
          background
          @update:current-page="handlePageChange"
          @update:page-size="handleSizeChange"
        />
      </slot>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.pro-table {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__search {
    padding: 16px 16px 0;
    background: var(--el-bg-color, #fff);
    border-radius: 4px;
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 0 8px;

    &-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    &-right {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  &__body {
    width: 100%;
  }

  &__empty {
    padding: 32px 0;
    text-align: center;

    &-default {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      color: var(--el-text-color-secondary, #909399);
      font-size: 14px;
    }

    &-icon {
      color: var(--el-color-info-light-3, #c0c4cc);
    }
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    padding: 12px 0 0;
  }
}
</style>
