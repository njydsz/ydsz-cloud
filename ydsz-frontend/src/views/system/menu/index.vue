<!--
  @file 菜单管理
  @description 菜单/权限管理页面：以树形结构展示菜单层级（菜单/按钮/接口三类权限），支持新增根菜单/下级菜单、编辑、删除（存在子菜单时不允许删除）。对应路由 /system/menu。
  @module views/system/menu
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPermissionTree,
  createPermission,
  updatePermission,
  deletePermission,
} from '@/api/system/menu'
import type { MenuTreeVO, PermissionFormDTO } from '@/api/system/menu/types'

const { t } = useI18n()

/** 菜单树加载状态 */
const loading = ref(false)
/** 菜单权限树形数据 */
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
  permCode: [{ required: true, message: t('system.menu.rules.permCodeRequired'), trigger: 'blur' }],
  permName: [{ required: true, message: t('system.menu.rules.permNameRequired'), trigger: 'blur' }],
  permType: [{ required: true, message: t('system.menu.rules.permTypeRequired'), trigger: 'change' }],
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
  if (!parentId) return t('system.menu.form.parentRoot')
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
    ElMessage.success(t('system.menu.messages.createSuccess'))
  } else {
    await updatePermission(form)
    ElMessage.success(t('system.menu.messages.updateSuccess'))
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
    ElMessage.warning(t('system.menu.messages.hasChildren'))
    return
  }
  try {
    await ElMessageBox.confirm(t('system.menu.messages.confirmDelete', { name: node.permName }), t('common.tip'), { type: 'warning' })
    await deletePermission(node.id)
    ElMessage.success(t('system.menu.messages.deleteSuccess'))
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
          {{ t('system.menu.buttons.createRoot') }}
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchTree">{{ t('system.menu.buttons.refresh') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="true"
        border
      >
        <el-table-column prop="permName" :label="t('system.menu.columns.permName')" min-width="220" />
        <el-table-column prop="permCode" :label="t('system.menu.columns.permCode')" min-width="220" />
        <el-table-column :label="t('system.menu.columns.type')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.permType === 'MENU' ? 'success' : row.permType === 'BUTTON' ? 'warning' : 'info'">
              {{ t(`system.menu.type.${row.permType}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" :label="t('system.menu.columns.path')" width="220" />
        <el-table-column prop="component" :label="t('system.menu.columns.component')" min-width="220" />
        <el-table-column prop="icon" :label="t('system.menu.columns.icon')" width="100" align="center" />
        <el-table-column prop="sortOrder" :label="t('system.menu.columns.sortOrder')" width="80" align="center" />
        <el-table-column :label="t('system.menu.columns.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as any).visible === 0 ? 'info' : 'success'">
              {{ (row as any).visible === 0 ? t('system.menu.visible.hide') : t('system.menu.visible.show') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.menu.columns.action')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['auth:perm:create']" type="primary" link @click="openCreate(row as MenuTreeVO)">
              {{ t('system.dept.buttons.createSub') }}
            </el-button>
            <el-button v-permission="['auth:perm:update']" type="primary" link @click="openEdit(row as MenuTreeVO)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="['auth:perm:delete']" type="danger" link @click="handleDelete(row as MenuTreeVO)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('system.menu.dialog.createTitle') : t('system.menu.dialog.editTitle')"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('system.menu.form.parentMenu')">
          <el-input :model-value="form.parentName || (form.parentId === 0 ? t('system.menu.form.parentRoot') : `#${form.parentId}`)" disabled />
        </el-form-item>
        <el-form-item :label="t('system.menu.form.permCode')" prop="permCode">
          <el-input v-model="form.permCode" :placeholder="t('system.menu.form.permCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.menu.form.permName')" prop="permName">
          <el-input v-model="form.permName" :placeholder="t('system.menu.form.permNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.menu.form.type')" prop="permType">
          <el-radio-group v-model="form.permType">
            <el-radio value="MENU">{{ t('system.menu.type.MENU') }}</el-radio>
            <el-radio value="BUTTON">{{ t('system.menu.type.BUTTON') }}</el-radio>
            <el-radio value="API">{{ t('system.menu.type.API') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.permType !== 'BUTTON'" :label="t('system.menu.form.path')">
          <el-input v-model="form.path" :placeholder="t('system.menu.form.pathPlaceholder')" />
        </el-form-item>
        <el-form-item v-if="form.permType === 'MENU'" :label="t('system.menu.form.component')">
          <el-input v-model="form.component" :placeholder="t('system.menu.form.componentPlaceholder')" />
        </el-form-item>
        <el-form-item v-if="form.permType === 'MENU'" :label="t('system.menu.form.icon')">
          <el-input v-model="form.icon" :placeholder="t('system.menu.form.iconPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.menu.form.sortOrder')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item v-if="form.permType !== 'BUTTON'" :label="t('system.menu.form.visible')">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">{{ t('system.menu.visible.show') }}</el-radio>
            <el-radio :value="0">{{ t('system.menu.visible.hide') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.menu-page {
  .toolbar { margin-bottom: $spacing-md; }
}
</style>
