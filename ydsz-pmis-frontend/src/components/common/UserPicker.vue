<!--
  @file UserPicker 用户选择器
  @module components/common/UserPicker
  @description P1-8: 通用用户选择器（对标钉钉/飞书的"选择审批人"控件）

  使用场景：
  1. 工作流 - 转办 / 委派 / 加签 / 抄送 的目标用户选择
  2. 业务表单 - 项目经理 / 销售负责人 / 抄送人 等字段选择
  3. 任何需要"远程搜索 + 单选/多选 + 展示用户信息"的场景

  核心能力：
  - 远程搜索：输入关键字后通过 /users 模糊查询
  - 单选 / 多选：multiple 控制
  - 信息卡片：下拉项展示姓名、用户名、部门、职级
  - 高级模式：showDialog 显示一个更丰富的弹窗，支持部门/职级筛选 + 最近选择
  - 双向绑定：支持 number/string（仅 ID）和 object（完整用户）两种 v-model 形态
  - 外部回填：通过 options prop 传入已选用户列表（无需再走搜索）

  与 el-select 的差异：
  - 远程搜索时自动显示加载中
  - 多选时支持一键清空 + 折叠 tag
  - 高级模式弹窗可按部门、职级筛选
-->
<script setup lang="ts">
/**
 * 通用用户选择器（远程搜索 + 单/多选 + 高级弹窗）
 */
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Search, User, OfficeBuilding } from '@element-plus/icons-vue'
import { listUsers } from '@/api/system/user'
import type { UserVO } from '@/api/system/user/types'

const { t } = useI18n()

export type UserModel = number | string | UserVO | null | undefined

const props = withDefaults(
  defineProps<{
    /** v-model 值：单选时为 userId 或 user；多选时为数组 */
    modelValue?: UserModel | UserModel[]
    /** 多选模式 */
    multiple?: boolean
    /** 占位文案 */
    placeholder?: string
    /** 是否禁用 */
    disabled?: boolean
    /** 是否可清空 */
    clearable?: boolean
    /** 远程搜索防抖毫秒 */
    debounce?: number
    /** 单次拉取条数 */
    pageSize?: number
    /** 状态过滤：ENABLED/DISABLED；默认只查启用 */
    status?: string
    /** 部门 ID 过滤 */
    departmentId?: number
    /** 职级编码过滤 */
    levelCode?: string
    /** 是否显示"高级选择"按钮（弹窗模式） */
    showDialog?: boolean
    /** 高级弹窗标题 */
    dialogTitle?: string
    /** 外部预置选项（已选用户等），用于无网/兜底展示 */
    options?: UserVO[]
    /** 显示的字段映射 */
    valueKey?: keyof UserVO
    /** 自定义渲染函数（v-model=object 时可直接使用 props.options 传值） */
  }>(),
  {
    modelValue: undefined,
    multiple: false,
    placeholder: '',
    disabled: false,
    clearable: true,
    debounce: 300,
    pageSize: 20,
    status: 'ENABLED',
    departmentId: undefined,
    levelCode: undefined,
    showDialog: true,
    dialogTitle: '',
    options: () => [],
    valueKey: 'id',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: UserModel | UserModel[] | undefined): void
  (e: 'change', v: UserModel | UserModel[] | undefined, user: UserVO | UserVO[] | null): void
}>()

// ===========================================
// 基础数据
// ===========================================
/** 内部维护的全部候选用户（搜索结果 + 外部预置 + 已选用户去重） */
const candidates = ref<UserVO[]>([])
/** 已通过 ID 匹配上的 UserVO，用于回显已选用户 */
const resolved = ref<Record<string, UserVO>>({})
/** 搜索关键字 */
const keyword = ref('')
/** 加载中 */
const loading = ref(false)
/** 高级弹窗显隐 */
const dialogVisible = ref(false)

/** 防抖句柄 */
let debounceTimer: ReturnType<typeof setTimeout> | null = null

// ===========================================
// 工具：用户主键
// ===========================================
function userKey(u: UserVO): string {
  return String(u[props.valueKey] ?? u.id)
}

