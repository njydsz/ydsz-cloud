<script lang="ts" setup>
/**
 * 部门（列表页）
 * <p>部门（{@code ydsz_dept}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag, ElTreeSelect, ElInputNumber, ElRadioGroup, ElRadio } from 'element-plus';
import { h, onMounted, ref } from 'vue';

import {
  createDeptApi,
  deleteDeptApi,
  getDeptListApi,
  getDeptTreeApi,
  updateDeptApi,
  type DeptApi,
} from '#/api/dept';
import { getCompanyListApi, type CompanyApi } from '#/api/company';

import DeptForm from './dept-form.vue';

defineOptions({ name: 'DeptManagement' });

const loading = ref(false);
const tableData = ref<DeptApi.DepartmentVO[]>([]);
const treeData = ref<DeptApi.DepartmentTreeVO[]>([]);
const companyList = ref<CompanyApi.CompanyVO[]>([]);

async function loadData() {
  loading.value = true;
  try {
    const [list, tree, companies] = await Promise.all([
      getDeptListApi(),
      getDeptTreeApi(),
      getCompanyListApi(),
    ]);
    tableData.value = buildTree(list);
    treeData.value = tree;
    companyList.value = companies;
  } finally {
    loading.value = false;
  }
}

function buildTree(list: DeptApi.DepartmentVO[]): DeptApi.DepartmentVO[] {
  const map = new Map<string, DeptApi.DepartmentVO>();
  const roots: DeptApi.DepartmentVO[] = [];
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

// ========== Form ==========
const [DeptFormModal, deptFormApi] = useVbenModal({
  connectedComponent: DeptForm,
});

function handleAdd(parentId?: string) {
  deptFormApi.setData({
    treeData: treeData.value,
    companyList: companyList.value,
    parentId,
  });
  deptFormApi.open();
}

function handleEdit(row: DeptApi.DepartmentVO) {
  deptFormApi.setData({
    record: row,
    treeData: treeData.value,
    companyList: companyList.value,
  });
  deptFormApi.open();
}

async function handleDelete(row: DeptApi.DepartmentVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除部门「${row.deptName}」吗？`,
      '删除确认',
      { type: 'warning' },
    );
    await deleteDeptApi(row.id);
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
        <h3 class="text-lg font-semibold">部门管理</h3>
        <div class="flex gap-2">
          <ElButton @click="loadData">刷新</ElButton>
          <ElButton type="primary" @click="handleAdd()">新增顶级部门</ElButton>
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
        <ElTableColumn prop="deptName" label="部门名称" min-width="180" />
        <ElTableColumn prop="leader" label="负责人" width="100" />
        <ElTableColumn prop="phone" label="联系电话" width="130" />
        <ElTableColumn prop="email" label="邮箱" width="180" />
        <ElTableColumn prop="sort" label="排序" width="80" align="center" />
        <ElTableColumn label="状态" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" link type="primary" @click="handleAdd(row.id)">
              新增子部门
            </ElButton>
            <ElButton size="small" link type="primary" @click="handleEdit(row)">
              编辑
            </ElButton>
            <ElButton size="small" link type="danger" @click="handleDelete(row)">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
    <DeptFormModal @success="loadData" />
  </Page>
</template>
