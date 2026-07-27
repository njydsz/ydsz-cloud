<!--
  @fileoverview DeptSelector 通用部门选择器
  @description 对标钉钉/飞书的"选择部门"控件：
  - Props: modelValue / multiple / placeholder / disabled / clearable / checkable
            / showDialog / dialogTitle / options / valueKey
  - Emits: update:modelValue / change
  - 支持树形选择、单选/多选、远程/外部回填、高级弹窗（按部门名称搜索）
  - 双向绑定支持 number/string(仅 ID) 与 object(完整部门) 两种形态
  - 场景: 工作流审批部门、业务表单部门字段、数据权限范围选择
  @module components/common/DeptSelector
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 通用部门选择器（树形选择 + 单/多选 + 高级弹窗）
 */
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Search, OfficeBuilding } from '@element-plus/icons-vue'
import { listDeptTree } from '@/api/system/dept'
import type { DeptVO } from '@/api/system/dept/types'

const { t } = useI18n()

export type DeptModel = number | string | DeptVO | null | undefined

const props = withDefaults(
  defineProps<{
    /** v-model 值：单选时为 deptId 或 dept；多选时为数组 */
    modelValue?: DeptModel | DeptModel[]
    /** 多选模式 */
    multiple?: boolean
    /** 占位文案 */
    placeholder?: string
    /** 是否禁用 */
    disabled?: boolean
    /** 是否可清空 */
    clearable?: boolean
    /** 是否可选任意节点（false 时只能选叶子） */
    checkable?: boolean
    /** 是否显示"高级选择"按钮（弹窗模式） */
    showDialog?: boolean
    /** 高级弹窗标题 */
    dialogTitle?: string
    /** 外部预置选项（已选部门等），用于无网/兜底展示 */
    options?: DeptVO[]
    /** 显示的字段映射 */
    valueKey?: keyof DeptVO
  }>(),
  {
    modelValue: undefined,
    multiple: false,
    placeholder: '',
    disabled: false,
    clearable: true,
    checkable: true,
    showDialog: true,
    dialogTitle: '',
    options: () => [],
    valueKey: 'id',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: DeptModel | DeptModel[] | undefined): void
  (e: 'change', v: DeptModel | DeptModel[] | undefined, dept: DeptVO | DeptVO[] | null): void
}>()

// ===========================================
// 基础数据
// ===========================================
/** 部门树数据 */
const deptTree = ref<DeptVO[]>([])
/** 扁平化部门列表（用于搜索） */
const deptFlat = ref<DeptVO[]>([])
/** 已通过 ID 匹配上的 DeptVO，用于回显已选部门 */
const resolved = ref<Record<string, DeptVO>>({})
/** 搜索关键字 */
const keyword = ref('')
/** 加载中 */
const loading = ref(false)
/** 高级弹窗显隐 */
const dialogVisible = ref(false)
/** 过滤后的树数据 */
const filteredTree = computed(() => filterTree(deptTree.value, keyword.value))

// ===========================================
// 工具：部门主键
// ===========================================
function deptKey(d: DeptVO): string {
  return String(d[props.valueKey] ?? d.id)
}

function normalizeToArray(v: DeptModel | DeptModel[] | undefined): DeptVO[] {
  if (v === undefined || v === null) return []
  const arr = Array.isArray(v) ? v : [v]
  const result: DeptVO[] = []
  for (const item of arr) {
    if (item === null || item === undefined) continue
    if (typeof item === 'object') {
      result.push(item as DeptVO)
    } else if (resolved.value[String(item)]) {
      result.push(resolved.value[String(item)])
    } else {
      // 占位：仅 ID，构造一个临时对象供显示
      result.push({ id: String(item), deptName: '...' } as DeptVO)
    }
  }
  return result
}

const selectedDepts = computed<DeptVO[]>(() => {
  return normalizeToArray(props.modelValue)
})

const placeholderText = computed(() => props.placeholder || t('common.deptSelector.placeholder'))
const dialogTitleText = computed(() => props.dialogTitle || t('common.deptSelector.dialogTitle'))

// ===========================================
// 树形过滤
// ===========================================
function filterTree(tree: DeptVO[], kw: string): DeptVO[] {
  if (!kw) return tree
  const lowerKw = kw.toLowerCase()
  const result: DeptVO[] = []
  for (const node of tree) {
    const matchSelf = node.deptName?.toLowerCase().includes(lowerKw)
    const filteredChildren = node.children ? filterTree(node.children, kw) : []
    if (matchSelf || filteredChildren.length > 0) {
      result.push({
        ...node,
        children: filteredChildren.length > 0 ? filteredChildren : node.children,
      })
    }
  }
  return result
}

