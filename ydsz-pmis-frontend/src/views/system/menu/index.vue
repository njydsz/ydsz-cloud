<!--
  @file 菜单管理
  @description 菜单/权限管理页面：以树形结构展示菜单层级（菜单/按钮/接口三类权限），支持新增根菜单/下级菜单、编辑、删除（存在子菜单时不允许删除）。对应路由 /system/menu。
  @module views/system/menu
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPermissionTree,
  createPermission,
  updatePermission,
  deletePermission,
} from '@/api/system/menu'
import type { MenuTreeVO, PermissionFormDTO } from '@/api/system/menu/types'

const loading = ref(false)
// 菜单权限树形数据
const treeData = ref<MenuTreeVO[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive<PermissionFormDTO & { parentName?: string }>({
  id: undefined,
  parentId: 0,
  permCode: '',
  permName: '',
  permType: 'MENU',
  path: '',
  component: '',
  icon: '',
  sortOrder: 0,
  visible: 1,
  status: 'ENABLED',
})

const formRules = {
  permCode: [{ required: true, message: '权限编码必填', trigger: 'blur' }],
  permName: [{ required: true, message: '权限名称必填', trigger: 'blur' }],
  permType: [{ required: true, message: '权限类型必填', trigger: 'change' }],
}

/** 拉取菜单/权限树形数据 */
async function fetchTree() {
  loading.value = true
  try {
    const { data } = await listPermissionTree()
    treeData.value = data || []
  } finally {
    loading.value = false
  }
}

/** 重置表单为默认值 */
function resetForm() {
  Object.assign(form, {
    id: undefined,
    parentId: 0,
    permCode: '',
    permName: '',
    permType: 'MENU',
    path: '',
    component: '',
    icon: '',
    sortOrder: 0,
    visible: 1,
    status: 'ENABLED',
    parentName: '',
  })
}

/**
 * 打开新增菜单弹窗，传入 parent 时作为其下级新增
 * @param parent 上级菜单节点，未传则新增根菜单
 */
function openCreate(parent?: MenuTreeVO) {
  dialogMode.value = 'create'
  resetForm()
  if (parent) {
    form.parentId = parent.id
    form.parentName = parent.permName
  }
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗，回填节点数据并查找上级名称
 * @param node 待编辑的菜单节点
 */
function openEdit(node: MenuTreeVO) {
  dialogMode.value = 'edit'
  resetForm()
  Object.assign(form, node, { parentName: findParentName(node.parentId) })
  dialogVisible.value = true
}

/**
 * 深度优先查找指定 parentId 对应的菜单名称
 * @param parentId 上级菜单 ID
 * @returns 上级菜单名称，未找到时返回 '#parentId' 或 '根'
 */
function findParentName(parentId?: number): string {
  if (!parentId) return '根'
  let name = ''
  function dfs(list: MenuTreeVO[]) {
    for (const n of list) {
      if (n.id === parentId) {
        name = n.permName
        return
      }
      if (n.children?.length) dfs(n.children)
    }
  }
  dfs(treeData.value)
  return name || `#${parentId}`
}

/** 提交表单：根据 dialogMode 执行创建或更新，成功后刷新树 */
async function submitForm() {
  await formRef.value?.validate()
  if (dialogMode.value === 'create') {
    await createPermission(form)
    ElMessage.success('创建成功')
  } else {
    await updatePermission(form)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  fetchTree()
}

/**
 * 删除菜单：存在子菜单时阻止删除，否则二次确认后执行
 * @param node 待删除的菜单节点
 */
async function handleDelete(node: MenuTreeVO) {
  if (node.children && node.children.length > 0) {
    ElMessage.warning('该菜单下存在子菜单,请先删除子菜单')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除菜单「${node.permName}」吗？`, '提示', { type: 'warning' })
    await deletePermission(node.id)
    ElMessage.success('删除成功')
    fetchTree()
  } catch {
    /* 取消 */
  }
}

onMounted(fetchTree)
</script>

<template>
  <div class="menu-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button v-permission="['auth:perm:create']" type="primary" :icon="'Plus'" @click="openCreate()">
          新增根菜单
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchTree">刷新</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="true"
        border
      >
        <el-table-column prop="permName" label="菜单名称" min-width="220" />
        <el-table-column prop="permCode" label="权限编码" min-width="220" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.permType === 'MENU' ? 'success' : row.permType === 'BUTTON' ? 'warning' : 'info'">
              {{ ({ MENU: '菜单', BUTTON: '按钮', API: '接口' } as any)[row.permType] || row.permType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" width="220" />
        <el-table-column prop="component" label="组件路径" min-width="220" />
        <el-table-column prop="icon" label="图标" width="100" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as any).visible === 0 ? 'info' : 'success'">
              {{ (row as any).visible === 0 ? '隐藏' : '显示' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['auth:perm:create']" type="primary" link @click="openCreate(row as MenuTreeVO)">
              新增下级
            </el-button>
            <el-button v-permission="['auth:perm:update']" type="primary" link @click="openEdit(row as MenuTreeVO)">
              编辑
            </el-button>
            <el-button v-permission="['auth:perm:delete']" type="danger" link @click="handleDelete(row as MenuTreeVO)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增菜单' : '编辑菜单'"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-input :model-value="form.parentName || (form.parentId === 0 ? '根' : `#${form.parentId}`)" disabled />
        </el-form-item>
        <el-form-item label="权限编码" prop="permCode">
          <el-input v-model="form.permCode" placeholder="例如: system:user:create" />
        </el-form-item>
        <el-form-item label="菜单名称" prop="permName">
          <el-input v-model="form.permName" placeholder="例如: 用户管理" />
        </el-form-item>
        <el-form-item label="类型" prop="permType">
          <el-radio-group v-model="form.permType">
            <el-radio value="MENU">菜单</el-radio>
            <el-radio value="BUTTON">按钮</el-radio>
            <el-radio value="API">接口</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.permType !== 'BUTTON'" label="路由路径">
          <el-input v-model="form.path" placeholder="例如: /system/user" />
        </el-form-item>
        <el-form-item v-if="form.permType === 'MENU'" label="组件路径">
          <el-input v-model="form.component" placeholder="例如: system/user/index" />
        </el-form-item>
        <el-form-item v-if="form.permType === 'MENU'" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item v-if="form.permType !== 'BUTTON'" label="是否显示">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.menu-page {
  .toolbar { margin-bottom: $spacing-md; }
}
</style>
