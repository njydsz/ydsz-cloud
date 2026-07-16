<!--
  @fileoverview 通用列表页布局
  @description 提供「搜索表单 + 工具栏 + 表格 + 分页」四段式布局：
  - Props: query / list / total / loading / title / tableMinHeight / tableHeight
            / virtualScroll / pageSizes / paginationLayout / hidePagination
            / hideSearch / hideToolbar / noPadding / loadingType
  - Emits: query / reset / page-change / size-change
  - Slots: search / toolbar / table / default(右上角)
  - 内置 BatchToolbar / SkeletonTable / EmptyState 联动
  @module components/common/PageLayout
  @author ydsz-team
  @since 1.0.0
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
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
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
  query: { type: Object as PropType<Record<string, unknown>>, default: () => ({ page: 1, size: 10 }) },
  list: { type: Array as PropType<unknown[]>, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  /** 最近一次请求的错误信息（非空字符串表示加载失败，优先级高于 emptyPreset） */
  error: { type: String as PropType<string | null>, default: null },
  title: { type: String, default: '' },
  /** 表格区域最小宽度，用于内嵌 vxe-table */
  tableMinHeight: { type: [Number, String], default: 400 },
  /** P0-E3: 表格固定高度（虚拟滚动前提），通过 slot scope 暴露给 #table 插槽 */
  tableHeight: { type: [Number, String], default: 480 },
  /** P0-E3: 是否启用虚拟滚动配置（通过 slot scope 暴露 scrollY 配置对象） */
  virtualScroll: { type: Boolean, default: true },
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
  selection: { type: Array as PropType<unknown[]>, default: () => [] },
  /** 批量操作按钮配置，传给 BatchToolbar 的 actions */
  batchActions: { type: Array as PropType<BatchAction[]>, default: () => [] },
  /** H16.4：空状态预设场景；空字符串表示不启用（使用 table 自带空状态），可选值 list/search/network/noPermission */
  emptyPreset: { type: String as PropType<'' | 'list' | 'search' | 'network' | 'noPermission'>, default: '' },
  /** H16.4：空状态 CTA 按钮文本；不传则隐藏 */
  emptyActionText: { type: String, default: '' },
  /** 搜索表单是否可折叠（UX-C3，默认 false） */
  searchCollapsible: { type: Boolean, default: false },
  /** 搜索表单折叠时展示的项数（默认 3，超出部分收起） */
  searchCollapseCount: { type: Number, default: 3 },
  /** 搜索表单默认是否收起（UX-C3，默认 true 收起） */
  searchDefaultCollapsed: { type: Boolean, default: true },
})

