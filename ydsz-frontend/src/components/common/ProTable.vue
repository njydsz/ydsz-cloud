<!--
  @fileoverview ProTable 通用高级表格组件（批次 29-2 增强）
  @description 集成 el-table / el-pagination / 搜索表单 / 工具栏 / 空状态 / 列设置 / 密度切换 / 合计行 / 行展开：
  - Props: columns / data / loading / total / page / size / selection / toolbar
            / paginationLayout / pageSizes / maxHeight / height / rowKey
            / columnSetting / density / summary / expandable / emptyCta
  - Emits: update:page / update:size / selection-change / sort-change / column-setting-change / density-change
  - 泛型 T 为行数据类型；配合 useTable composable 使用

  批次 29-2 新增能力（对齐企业级高级表格规范）：
  1. 列设置（columnSetting）：显隐切换 + 拖拽排序 + 宽度持久化（localStorage）
  2. 密度切换（density）：large/default/small 三档，影响行高
  3. 合计行（summary）：通过 summaryMethod 自定义合计逻辑
  4. 行展开（expandable）：通过 #expand 插槽渲染展开内容
  5. 空 CTA（emptyCta）：空状态时展示引导按钮
  @module components/common/ProTable
  @author ydsz-pmis-team
  @since 1.4.0
-->
<script setup lang="ts" generic="T extends Record<string, unknown> = Record<string, unknown>">
/**
 * ProTable 通用表格组件
 *
 * 集成搜索表单、工具栏、分页、行选择、排序、空状态、列设置、密度切换、合计行、行展开等功能，
 * 配合 useTable composable 使用，减少页面样板代码。
 *
 * 泛型参数 T 为行数据类型，默认 Record<string, unknown>，使用时可传入具体类型以获得类型提示：
 * ```vue
 * <ProTable<UserVO> :data="users" :columns="cols" />
 * ```
 */
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Document, Setting, Rank, FullScreen } from '@element-plus/icons-vue'
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
  /** 列是否默认隐藏（列设置功能使用） */
  defaultHidden?: boolean
  /** 列是否禁用显隐切换（如选择列、操作列） */
  disableHidden?: boolean
  /** P0-3: 响应式断点下自动隐藏（传入断点名称数组，如 ['xs', 'sm'] 表示在这些断点下隐藏） */
  responsiveHidden?: Array<'xs' | 'sm' | 'md' | 'lg' | 'xl'>
}

/** 表格密度 */
type TableDensity = 'large' | 'default' | 'small'

/** 列设置项（用于列设置弹窗的渲染状态） */
interface ColumnSettingItem {
  prop: string
  label: string
  visible: boolean
  disableHidden?: boolean
}

const { t } = useI18n()

const props = withDefaults(
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
    /** 是否启用列设置（批次 29-2） */
    columnSetting?: boolean
    /** 列设置持久化 key（传则持久化到 localStorage，不传则仅内存） */
    columnSettingKey?: string
    /** 是否启用密度切换（批次 29-2） */
    density?: boolean
    /** 默认密度（批次 29-2） */
    defaultDensity?: TableDensity
    /** 是否显示合计行（批次 29-2） */
    showSummary?: boolean
    /** 合计方法（批次 29-2） */
    summaryMethod?: (param: { columns: any[]; data: T[] }) => string[]
    /** 是否支持行展开（批次 29-2） */
    expandable?: boolean
    /** 空状态 CTA 按钮文案（批次 29-2） */
    emptyCtaText?: string
    /** 是否显示全屏切换（批次 29-2） */
    fullscreen?: boolean
    /** 是否启用批量操作工具栏（选中行时浮现） */
    batchActions?: boolean
    /** 批量操作工具栏位置（top / bottom） */
    batchActionsPosition?: 'top' | 'bottom'
    /** P2-11: 大数据量阈值（当 data 长度超过此值且未开启分页时，显示警告并启用优化） */
    largeDataThreshold?: number
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
    columnSetting: false,
    density: false,
    defaultDensity: 'default',
    showSummary: false,
    expandable: false,
    fullscreen: false,
    batchActions: false,
    batchActionsPosition: 'top',
    largeDataThreshold: 500,
  },
)

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'update:size', size: number): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
  (e: 'selection-change', selection: T[]): void
  (e: 'sort-change', payload: { prop: string; order: string }): void
  (e: 'empty-cta'): void
  (e: 'density-change', density: TableDensity): void
}>()

