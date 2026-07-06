<!--
  @file 角色管理
  @description 角色管理页面：提供角色分页查询、新增/编辑/删除，以及权限树分配（支持全部/本部门/本人/自定义四种数据权限范围）。对应路由 /system/role。
  @module views/system/role
-->
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  listRoles,
  getRole,
  createRole,
  updateRole,
  deleteRole,
  assignPermissions,
  listRolePermissions,
} from '@/api/system/role'
import { getAllPermissionsApi } from '@/api/menu'
import type { RoleVO, RoleFormDTO } from '@/api/system/role/types'
import type { MenuTreeNode } from '@/api/menu/types'
import { handleError, confirmAction, showSuccess } from '@/utils/error'

const { t } = useI18n()

const loading = ref(false)
const list = ref<RoleVO[]>([])
const total = ref(0)
// 角色分页查询条件
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive<RoleFormDTO>({
  id: undefined,
  roleCode: '',
  roleName: '',
  description: '',
  dataScope: 'ALL',
  sortOrder: 0,
  status: 'ENABLED',
  permissionIds: [],
})

const formRules = computed(() => ({
  roleCode: [{ required: true, message: t('system.role.rules.roleCodeRequired'), trigger: 'blur' }],
  roleName: [{ required: true, message: t('system.role.rules.roleNameRequired'), trigger: 'blur' }],
  dataScope: [{ required: true, message: t('system.role.rules.dataScopeRequired'), trigger: 'change' }],
}))

const permDialogVisible = ref(false)
const permTree = ref<MenuTreeNode[]>([])
const permTreeRef = ref()
const permDialogRoleId = ref<number | null>(null)
const permCheckedIds = ref<number[]>([])

/** 拉取角色分页列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await listRoles(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 打开新增角色弹窗，初始化表单默认值 */
function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    roleCode: '',
    roleName: '',
    description: '',
    dataScope: 'ALL',
    sortOrder: 0,
    status: 'ENABLED',
    permissionIds: [],
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗：拉取角色详情并回填到表单
 * @param row 待编辑的角色行数据
 */
async function openEdit(row: RoleVO) {
  dialogMode.value = 'edit'
  try {
    const { data } = await getRole(row.id)
    Object.assign(form, data)
    form.permissionIds = []
    dialogVisible.value = true
  } catch (e) {
    handleError(e, 'openEdit')
  }
}

