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
})

const emit = defineEmits<{
  (e: 'query'): void
  (e: 'reset'): void
  (e: 'page-change'): void
  (e: 'refresh'): void
  (e: 'update:query', value: Record<string, any>): void
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
      >
        <slot name="search" />
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="onQuery">查询</el-button>
          <el-button :icon="'RefreshLeft'" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 工具栏 -->
      <div v-if="!hideToolbar" class="toolbar">
        <slot name="toolbar" />
        <div class="toolbar-right">
          <slot name="toolbar-right">
            <el-button :icon="'Refresh'" circle @click="onRefresh" />
          </slot>
        </div>
      </div>

      <!-- 表格区 -->
      <div class="table-area" :style="{ minHeight: typeof tableMinHeight === 'number' ? `${tableMinHeight}px` : tableMinHeight }">
        <slot name="table" />
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