function normalizeToArray(v: UserModel | UserModel[] | undefined): UserVO[] {
  if (v === undefined || v === null) return []
  const arr = Array.isArray(v) ? v : [v]
  const result: UserVO[] = []
  for (const item of arr) {
    if (item === null || item === undefined) continue
    if (typeof item === 'object') {
      result.push(item as UserVO)
    } else if (resolved.value[String(item)]) {
      result.push(resolved.value[String(item)])
    } else {
      // 占位：仅 ID，构造一个临时对象供显示，后续搜索到会替换
      result.push({ id: Number(item), username: String(item), realName: '...' } as UserVO)
    }
  }
  return result
}

const selectedUsers = computed<UserVO[]>(() => {
  return normalizeToArray(props.modelValue)
})

const placeholderText = computed(() => props.placeholder || t('common.userPicker.placeholder'))
const dialogTitleText = computed(() => props.dialogTitle || t('common.userPicker.dialogTitle'))

// ===========================================
// 搜索逻辑
// ===========================================
function debouncedSearch(kw: string) {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => doSearch(kw), props.debounce)
}

async function doSearch(kw: string) {
  loading.value = true
  try {
    const res = await listUsers({
      page: 1,
      size: props.pageSize,
      status: props.status,
      departmentId: props.departmentId,
      levelCode: props.levelCode,
      keyword: kw || undefined,
    })
    // res.data 为 PageResult<UserVO>，后端分页返回 records 字段（MyBatis-Plus），用类型断言收敛
    const pageData = res.data as unknown as { data?: { records?: UserVO[] } } | undefined
    const records = (pageData?.data?.records || []) as UserVO[]
    mergeCandidates(records)
  } catch (e) {
    // 全局拦截器已弹错，这里只兜底
    ElMessage.error(t('common.userPicker.searchFailed', { message: (e as Error).message }))
  } finally {
    loading.value = false
  }
}

function mergeCandidates(records: UserVO[]) {
  const map = new Map<string, UserVO>()
  for (const u of candidates.value) map.set(userKey(u), u)
  for (const u of records) map.set(userKey(u), u)
  candidates.value = Array.from(map.values())
  for (const u of records) resolved.value[userKey(u)] = u
}

// 处理 el-select 远程搜索
function onRemoteSearch(kw: string) {
  keyword.value = kw
  debouncedSearch(kw)
}

// 初次聚焦：拉一批数据兜底
function onFocus() {
  if (candidates.value.length === 0) {
    doSearch('')
  }
}

// ===========================================
// 选中 / 取消选中
// ===========================================
function onChange(v: string | number | (string | number)[] | undefined) {
  if (props.multiple) {
    // v 是 ID 数组（el-select multiple 模式下默认以 valueKey 为绑定值）
    const arr = (v as Array<string | number>) || []
    const users = arr.map((id) => resolved.value[String(id)] || { id: Number(id) } as UserVO)
    emit('update:modelValue', users)
    emit('change', users, users)
  } else {
    const id = v as string | number | undefined
    if (id === undefined || id === null || id === '') {
      emit('update:modelValue', undefined)
      emit('change', undefined, null)
    } else {
      const user = resolved.value[String(id)] || ({ id: Number(id) } as UserVO)
      emit('update:modelValue', user)
      emit('change', user, user)
    }
  }
}

// ===========================================
// 高级弹窗
// ===========================================
const dialogKeyword = ref('')
const dialogDept = ref<string | undefined>(undefined)
const dialogList = ref<UserVO[]>([])
const dialogSelected = ref<UserVO[]>([])
const dialogLoading = ref(false)
const dialogPage = ref(1)
const dialogTotal = ref(0)
const dialogPageSize = ref(20)
/** 最近选择（localStorage） */
const RECENT_KEY = 'pmis:user-picker:recent'
const recent = ref<UserVO[]>([])

function loadRecent() {
  try {
    const raw = localStorage.getItem(RECENT_KEY)
    if (raw) recent.value = JSON.parse(raw) as UserVO[]
  } catch {
    recent.value = []
  }
}

function pushRecent(u: UserVO) {
  const next = [u, ...recent.value.filter((x) => userKey(x) !== userKey(u))].slice(0, 10)
  recent.value = next
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  } catch {
    // 忽略存储失败
  }
}