// ===========================================
// 加载逻辑
// ===========================================
async function loadDeptTree() {
  loading.value = true
  try {
    const res = await listDeptTree()
    deptTree.value = res.data || []
    // 扁平化用于搜索
    deptFlat.value = flattenTree(deptTree.value)
    // 更新 resolved
    for (const d of deptFlat.value) {
      resolved.value[deptKey(d)] = d
    }
  } catch (e) {
    ElMessage.error(t('common.deptSelector.loadFailed', { message: (e as Error).message }))
  } finally {
    loading.value = false
  }
}

function flattenTree(tree: DeptVO[]): DeptVO[] {
  const result: DeptVO[] = []
  for (const node of tree) {
    result.push(node)
    if (node.children && node.children.length > 0) {
      result.push(...flattenTree(node.children))
    }
  }
  return result
}

// ===========================================
// 选中 / 取消选中
// ===========================================
function onChange(v: string | number | (string | number)[] | undefined) {
  if (props.multiple) {
    const arr = (v as Array<string | number>) || []
    const depts = arr.map((id) => resolved.value[String(id)] || { id: String(id) } as DeptVO)
    emit('update:modelValue', depts)
    emit('change', depts, depts)
  } else {
    const id = v as string | number | undefined
    if (id === undefined || id === null || id === '') {
      emit('update:modelValue', undefined)
      emit('change', undefined, null)
    } else {
      const dept = resolved.value[String(id)] || ({ id: String(id) } as DeptVO)
      emit('update:modelValue', dept)
      emit('change', dept, dept)
    }
  }
}

// ===========================================
// 高级弹窗
// ===========================================
const dialogKeyword = ref('')
const dialogSelected = ref<DeptVO[]>([])
/** 最近选择（localStorage） */
const RECENT_KEY = 'ydsz:dept-selector:recent'
const recent = ref<DeptVO[]>([])

function loadRecent() {
  try {
    const raw = localStorage.getItem(RECENT_KEY)
    if (raw) recent.value = JSON.parse(raw) as DeptVO[]
  } catch {
    recent.value = []
  }
}

function pushRecent(d: DeptVO) {
  const next = [d, ...recent.value.filter((x) => deptKey(x) !== deptKey(d))].slice(0, 10)
  recent.value = next
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  } catch {
    // 忽略存储失败
  }
}

const filteredDialogTree = computed(() => filterTree(deptTree.value, dialogKeyword.value))

function openDialog() {
  dialogVisible.value = true
  dialogKeyword.value = ''
  dialogSelected.value = selectedDepts.value.map((d) => ({ ...d }))
  loadRecent()
  if (deptTree.value.length === 0) {
    loadDeptTree()
  }
}

function toggleDialogSelect(d: DeptVO) {
  const key = deptKey(d)
  const idx = dialogSelected.value.findIndex((x) => deptKey(x) === key)
  if (idx >= 0) {
    dialogSelected.value.splice(idx, 1)
  } else {
    if (props.multiple) {
      dialogSelected.value.push({ ...d })
    } else {
      dialogSelected.value = [{ ...d }]
    }
  }
}

function isDialogSelected(d: DeptVO): boolean {
  return dialogSelected.value.some((x) => deptKey(x) === deptKey(d))
}

function pickRecent(d: DeptVO) {
  if (props.multiple) {
    if (!isDialogSelected(d)) dialogSelected.value.push({ ...d })
  } else {
    dialogSelected.value = [{ ...d }]
  }
}

function confirmDialog() {
  const depts = dialogSelected.value
  if (depts.length === 0) {
    dialogVisible.value = false
    return
  }
  for (const d of depts) {
    pushRecent(d)
    resolved.value[deptKey(d)] = d
  }
  if (props.multiple) {
    emit('update:modelValue', depts)
    emit('change', depts, depts)
  } else {
    const d = depts[0]
    emit('update:modelValue', d)
    emit('change', d, d)
  }
  dialogVisible.value = false
}

function clearDialogSelection() {
  dialogSelected.value = []
}

// ===========================================
// 初始：注入外部 options
// ===========================================
watch(
  () => props.options,
  (val) => {
    if (val && val.length > 0) {
      for (const d of val) {
        resolved.value[deptKey(d)] = d
      }
    }
  },
  { immediate: true, deep: true },
)

// 注入已选部门
watch(
  selectedDepts,
  (val) => {
    for (const d of val) {
      if (d && d.id !== undefined) {
        resolved.value[deptKey(d)] = d
      }
    }
  },
  { immediate: true, deep: true },
)

onMounted(() => {
  loadRecent()
  // 预拉部门树
  if (deptTree.value.length === 0) {
    nextTick(() => loadDeptTree())
  }
})
</script>

