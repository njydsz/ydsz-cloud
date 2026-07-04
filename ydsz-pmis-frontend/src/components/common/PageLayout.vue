<!--
  @file 通用列表页布局
  @description 提供「搜索表单 + 工具栏 + 表格 + 分页」四段式布局，页面只需通过 slot 注入内容即可
  @module components/common/PageLayout
-->
<script setup lang="ts">
/**
 * 通用列表页布局
 *
 * 提供列表页标准的「搜索表单 + 工具栏 + 表格 + 分页」四段式布局，
 * 页面只需通过 slot 注入内容即可，避免重复布局代码。
 *
 * 使用示例：
 *   <PageLayout
 *     :query="query"
 *     :list="list"
 *     :total="total"
 *     :loading="loading"
 *     :page-sizes="[10, 20, 50]"
 *     @query="handleQuery"
 *     @reset="resetQuery"
 *     @page-change="handlePageChange"
 *   >
 *     <template #search>
 *       <el-form-item label="关键字">...</el-form-item>
 *     </template>
 *     <template #toolbar>
 *       <el-button type="primary" @click="openCreate">新增</el-button>
 *     </template>
 *     <template #table>
 *       <vxe-table :data="list" :loading="loading" border>
 *         <vxe-column ... />
 *       </vxe-table>
 *     </template>
 *   </PageLayout>
 */
import type { PropType } from 'vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SkeletonTable from './SkeletonTable.vue'
import BatchToolbar from './BatchToolbar.vue'
import EmptyState, { type EmptyPreset } from './EmptyState.vue'

const { t } = useI18n()

/** 批量操作按钮配置 */
interface BatchAction {
  label: string
  type?: 'primary' | 'warning' | 'danger' | 'info'
  icon?: string
  permission?: string
  handler: () => void
}