async function loadDialogList(reset = false) {
  if (reset) {
    dialogPage.value = 1
    dialogList.value = []
  }
  dialogLoading.value = true
  try {
    const res = await listUsers({
      page: dialogPage.value,
      size: dialogPageSize.value,
      status: props.status,
      departmentId: dialogDept.value ? Number(dialogDept.value) : undefined,
      levelCode: props.levelCode,
      keyword: dialogKeyword.value || undefined,
    })
    // res.data 为 PageResult<UserVO>，后端分页返回 records/total 字段（MyBatis-Plus），用类型断言收敛
    const pageData = res.data as unknown as { data?: { records?: UserVO[]; total?: number } } | undefined
    const records = (pageData?.data?.records || []) as UserVO[]
    dialogList.value = reset ? records : [...dialogList.value, ...records]
    dialogTotal.value = pageData?.data?.total || 0
    mergeCandidates(records)
  } catch (e) {
    ElMessage.error(t('common.userPicker.loadFailed', { message: (e as Error).message }))
  } finally {
    dialogLoading.value = false
  }
}

function openDialog() {
  dialogVisible.value = true
  dialogKeyword.value = ''
  dialogDept.value = undefined
  dialogSelected.value = selectedUsers.value.map((u) => ({ ...u }))
  loadRecent()
  loadDialogList(true)
}

function toggleDialogSelect(u: UserVO) {
  const key = userKey(u)
  const idx = dialogSelected.value.findIndex((x) => userKey(x) === key)
  if (idx >= 0) {
    dialogSelected.value.splice(idx, 1)
  } else {
    if (props.multiple) {
      dialogSelected.value.push({ ...u })
    } else {
      dialogSelected.value = [{ ...u }]
    }
  }
}

function isDialogSelected(u: UserVO): boolean {
  return dialogSelected.value.some((x) => userKey(x) === userKey(u))
}

function pickRecent(u: UserVO) {
  if (props.multiple) {
    if (!isDialogSelected(u)) dialogSelected.value.push({ ...u })
  } else {
    dialogSelected.value = [{ ...u }]
  }
}

function confirmDialog() {
  const users = dialogSelected.value
  if (users.length === 0) {
    dialogVisible.value = false
    return
  }
  for (const u of users) {
    pushRecent(u)
    resolved.value[userKey(u)] = u
  }
  if (props.multiple) {
    emit('update:modelValue', users)
    emit('change', users, users)
  } else {
    const u = users[0]
    emit('update:modelValue', u)
    emit('change', u, u)
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
    if (val && val.length > 0) mergeCandidates(val)
  },
  { immediate: true, deep: true },
)

// 注入已选用户
watch(
  selectedUsers,
  (val) => {
    for (const u of val) {
      if (u && u.id !== undefined) {
        resolved.value[userKey(u)] = u
      }
    }
  },
  { immediate: true, deep: true },
)

onMounted(() => {
  loadRecent()
  // 预拉一批避免空列表
  if (candidates.value.length === 0) {
    nextTick(() => doSearch(''))
  }
})
</script>

