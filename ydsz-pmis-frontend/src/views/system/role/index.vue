<!--
  @file 角色管理
  @description 角色管理页面：提供角色分页查询、新增/编辑/删除，以及权限树分配（支持全部/本部门/本人/自定义四种数据权限范围）。对应路由 /system/role。
  @module views/system/role
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const formRules = {
  roleCode: [{ required: true, message: '角色编码必填', trigger: 'blur' }],
  roleName: [{ required: true, message: '角色名称必填', trigger: 'blur' }],
  dataScope: [{ required: true, message: '数据权限必填', trigger: 'change' }],
}

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
  const { data } = await getRole(row.id)
  Object.assign(form, data)
  form.permissionIds = []
  dialogVisible.value = true
}

/** 提交表单：根据 dialogMode 执行创建或更新，成功后刷新列表 */
async function submitForm() {
  await formRef.value?.validate()
  if (dialogMode.value === 'create') {
    await createRole(form)
    ElMessage.success('创建成功')
  } else {
    await updateRole(form)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  fetchList()
}

/**
 * 删除角色，二次确认后执行
 * @param row 待删除的角色行数据
 */
async function handleDelete(row: RoleVO) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.roleName}」吗？`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    /* 用户取消 */
  }
}

/**
 * 打开权限分配弹窗：拉取全量权限树与该角色已分配权限
 * @param row 待分配权限的角色行数据
 */
async function openPermDialog(row: RoleVO) {
  permDialogRoleId.value = row.id
  // 1. 拉取全量权限
  const { data } = await getAllPermissionsApi()
  permTree.value = data || []
  // 2. 拉取已分配
  const { data: checkedIds } = await listRolePermissions(row.id)
  permCheckedIds.value = checkedIds
  permDialogVisible.value = true
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
  await assignPermissions(permDialogRoleId.value, ids)
  ElMessage.success('权限分配成功')
  permDialogVisible.value = false
}

onMounted(fetchList)
</script>

<template>
  <div class="role-page">
    <el-card shadow="never">
      <el-form inline :model="query" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="角色编码/名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">查询</el-button>
          <el-button @click="query.keyword = ''; fetchList()">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['auth:role:create']" type="primary" :icon="'Plus'" @click="openCreate">
          新增角色
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchList">刷新</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="roleCode" title="角色编码" width="160" />
        <vxe-column field="roleName" title="角色名称" />
        <vxe-column field="dataScope" title="数据权限" width="120">
          <template #default="{ row }">
            <el-tag size="small">
              {{ ({ ALL: '全部', DEPT: '本部门', SELF: '本人', CUSTOM: '自定义' } as any)[row.dataScope] || row.dataScope }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="description" title="描述" />
        <vxe-column field="status" title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['auth:role:assign']" type="primary" link @click="openPermDialog(row)">
              分配权限
            </el-button>
            <el-button v-permission="['auth:role:update']" type="primary" link @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="['auth:role:delete']" type="danger" link @click="handleDelete(row)">
              删除
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
      :title="dialogMode === 'create' ? '新增角色' : '编辑角色'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="例如: PM" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="例如: 项目经理" />
        </el-form-item>
        <el-form-item label="数据权限" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option label="全部" value="ALL" />
            <el-option label="本部门" value="DEPT" />
            <el-option label="本人" value="SELF" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="permDialogVisible" title="分配权限" width="500px">
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
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPermAssign">确定</el-button>
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