/** 提交表单：根据 dialogMode 执行创建或更新，成功后刷新列表 */
async function submitForm() {
  await formRef.value?.validate()
  try {
    if (dialogMode.value === 'create') {
      await createRole(form)
      showSuccess(t('system.role.messages.createSuccess'))
    } else {
      await updateRole(form)
      showSuccess(t('system.role.messages.updateSuccess'))
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    handleError(e, 'submitForm')
  }
}

/**
 * 删除角色，二次确认后执行
 * @param row 待删除的角色行数据
 */
async function handleDelete(row: RoleVO) {
  const confirmed = await confirmAction(
    t('system.role.messages.confirmDelete', { name: row.roleName }),
    t('common.confirm'),
  )
  if (!confirmed) return
  try {
    await deleteRole(row.id)
    showSuccess(t('system.role.messages.deleteSuccess'))
    fetchList()
  } catch (e) {
    handleError(e, 'handleDelete')
  }
}

/**
 * 打开权限分配弹窗：拉取全量权限树与该角色已分配权限
 * @param row 待分配权限的角色行数据
 */
async function openPermDialog(row: RoleVO) {
  permDialogRoleId.value = row.id
  try {
    // 1. 拉取全量权限
    const { data } = await getAllPermissionsApi()
    permTree.value = data || []
    // 2. 拉取已分配
    const { data: checkedIds } = await listRolePermissions(row.id)
    permCheckedIds.value = checkedIds
    permDialogVisible.value = true
  } catch (e) {
    handleError(e, 'openPermDialog')
  }
}

/**
 * 递归收集节点及其子节点的全部 ID
 * @param nodes 权限树节点列表
 * @param acc 累加器数组
 * @returns 包含全部节点 ID 的数组
 */
function collectCheckedIds(nodes: MenuTreeNode[], acc: number[] = []): number[] {
  for (const n of nodes) {
    acc.push(n.id)
    if (n.children) collectCheckedIds(n.children, acc)
  }
  return acc
}

/** 提交权限分配：收集勾选与半勾节点 ID 并提交后端 */
async function submitPermAssign() {
  if (permDialogRoleId.value === null || permDialogRoleId.value === undefined) return
  // 收集所有勾选节点(包括半勾节点的子节点)
  const checked = permTreeRef.value?.getCheckedNodes?.() || []
  const halfChecked = permTreeRef.value?.getHalfCheckedNodes?.() || []
  const all = [...checked, ...halfChecked]
  const ids = collectCheckedIds(all as MenuTreeNode[])
  try {
    await assignPermissions(permDialogRoleId.value, ids)
    showSuccess(t('system.role.messages.permAssignSuccess'))
    permDialogVisible.value = false
  } catch (e) {
    handleError(e, 'submitPermAssign')
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="role-page">
    <el-card shadow="never">
      <el-form inline :model="query" class="search-form">
        <el-form-item :label="$t('system.role.search.keyword')">
          <el-input v-model="query.keyword" :placeholder="$t('system.role.search.keywordPlaceholder')" clearable @keyup.enter="query.page = 1; fetchList()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">{{ $t('common.search') }}</el-button>
          <el-button @click="query.keyword = ''; fetchList()">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['auth:role:create']" type="primary" :icon="'Plus'" @click="openCreate">
          {{ $t('system.role.buttons.create') }}
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchList">{{ $t('common.refresh') }}</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="roleCode" :title="$t('system.role.columns.roleCode')" width="160" />
        <vxe-column field="roleName" :title="$t('system.role.columns.roleName')" />
        <vxe-column field="dataScope" :title="$t('system.role.columns.dataScope')" width="120">
          <template #default="{ row }">
            <el-tag size="small">
              {{ $t(`system.role.dataScope.${row.dataScope}`) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="description" :title="$t('system.role.columns.description')" />
        <vxe-column field="status" :title="$t('system.role.columns.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ $t(`system.role.status.${row.status}`) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column :title="$t('system.role.columns.action')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['auth:role:assign']" type="primary" link @click="openPermDialog(row)">
              {{ $t('system.role.buttons.assignPermissions') }}
            </el-button>
            <el-button v-permission="['auth:role:update']" type="primary" link @click="openEdit(row)">
              {{ $t('system.role.buttons.edit') }}
            </el-button>
            <el-button v-permission="['auth:role:delete']" type="danger" link @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </el-card>

    <!-- 创建/编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? $t('system.role.dialog.createTitle') : $t('system.role.dialog.editTitle')"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('system.role.form.roleCode')" prop="roleCode">
          <el-input v-model="form.roleCode" :placeholder="$t('system.role.form.roleCodePlaceholder')" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item :label="$t('system.role.form.roleName')" prop="roleName">
          <el-input v-model="form.roleName" :placeholder="$t('system.role.form.roleNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('system.role.form.dataScope')" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option :label="$t('system.role.dataScope.ALL')" value="ALL" />
            <el-option :label="$t('system.role.dataScope.DEPT')" value="DEPT" />
            <el-option :label="$t('system.role.dataScope.SELF')" value="SELF" />
            <el-option :label="$t('system.role.dataScope.CUSTOM')" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.role.form.sortOrder')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item :label="$t('system.role.form.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">{{ $t('system.role.status.ENABLED') }}</el-radio>
            <el-radio value="DISABLED">{{ $t('system.role.status.DISABLED') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('system.role.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="permDialogVisible" :title="$t('system.role.dialog.permTitle')" width="500px">
      <el-tree
        ref="permTreeRef"
        :data="permTree"
        :props="{ label: 'permName', children: 'children' }"
        show-checkbox
        node-key="id"
        :default-checked-keys="permCheckedIds"
        :default-expand-all="true"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitPermAssign">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.role-page {
  .search-form { margin-bottom: $spacing-md; }
  .toolbar { margin-bottom: $spacing-md; }
  .pagination { margin-top: $spacing-md; justify-content: flex-end; }
}
</style>