<template>
  <div class="user-picker">
    <el-select
      :model-value="
        multiple
          ? (Array.isArray(modelValue) ? modelValue.filter((x) => x !== null && x !== undefined) : [])
          : (typeof modelValue === 'object' && modelValue !== null
              ? (modelValue as UserVO).id
              : (modelValue as number | string | undefined))
      "
      :multiple="multiple"
      :placeholder="placeholderText"
      :disabled="disabled"
      :clearable="clearable"
      :filterable="true"
      :remote="true"
      :remote-method="onRemoteSearch"
      :loading="loading"
      :collapse-tags="multiple"
      :collapse-tags-tooltip="multiple"
      value-key="id"
      style="width: 100%"
      @update:model-value="onChange"
      @focus="onFocus"
    >
      <el-option
        v-for="u in candidates"
        :key="userKey(u)"
        :value="u.id"
        :label="`${u.realName || u.username}${u.departmentName ? ' / ' + u.departmentName : ''}`"
      >
        <div class="user-option">
          <el-avatar :size="24" class="user-avatar">
            {{ (u.realName || u.username || '?').slice(0, 1) }}
          </el-avatar>
          <div class="user-info">
            <div class="user-name">
              {{ u.realName || u.username }}
              <span v-if="u.username && u.realName && u.username !== u.realName" class="user-username">
                @{{ u.username }}
              </span>
            </div>
            <div class="user-meta">
              <span v-if="u.departmentName" class="user-meta-item">
                <el-icon><OfficeBuilding /></el-icon>{{ u.departmentName }}
              </span>
              <span v-if="u.levelName" class="user-meta-item user-level">
                {{ u.levelName }}
              </span>
            </div>
          </div>
        </div>
      </el-option>

      <template #footer v-if="showDialog">
        <div class="picker-footer">
          <el-button text type="primary" @click="openDialog">
            <el-icon><Search /></el-icon>
            {{ t('common.advancedSelect') }}
          </el-button>
        </div>
      </template>
    </el-select>

    <!-- 高级选择弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitleText"
      width="720px"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="user-dialog">
        <div class="user-dialog__filters">
          <el-input
            v-model="dialogKeyword"
            :placeholder="t('common.userPicker.searchPlaceholder')"
            clearable
            :prefix-icon="Search"
            @input="loadDialogList(true)"
          />
          <el-select
            v-model="dialogDept"
            :placeholder="t('common.userPicker.deptPlaceholder')"
            clearable
            style="width: 200px"
            @change="loadDialogList(true)"
          >
            <!-- 部门选项可由业务方通过 slot 覆盖 -->
            <slot name="department-options" />
          </el-select>
        </div>

        <div v-if="recent.length > 0 && dialogSelected.length === 0" class="user-dialog__recent">
          <div class="user-dialog__section-title">{{ t('common.userPicker.recent') }}</div>
          <div class="user-dialog__recent-list">
            <el-tag
              v-for="u in recent"
              :key="'r-' + userKey(u)"
              class="recent-tag"
              effect="plain"
              round
              @click="pickRecent(u)"
            >
              <el-icon><User /></el-icon>
              {{ u.realName || u.username }}
            </el-tag>
          </div>
        </div>

        <el-table
          :data="dialogList"
          v-loading="dialogLoading"
          size="small"
          height="380"
          @row-click="toggleDialogSelect"
        >
          <el-table-column width="50">
            <template #default="{ row }">
              <el-checkbox
                :model-value="isDialogSelected(row as UserVO)"
                @change="toggleDialogSelect(row as UserVO)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('common.userPicker.colName')" min-width="120">
            <template #default="{ row }">
              <div class="dialog-user-name">
                <el-avatar :size="22">{{ (row.realName || row.username || '?').slice(0, 1) }}</el-avatar>
                <span>{{ row.realName || row.username }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="username" :label="t('common.userPicker.colUsername')" min-width="100" />
          <el-table-column prop="departmentName" :label="t('common.userPicker.colDept')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="levelName" :label="t('common.userPicker.colLevel')" width="100" />
          <el-table-column prop="email" :label="t('common.userPicker.colEmail')" min-width="160" show-overflow-tooltip />
        </el-table>

        <div class="user-dialog__pagination">
          <el-pagination
            v-model:current-page="dialogPage"
            v-model:page-size="dialogPageSize"
            :total="dialogTotal"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadDialogList()"
            @size-change="loadDialogList(true)"
          />
        </div>

        <div v-if="dialogSelected.length > 0" class="user-dialog__selected">
          <span class="user-dialog__section-title">
            {{ t('common.userPicker.selected', { n: dialogSelected.length }) }}
            <el-button text type="danger" size="small" @click="clearDialogSelection">{{ t('common.userPicker.clear') }}</el-button>
          </span>
          <el-tag
            v-for="u in dialogSelected"
            :key="'s-' + userKey(u)"
            closable
            class="selected-tag"
            @close="toggleDialogSelect(u)"
          >
            {{ u.realName || u.username }}
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
.user-picker {
  width: 100%;
}

.user-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}

.user-avatar {
  background: #1890ff;
  color: #fff;
  font-size: 12px;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  color: #1e293b;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-username {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
}

.user-meta {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.user-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.user-level {
  background: #f0f9ff;
  color: #0369a1;
  padding: 0 6px;
  border-radius: 8px;
  font-size: 11px;
}

.picker-footer {
  display: flex;
  justify-content: center;
  padding: 4px 0;
  border-top: 1px solid #f1f5f9;
  margin-top: 2px;
}

.user-dialog {
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

  &__pagination {
    display: flex;
    justify-content: flex-end;
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

.dialog-user-name {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
