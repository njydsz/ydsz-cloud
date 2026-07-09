/**
 * @file 表格选择 composable
 * @description 封装表格行选择的通用逻辑，配合 BatchToolbar 组件使用：
 *   - 管理 selectedRows 状态
 *   - 提供 selectAll/clearSelection/toggleSelection 方法
 *   - 提供 selectedIds 计算属性
 *   - 提供 isSelected/isAllSelected 判断方法
 * @module composables/useSelection
 *
 * 用法：
 *   const { selectedRows, selectedIds, clearSelection, handleSelectionChange } = useSelection<T>()
 *
 *   <ProTable @selection-change="handleSelectionChange" />
 *   <BatchToolbar
 *     :selected-count="selectedRows.length"
 *     :actions="batchActions"
 *     @clear="clearSelection"
 *   />
 */
import { ref, computed } from 'vue'

export function useSelection<T extends Record<string, unknown>>(idField = 'id') {
  /** 选中的行数据 */
  const selectedRows = ref<T[]>([]) as ReturnType<typeof ref<T[]>>

  /** 选中的 ID 数组 */
  const selectedIds = computed(() =>
    selectedRows.value.map((row) => row[idField] as string | number),
  )

  /** 选中数量 */
  const selectedCount = computed(() => selectedRows.value.length)

  /** 是否有选中 */
  const hasSelection = computed(() => selectedRows.value.length > 0)

  /** 处理表格 selection-change 事件 */
  function handleSelectionChange(rows: T[]): void {
    selectedRows.value = rows
  }

  /** 清空选择 */
  function clearSelection(): void {
    selectedRows.value = []
  }

  /** 判断某行是否已选中 */
  function isSelected(id: string | number): boolean {
    return selectedIds.value.includes(id)
  }

  /** 手动切换选中状态 */
  function toggleSelection(row: T, selected?: boolean): void {
    const id = row[idField] as string | number
    const idx = selectedRows.value.findIndex((r) => r[idField] === id)
    if (selected === undefined) {
      // toggle
      if (idx > -1) {
        selectedRows.value.splice(idx, 1)
      } else {
        selectedRows.value.push(row)
      }
    } else if (selected && idx === -1) {
      selectedRows.value.push(row)
    } else if (!selected && idx > -1) {
      selectedRows.value.splice(idx, 1)
    }
  }

  /** 选中所有行 */
  function selectAll(rows: T[]): void {
    selectedRows.value = [...rows]
  }

  return {
    selectedRows,
    selectedIds,
    selectedCount,
    hasSelection,
    handleSelectionChange,
    clearSelection,
    isSelected,
    toggleSelection,
    selectAll,
  }
}