const tableRef = ref<TableInstance | null>(null)

/** 当前选中行 */
const selectedRows = ref<T[]>([])

// ===== 密度切换（批次 29-2） =====
const currentDensity = ref<TableDensity>(props.defaultDensity)

/** 密度对应的 size 属性值 */
const densitySize = computed(() => currentDensity.value)

/** 切换密度 */
function handleDensityChange(d: TableDensity) {
  currentDensity.value = d
  emit('density-change', d)
}

// ===== 列设置（批次 29-2） =====
/** 列设置持久化存储 key */
const columnSettingStorageKey = computed(
  () => props.columnSettingKey ? `pmis:pro-table:columns:${props.columnSettingKey}` : '',
)

/** 从 localStorage 读取持久化的列设置 */
function loadPersistedColumnSetting(): Record<string, { visible: boolean; order: number }> | null {
  if (!columnSettingStorageKey.value) return null
  try {
    const raw = localStorage.getItem(columnSettingStorageKey.value)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 持久化列设置到 localStorage */
function persistColumnSetting(map: Record<string, { visible: boolean; order: number }>) {
  if (!columnSettingStorageKey.value) return
  try {
    localStorage.setItem(columnSettingStorageKey.value, JSON.stringify(map))
  } catch {
    /* localStorage 可能不可用 */
  }
}

/** 列设置项列表（可拖拽排序） */
const columnSettingList = ref<ColumnSettingItem[]>([])

/** 初始化列设置项 */
function initColumnSetting() {
  const persisted = loadPersistedColumnSetting()
  columnSettingList.value = props.columns.map((col) => {
    const persistedItem = persisted?.[col.prop]
    return {
      prop: col.prop,
      label: col.label,
      visible: persistedItem ? persistedItem.visible : !col.defaultHidden,
      disableHidden: col.disableHidden,
    }
  })
  // 若有持久化顺序，按持久化顺序重排
  if (persisted) {
    const orderMap = new Map<string, number>()
    Object.entries(persisted).forEach(([prop, val]) => {
      orderMap.set(prop, val.order ?? 999)
    })
    columnSettingList.value.sort((a, b) => (orderMap.get(a.prop) ?? 999) - (orderMap.get(b.prop) ?? 999))
  }
}

/** 列设置变更时持久化 + 同步 */
function syncColumnSetting() {
  const map: Record<string, { visible: boolean; order: number }> = {}
  columnSettingList.value.forEach((item, idx) => {
    map[item.prop] = { visible: item.visible, order: idx }
  })
  persistColumnSetting(map)
}

/** 重置列设置 */
function resetColumnSetting() {
  columnSettingList.value = props.columns.map((col) => ({
    prop: col.prop,
    label: col.label,
    visible: !col.defaultHidden,
    disableHidden: col.disableHidden,
  }))
  syncColumnSetting()
}

/** P0-3: 当前视口宽度 */
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)

/** P0-3: 监听窗口尺寸变化 */
if (typeof window !== 'undefined') {
  let resizeTimer: ReturnType<typeof setTimeout> | null = null
  window.addEventListener('resize', () => {
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      viewportWidth.value = window.innerWidth
    }, 150)
  })
}

/** P0-3: 判断当前视口是否在指定断点范围内 */
function isInBreakpoint(bp: 'xs' | 'sm' | 'md' | 'lg' | 'xl'): boolean {
  const breakpoints: Record<string, [number, number]> = {
    xs: [0, 479],
    sm: [480, 767],
    md: [768, 991],
    lg: [992, 1199],
    xl: [1200, 99999],
  }
  const range = breakpoints[bp]
  if (!range) return false
  return viewportWidth.value >= range[0] && viewportWidth.value <= range[1]
}

/** 实际渲染的列（过滤掉隐藏的列，按列设置顺序排序） */
const visibleColumns = computed<ProTableColumn<T>[]>(() => {
  let cols = props.columns
  if (props.columnSetting) {
    const visibleProps = new Set(
      columnSettingList.value.filter((c) => c.visible).map((c) => c.prop),
    )
    // 按列设置顺序排序
    const orderMap = new Map<string, number>()
    columnSettingList.value.forEach((item, idx) => orderMap.set(item.prop, idx))
    cols = cols
      .filter((col) => visibleProps.has(col.prop))
      .sort((a, b) => (orderMap.get(a.prop) ?? 999) - (orderMap.get(b.prop) ?? 999))
  }
  // P0-3: 响应式列隐藏
  return cols.filter((col) => {
    if (!col.responsiveHidden || col.responsiveHidden.length === 0) return true
    return !col.responsiveHidden.some((bp) => isInBreakpoint(bp))
  })
})