const props = defineProps({
  query: { type: Object as PropType<Record<string, any>>, default: () => ({ page: 1, size: 10 }) },
  list: { type: Array as PropType<unknown[]>, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
  /** 表格区域最小宽度，用于内嵌 vxe-table */
  tableMinHeight: { type: [Number, String], default: 400 },
  /** 分页条数选项 */
  pageSizes: { type: Array as PropType<number[]>, default: () => [10, 20, 50, 100] },
  /** 分页布局 */
  paginationLayout: { type: String, default: 'total, sizes, prev, pager, next, jumper' },
  /** 是否隐藏分页 */
  hidePagination: { type: Boolean, default: false },
  /** 是否隐藏搜索区 */
  hideSearch: { type: Boolean, default: false },
  /** 是否隐藏工具栏 */
  hideToolbar: { type: Boolean, default: false },
  /** 卡片是否无内边距（适用于自带 padding 的自定义内容） */
  noPadding: { type: Boolean, default: false },
  /** 加载态展示模式：mask=遮罩（翻页时使用），skeleton=骨架屏（首次加载时使用） */
  loadingType: { type: String as PropType<'mask' | 'skeleton'>, default: 'mask' },
  /** 骨架屏行数（loadingType='skeleton' 时生效） */
  skeletonRows: { type: Number, default: 5 },
  /** 骨架屏列数（loadingType='skeleton' 时生效） */
  skeletonColumns: { type: Number, default: 6 },
  /** 当前选中的行数据，非空时在表格上方展示批量操作工具栏 */
  selection: { type: Array as PropType<any[]>, default: () => [] },
  /** 批量操作按钮配置，传给 BatchToolbar 的 actions */
  batchActions: { type: Array as PropType<BatchAction[]>, default: () => [] },
  /** H16.4：空状态预设场景；空字符串表示不启用（使用 table 自带空状态），可选值 list/search/network/noPermission */
  emptyPreset: { type: String as PropType<'' | 'list' | 'search' | 'network' | 'noPermission'>, default: '' },
  /** H16.4：空状态 CTA 按钮文本；不传则隐藏 */
  emptyActionText: { type: String, default: '' },
})

const emit = defineEmits<{
  (e: 'query'): void
  (e: 'reset'): void
  (e: 'page-change'): void
  (e: 'refresh'): void
  (e: 'update:query', value: Record<string, any>): void
  /** 清空选择（点击 BatchToolbar「清空选择」时触发） */
  (e: 'clear-selection'): void
  /** H16.4：空状态 CTA 按钮点击 */
  (e: 'empty-action'): void
}>()

// el-pagination 不允许直接 v-model 外部 prop，
// 通过本地计算属性代理读写,避免 vue/no-mutating-props 违规
const currentPage = computed<number>({
  get: () => Number(props.query.page) || 1,
  set: (v) => emit('update:query', { ...props.query, page: v }),
})
const pageSize = computed<number>({
  get: () => Number(props.query.size) || 10,
  set: (v) => emit('update:query', { ...props.query, size: v }),
})

function onQuery() {
  emit('query')
}
function onReset() {
  emit('reset')
}
function onPageChange() {
  emit('page-change')
}
function onRefresh() {
  emit('refresh')
}
function onClearSelection() {
  emit('clear-selection')
}
function onEmptyAction() {
  emit('empty-action')
}

/**
 * 骨架屏展示条件：
 *   loadingType='skeleton' 且处于加载中 且当前列表为空（首次加载）。
 * 翻页时 list 非空，此时表格区走 slot 自身的遮罩 loading，不再展示骨架屏。
 */
const showSkeleton = computed(
  () => props.loadingType === 'skeleton' && props.loading && props.list.length === 0,
)

/**
 * H16.4：空状态展示条件
 *   配置了 emptyPreset 且非加载中 且列表为空。
 * 骨架屏优先级更高，加载中不展示空状态。
 */
const showEmpty = computed(
  () => props.emptyPreset !== '' && !props.loading && props.list.length === 0,
)

/**
 * H16.4：传给 EmptyState 的 preset（已剔除空字符串，'' 时回退默认 'list'）。
 * showEmpty 已保证渲染时 emptyPreset 必非空，这里仅做类型收敛。
 */
const resolvedEmptyPreset = computed<EmptyPreset>(
  () => (props.emptyPreset || 'list') as EmptyPreset,
)
</script>

<template>
  <div class="page-layout">
    <el-card v-if="title" shadow="never" class="mb-2">
      <div class="text-lg font-semibold">{{ title }}</div>
    </el-card>
    <el-card shadow="never" :body-style="noPadding ? { padding: 0 } : undefined">
      <!-- 搜索区 -->
      <el-form
        v-if="!hideSearch"
        inline
        :model="query"
        class="search-form"
        role="search"
      >
        <slot name="search" />
        <el-form-item>
          <el-button type="primary" :icon="'Search'" :aria-label="t('common.query')" @click="onQuery">{{ t('common.query') }}</el-button>
          <el-button :icon="'RefreshLeft'" :aria-label="t('common.resetQuery')" @click="onReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 工具栏 -->
      <div v-if="!hideToolbar" class="toolbar">
        <slot name="toolbar" />
        <div class="toolbar-right">
          <slot name="toolbar-right">
            <el-button :icon="'Refresh'" circle :aria-label="t('common.refreshList')" @click="onRefresh" />
          </slot>
        </div>
      </div>

      <!-- 批量操作工具栏：选中行时在表格上方展示 -->
      <div v-if="selection.length > 0" class="batch-area">
        <slot name="batch-actions" :selection="selection" :count="selection.length">
          <BatchToolbar
            :selected-count="selection.length"
            :actions="batchActions"
            @clear="onClearSelection"
          />
        </slot>
      </div>

      <!-- 表格区：首次加载且开启骨架模式时以骨架屏占位；空状态展示 EmptyState；其余渲染 table 插槽 -->
      <div class="table-area" :style="{ minHeight: typeof tableMinHeight === 'number' ? `${tableMinHeight}px` : tableMinHeight }">
        <SkeletonTable
          v-if="showSkeleton"
          :rows="skeletonRows"
          :columns="skeletonColumns"
        />
        <EmptyState
          v-else-if="showEmpty"
          :preset="resolvedEmptyPreset"
          :action-text="emptyActionText"
          :block-height="Number(tableMinHeight) || 0"
          @action="onEmptyAction"
        />
        <slot v-else name="table" />
      </div>

      <!-- 分页 -->
      <div v-if="!hidePagination" class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="pageSizes"
          :layout="paginationLayout"
          background
          @current-change="onPageChange"
          @size-change="onPageChange"
        />
      </div>
    </el-card>
    <slot name="footer" />
  </div>
</template>

<style lang="scss" scoped>
.page-layout {
  .search-form { margin-bottom: $spacing-md; }
  .toolbar {
    display: flex;
    align-items: center;
    margin-bottom: $spacing-md;
    gap: 8px;
    .toolbar-right {
      margin-left: auto;
      display: flex;
      gap: 8px;
    }
  }
  .table-area { width: 100%; }
  .pagination { margin-top: $spacing-md; display: flex; justify-content: flex-end; }
}
</style>