<template>
  <div class="dept-selector">
    <el-tree-select
      :model-value="
        multiple
          ? (Array.isArray(modelValue) ? modelValue.filter((x) => x !== null && x !== undefined) : [])
          : (typeof modelValue === 'object' && modelValue !== null
              ? (modelValue as DeptVO).id
              : (modelValue as number | string | undefined))
      "
      :data="filteredTree"
      :multiple="multiple"
      :placeholder="placeholderText"
      :disabled="disabled"
      :clearable="clearable"
      :filterable="true"
      :filter-node-method="(value: string, data: DeptVO) => !value || data.deptName?.toLowerCase().includes(value.toLowerCase())"
      :loading="loading"
      :collapse-tags="multiple"
      :collapse-tags-tooltip="multiple"
      :check-strictly="checkable"
      node-key="id"
      :props="{ label: 'deptName', children: 'children' }"
      style="width: 100%"
      @update:model-value="onChange"
    >
      <template #default="{ data }">
        <div class="dept-option">
          <el-icon class="dept-icon"><OfficeBuilding /></el-icon>
          <span class="dept-name">{{ data.deptName }}</span>
          <span v-if="data.leaderName" class="dept-leader">
            {{ t('common.deptSelector.leader') }}: {{ data.leaderName }}
          </span>
        </div>
      </template>
    </el-tree-select>

    <div v-if="showDialog" class="selector-footer">
      <el-button text type="primary" size="small" @click="openDialog">
        <el-icon><Search /></el-icon>
        {{ t('common.advancedSelect') }}
      </el-button>
    </div>

    <!-- 高级选择弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitleText"
      width="640px"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="dept-dialog">
        <div class="dept-dialog__filters">
          <el-input
            v-model="dialogKeyword"
            :placeholder="t('common.deptSelector.searchPlaceholder')"
            clearable
            :prefix-icon="Search"
          />
        </div>

        <div v-if="recent.length > 0 && dialogSelected.length === 0" class="dept-dialog__recent">
          <div class="dept-dialog__section-title">{{ t('common.deptSelector.recent') }}</div>
          <div class="dept-dialog__recent-list">
            <el-tag
              v-for="d in recent"
              :key="'r-' + deptKey(d)"
              class="recent-tag"
              effect="plain"
              round
              @click="pickRecent(d)"
            >
              <el-icon><OfficeBuilding /></el-icon>
              {{ d.deptName }}
            </el-tag>
          </div>
        </div>

        <el-tree
          :data="filteredDialogTree"
          :props="{ label: 'deptName', children: 'children' }"
          node-key="id"
          :default-expanded-keys="dialogSelected.map(d => String(d.id))"
          :show-checkbox="multiple"
          :check-strictly="true"
          highlight-current
          @node-click="(data: DeptVO) => !multiple && toggleDialogSelect(data)"
          @check="(data: DeptVO) => multiple && toggleDialogSelect(data)"
        >
          <template #default="{ data }">
            <div class="dialog-dept-node" :class="{ 'is-selected': isDialogSelected(data) }">
              <el-icon><OfficeBuilding /></el-icon>
              <span class="dept-name">{{ data.deptName }}</span>
              <span v-if="data.leaderName" class="dept-leader">
                ({{ data.leaderName }})
              </span>
            </div>
          </template>
        </el-tree>

        <div v-if="dialogSelected.length > 0" class="dept-dialog__selected">
          <span class="dept-dialog__section-title">
            {{ t('common.deptSelector.selected', { n: dialogSelected.length }) }}
            <el-button text type="danger" size="small" @click="clearDialogSelection">{{ t('common.deptSelector.clear') }}</el-button>
          </span>
          <el-tag
            v-for="d in dialogSelected"
            :key="'s-' + deptKey(d)"
            closable
            class="selected-tag"
            @close="toggleDialogSelect(d)"
          >
            {{ d.deptName }}
          </el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmDialog">
          {{ t('common.ok') }}{{ dialogSelected.length > 0 ? `（${dialogSelected.length}）` : '' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.dept-selector {
  width: 100%;
}

.selector-footer {
  display: flex;
  justify-content: center;
  padding: 4px 0;
  margin-top: 4px;
}

.dept-option {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.dept-icon {
  color: #64748b;
  flex-shrink: 0;
}

.dept-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dept-leader {
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}

.dept-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__filters {
    display: flex;
    gap: 8px;
  }

  &__section-title {
    font-size: 13px;
    font-weight: 600;
    color: #475569;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__recent {
    background: #f8fafc;
    border-radius: 6px;
    padding: 10px 12px;
  }

  &__recent-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__selected {
    background: #ecfeff;
    border-radius: 6px;
    padding: 10px 12px;
  }
}

.recent-tag {
  cursor: pointer;
  transition: all 0.2s;
  &:hover {
    background: #e0f2fe;
  }
}

.selected-tag {
  margin: 2px 4px 2px 0;
}

.dialog-dept-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f1f5f9;
  }

  &.is-selected {
    background: #e0f2fe;
    color: #0369a1;
  }

  .dept-name {
    flex: 1;
    font-size: 13px;
  }

  .dept-leader {
    font-size: 12px;
    color: #64748b;
  }
}

:deep(.el-tree-node__content) {
  height: auto;
  padding: 4px 0;
}
</style>
