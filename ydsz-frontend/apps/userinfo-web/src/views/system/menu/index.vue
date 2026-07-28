<script lang="ts" setup>
/**
 * 菜单（列表页）
 * <p>菜单（{@code ydsz_menu}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag, ElInput } from 'element-plus';
import { h, onMounted, ref } from 'vue';

import {
  createMenuApi,
  deleteMenuApi,
  getMenuListApi,
  updateMenuApi,
  type MenuApi,
} from '#/api/menu';

import MenuForm from './menu-form.vue';

defineOptions({ name: 'MenuManagement' });

const loading = ref(false);
const searchText = ref('');
const tableData = ref<MenuApi.MenuVO[]>([]);

async function loadData() {
  loading.value = true;
  try {
    const list = await getMenuListApi();
    tableData.value = buildTree(list);
  } finally {
    loading.value = false;
  }
}

function buildTree(list: MenuApi.MenuVO[]): MenuApi.MenuVO[] {
  const map = new Map<string, MenuApi.MenuVO>();
  const roots: MenuApi.MenuVO[] = [];
  list.forEach((item) => {
    map.set(item.id, { ...item, children: [] });
  });
  list.forEach((item) => {
    const node = map.get(item.id);
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId)!.children!.push(node!);
    } else {
      roots.push(node!);
    }
  });
  return roots;
}

onMounted(loadData);

const menuTypeMap: Record<number, { label: string; type: any }> = {
  0: { label: '目录', type: 'primary' },
  1: { label: '菜单', type: 'success' },
  2: { label: '按钮', type: 'warning' },
};

// ========== Form ==========
const [MenuFormModal, menuFormApi] = useVbenModal({ connectedComponent: MenuForm });

function handleAdd(parentId?: string) {
  menuFormApi.setData({ tableData: tableData.value, parentId });
  menuFormApi.open();
}

function handleEdit(row: MenuApi.MenuVO) {
  menuFormApi.setData({ record: row, tableData: tableData.value });
  menuFormApi.open();
}

async function handleDelete(row: MenuApi.MenuVO) {
  try {
    await ElMessageBox.confirm(`确定删除菜单「${row.menuName}」吗？`, '删除确认', { type: 'warning' });
    await deleteMenuApi(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <div class="p-4">
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-lg font-semibold">菜单管理</h3>
        <div class="flex gap-2">
          <ElInput v-model="searchText" placeholder="搜索菜单名称" clearable style="width: 200px" />
          <ElButton @click="loadData">刷新</ElButton>
          <ElButton type="primary" @click="handleAdd()">新增顶级菜单</ElButton>
        </div>
      </div>
      <ElTable
        v-loading="loading"
        :data="tableData"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <ElTableColumn prop="menuName" label="菜单名称" min-width="180" />
        <ElTableColumn label="类型" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="menuTypeMap[row.menuType]?.type || 'info'" size="small">
              {{ menuTypeMap[row.menuType]?.label || '未知' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="icon" label="图标" width="80" align="center" />
        <ElTableColumn prop="path" label="路由路径" width="160" />
        <ElTableColumn prop="component" label="组件路径" width="200" />
        <ElTableColumn prop="permission" label="权限标识" width="160" />
        <ElTableColumn prop="sort" label="排序" width="80" align="center" />
        <ElTableColumn label="状态" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" link type="primary" @click="handleAdd(row.id)">新增子菜单</ElButton>
            <ElButton size="small" link type="primary" @click="handleEdit(row)">编辑</ElButton>
            <ElButton size="small" link type="danger" @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
    <MenuFormModal @success="loadData" />
  </Page>
</template>