const emit = defineEmits<{
  (e: 'query'): void
  (e: 'reset'): void
  (e: 'page-change'): void
  (e: 'refresh'): void
  (e: 'update:query', value: Record<string, unknown>): void
  /** 清空选择（点击 BatchToolbar「清空选择」时触发） */
  (e: 'clear-selection'): void
  /** H16.4：空状态 CTA 按钮点击 */
  (e: 'empty-action'): void
  /** 加载失败时点击「重试」按钮触发 */
  (e: 'retry'): void
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

function onRetry() {
  emit('retry')
}

// ===== 搜索表单折叠（UX-C3） =====
/** 搜索表单当前是否收起 */
const searchCollapsed = ref(props.searchDefaultCollapsed)

/** 切换搜索表单展开/收起 */
function toggleSearchCollapse() {
  searchCollapsed.value = !searchCollapsed.value
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
 * 加载失败展示条件（C6 修复）：
 *   error 非空 且 非加载中 且列表为空。
 * 翻页失败时保留上一次数据（list 非空），仅通过 toast 提示，不覆盖表格。
 * 首次加载失败时 list 为空，展示 network preset + 重试按钮。
 */
const showError = computed(
  () => !!props.error && !props.loading && props.list.length === 0,
)

/**
 * H16.4：空状态展示条件
 *   配置了 emptyPreset 且非加载中 且非加载失败 且列表为空。
 * 骨架屏与错误状态优先级更高。
 */
const showEmpty = computed(
  () => props.emptyPreset !== '' && !props.loading && !showError.value && props.list.length === 0,
)

/**
 * H16.4：传给 EmptyState 的 preset（已剔除空字符串，'' 时回退默认 'list'）。
 * showEmpty 已保证渲染时 emptyPreset 必非空，这里仅做类型收敛。
 */
const resolvedEmptyPreset = computed<EmptyPreset>(
  () => (props.emptyPreset || 'list') as EmptyPreset,
)

/**
 * P0-E3: 通过 slot scope 暴露给 #table 插槽的虚拟滚动配置
 * 页面可在 vxe-table 上直接绑定：:height="tableProps.height" :scroll-y="tableProps.scrollY"
 */
const tableProps = computed(() => ({
  height: props.tableHeight,
  scrollY: props.virtualScroll ? { enabled: true, gt: 50 } : { enabled: false },
}))
</script>

<template>
  <div class="page-layout">
    <el-card v-if="title" shadow="never" class="mb-2">
      <div class="text-lg font-semibold">{{ title }}</div>
    </el-card>
    <el-card shadow="never" :body-style="noPadding ? { padding: 0 } : undefined">
      <!-- 搜索区（UX-D1: loading 期间禁用交互；UX-C3: 支持展开/收起） -->
      <el-form
        v-if="!hideSearch"
        inline
        :model="query"
        :class="['search-form', { 'is-loading': loading }]"
        :disabled="loading"
        role="search"
      >
        <div
          class="search-form__items"
          :class="{
            'is-collapsed': searchCollapsible && searchCollapsed,
            [`collapse-count-${searchCollapseCount}`]: searchCollapsible && searchCollapsed,
          }"
        >
          <slot name="search" />
        </div>
        <el-form-item class="search-form__actions">
          <el-button type="primary" :icon="'Search'" :loading="loading" :aria-label="t('common.query')" @click="onQuery">{{ t('common.query') }}</el-button>
          <el-button :icon="'RefreshLeft'" :aria-label="t('common.resetQuery')" @click="onReset">{{ t('common.reset') }}</el-button>
          <el-button
            v-if="searchCollapsible"
            link
            type="primary"
            :aria-label="searchCollapsed ? t('common.expand') : t('common.collapse')"
            @click="toggleSearchCollapse"
          >
            {{ searchCollapsed ? t('common.expand') : t('common.collapse') }}
            <el-icon class="el-icon--right">
              <ArrowDown v-if="searchCollapsed" />
              <ArrowUp v-else />
            </el-icon>
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 工具栏 -->
      <div v-if="!hideToolbar" class="toolbar">
        <slot name="toolbar" />
        <div class="toolbar-right">
          <slot name="toolbar-right">
            <el-button :icon="'Refresh'" circle :loading="loading" :aria-label="t('common.refreshList')" @click="onRefresh" />
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

      <!-- 表格区：骨架屏 > 加载失败 > 空状态 > table 插槽 -->
      <div class="table-area" :style="{ minHeight: typeof tableMinHeight === 'number' ? `${tableMinHeight}px` : tableMinHeight }">
        <SkeletonTable
          v-if="showSkeleton"
          :rows="skeletonRows"
          :columns="skeletonColumns"
        />
        <EmptyState
          v-else-if="showError"
          preset="network"
          :action-text="t('common.retry')"
          :block-height="Number(tableMinHeight) || 0"
          @action="onRetry"
        />
        <EmptyState
          v-else-if="showEmpty"
          :preset="resolvedEmptyPreset"
          :action-text="emptyActionText"
          :block-height="Number(tableMinHeight) || 0"
          @action="onEmptyAction"
        />
        <slot v-else name="table" :table-props="tableProps" />
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
  .search-form {
    margin-bottom: $spacing-md;
    /* UX-D1: loading 期间降低搜索区视觉权重，提示用户正在处理 */
    &.is-loading {
      opacity: 0.7;
      transition: opacity 0.2s ease;
    }

    /* UX-C3: 搜索表单折叠容器 */
    &__items {
      display: inline;

      /* 折叠状态：隐藏第 N+1 个及以后的 el-form-item */
      &.is-collapsed {
        /* 预生成 1-10 的折叠数量 class，避免 :nth-child 不支持 calc 的问题 */
        @for $i from 1 through 10 {
          &.collapse-count-#{$i} .el-form-item:nth-child(n + #{$i + 1}) {
            display: none;
          }
        }
      }
    }

    /* 操作按钮组始终可见 */
    &__actions {
      margin-right: 0 !important;
    }
  }
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