// 列配置变化时重新初始化列设置
watch(
  () => props.columns,
  () => {
    if (props.columnSetting) initColumnSetting()
  },
  { immediate: true },
)

// ===== 全屏切换（批次 29-2） =====
const isFullscreen = ref(false)

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

/** 表格容器类（全屏时固定定位） */
const tableContainerClass = computed(() => ({
  'pro-table--fullscreen': isFullscreen.value,
}))

// ===== 分页与选择 =====
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

/** 空状态 CTA 点击 */
function handleEmptyCta() {
  emit('empty-cta')
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
  toggleFullscreen,
  resetColumnSetting,
})

/** P2-11: 大数据量警告 */
const showLargeDataWarning = computed(
  () =>
    !props.showPagination &&
    props.data.length > props.largeDataThreshold,
)

/** P2-11: 是否启用大数据优化（lazy 渲染） */
const isLargeData = computed(
  () => props.data.length > props.largeDataThreshold,
)

/** P2-11: 表格 lazy 属性（大数据量时启用懒渲染） */
const tableLazy = computed(() => isLargeData.value)

/** 是否显示批量操作栏 */
const showBatchBar = computed(
  () => props.batchActions && props.selection && selectedRows.value.length > 0,
)
</script>

<template>
  <div class="pro-table" :class="tableContainerClass">
    <!-- 搜索表单区域 -->
    <div v-if="$slots.search" class="pro-table__search">
      <slot name="search" />
    </div>

    <!-- 工具栏区域 -->
    <div
      v-if="toolbar && ($slots.toolbar || $slots['toolbar-left'] || $slots['toolbar-right'] || columnSetting || density || fullscreen)"
      class="pro-table__toolbar"
    >
      <div class="pro-table__toolbar-left">
        <slot name="toolbar-left" />
        <slot name="toolbar" />
      </div>
      <div class="pro-table__toolbar-right">
        <slot name="toolbar-right" />
        <!-- 密度切换（批次 29-2） -->
        <el-dropdown v-if="density" trigger="click" @command="handleDensityChange">
          <el-button :icon="Rank" circle :aria-label="t('common.tableDensity')" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="large" :class="{ 'is-active': currentDensity === 'large' }">
                {{ t('common.tableDensityLarge') }}
              </el-dropdown-item>
              <el-dropdown-item command="default" :class="{ 'is-active': currentDensity === 'default' }">
                {{ t('common.tableDensityDefault') }}
              </el-dropdown-item>
              <el-dropdown-item command="small" :class="{ 'is-active': currentDensity === 'small' }">
                {{ t('common.tableDensitySmall') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- 列设置（批次 29-2） -->
        <el-popover v-if="columnSetting" trigger="click" placement="bottom-end" :width="240">
          <template #reference>
            <el-button :icon="Setting" circle :aria-label="t('common.columnSetting')" />
          </template>
          <div class="pro-table__column-setting">
            <div class="pro-table__column-setting-header">
              <span>{{ t('common.columnSetting') }}</span>
              <el-button link type="primary" size="small" @click="resetColumnSetting">
                {{ t('common.reset') }}
              </el-button>
            </div>
            <div class="pro-table__column-setting-list">
              <div
                v-for="item in columnSettingList"
                :key="item.prop"
                class="pro-table__column-setting-item"
              >
                <el-checkbox
                  v-model="item.visible"
                  :disabled="item.disableHidden"
                  @change="syncColumnSetting"
                >
                  {{ item.label }}
                </el-checkbox>
              </div>
            </div>
          </div>
        </el-popover>
        <!-- 全屏切换（批次 29-2） -->
        <el-button v-if="fullscreen" :icon="FullScreen" circle @click="toggleFullscreen" />
      </div>
    </div>

    <!-- 批量操作工具栏（顶部） -->
    <Transition name="batch-bar-slide">
      <div
        v-if="showBatchBar && batchActionsPosition === 'top'"
        class="pro-table__batch-bar"
      >
        <div class="pro-table__batch-info">
          <span class="pro-table__batch-count">
            {{ t('common.batchSelected', { count: selectedRows.length }) }}
          </span>
          <el-button link type="primary" size="small" @click="clearSelection">
            {{ t('common.batchClear') }}
          </el-button>
        </div>
        <div class="pro-table__batch-actions">
          <slot name="batch-actions" :selection="selectedRows" :clear="clearSelection" />
        </div>
      </div>
    </Transition>

    <!-- P2-11: 大数据量警告 -->
    <el-alert
      v-if="showLargeDataWarning"
      type="warning"
      show-icon
      :closable="false"
      class="pro-table__large-data-warning"
    >
      当前数据量 {{ data.length }} 条已超过 {{ largeDataThreshold }} 条，已启用懒渲染优化。
      建议开启分页以获得更佳体验。
    </el-alert>

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
        :size="densitySize"
        :lazy="tableLazy"
        :show-summary="showSummary"
        :summary-method="summaryMethod"
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

        <!-- 行展开列（批次 29-2） -->
        <el-table-column v-if="expandable" type="expand">
          <template #default="scope">
            <slot name="expand" v-bind="scope" />
          </template>
        </el-table-column>

        <!-- 数据列（按 visibleColumns 渲染，支持列设置显隐与排序） -->
        <template v-for="col in visibleColumns" :key="col.prop">
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

        <!-- 空状态（批次 29-2：增加 CTA 按钮） -->
        <template #empty>
          <div class="pro-table__empty">
            <slot name="empty">
              <div class="pro-table__empty-default">
                <el-icon :size="48" class="pro-table__empty-icon">
                  <Document />
                </el-icon>
                <span>{{ t('common.empty') }}</span>
                <el-button
                  v-if="emptyCtaText"
                  type="primary"
                  size="small"
                  @click="handleEmptyCta"
                >
                  {{ emptyCtaText }}
                </el-button>
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

    <!-- 批量操作工具栏（底部） -->
    <Transition name="batch-bar-slide">
      <div
        v-if="showBatchBar && batchActionsPosition === 'bottom'"
        class="pro-table__batch-bar pro-table__batch-bar--bottom"
      >
        <div class="pro-table__batch-info">
          <span class="pro-table__batch-count">
            {{ t('common.batchSelected', { count: selectedRows.length }) }}
          </span>
          <el-button link type="primary" size="small" @click="clearSelection">
            {{ t('common.batchClear') }}
          </el-button>
        </div>
        <div class="pro-table__batch-actions">
          <slot name="batch-actions" :selection="selectedRows" :clear="clearSelection" />
        </div>
      </div>
    </Transition>

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
  gap: $spacing-md;

  // 全屏模式（批次 29-2）
  &--fullscreen {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: $z-index-modal;
    background: $bg-base;
    padding: $spacing-lg;
    overflow: auto;
  }

  &__search {
    padding: $spacing-md $spacing-md 0;
    background: $bg-white;
    border-radius: $border-radius-base;
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 0 $spacing-sm;

    &-left {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
    }

    &-right {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
    }
  }

  &__body {
    width: 100%;
  }

  // 列设置弹窗（批次 29-2）
  &__column-setting {
    &-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-bottom: $spacing-sm;
      margin-bottom: $spacing-sm;
      border-bottom: 1px solid $border-lighter;
      font-weight: 600;
      font-size: $font-size-base;
    }

    &-list {
      max-height: 320px;
      overflow-y: auto;
    }

    &-item {
      display: flex;
      align-items: center;
      padding: $spacing-xs 0;
      cursor: pointer;

      &:hover {
        background: $border-extra-light;
      }
    }
  }

  &__empty {
    padding: $spacing-xl 0;
    text-align: center;

    &-default {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: $spacing-base;
      color: $text-secondary;
      font-size: $font-size-base;
    }

    &-icon {
      color: $text-placeholder;
    }
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    padding: $spacing-base 0 0;
  }

  // 批量操作工具栏
  &__batch-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-md;
    background: var(--el-color-primary-light-9);
    border: 1px solid var(--el-color-primary-light-7);
    border-radius: $border-radius-base;
    gap: $spacing-md;

    &--bottom {
      margin-top: 0;
    }
  }

  &__batch-info {
    display: flex;
    align-items: center;
    gap: $spacing-base;
  }

  &__batch-count {
    font-size: $font-size-sm;
    font-weight: 600;
    color: $primary-color;
  }

  &__batch-actions {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }
}

// 批量操作栏过渡动画
.batch-bar-slide-enter-active,
.batch-bar-slide-leave-active {
  transition: all 0.25s ease;
}
.batch-bar-slide-enter-from,
.batch-bar-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
