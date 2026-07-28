<!--
 * 流程分类（列表页）
 *
 * @path apps\workflow-web\src\views\category\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 流程分类（列表页）
 * <p>流程分类（{@code ydsz_flow_category}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteCategoryApi, getCategoryPageApi, type CategoryApi } from '#/api/category';
import CategoryForm from './category-form.vue';
defineOptions({ name: 'CategoryManagement' });
const gridOptions: VxeGridProps<CategoryApi.CategoryVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'categoryCode', title: '编码', width: 150 },
    { field: 'categoryName', title: '名称', width: 200 },
    { field: 'sort', title: '排序', width: 80 },
    { field: 'status', title: '状态', width: 80 },
    { field: 'createTime', title: '创建时间', width: 160 },
    {
      field: 'action', title: '操作', width: 160, fixed: 'right',
      slots: { default: ({ row }) => h('div', { class: 'flex gap-1' }, [
        h(ElButton, { size: 'small', link: true, type: 'primary', onClick: () => handleEdit(row) }, () => '编辑'),
        h(ElButton, { size: 'small', link: true, type: 'danger', onClick: () => handleDelete(row) }, () => '删除'),
      ]) },
    },
  ],
  height: 'auto',
  pagerConfig: { pageSize: 20, pageSizes: [10, 20, 50, 100] },
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getCategoryPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'categoryName', title: 'categoryName', itemRender: { name: 'Input', props: { placeholder: 'categoryName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [CategoryFormModal, categoryFormApi] = useVbenModal({ connectedComponent: CategoryForm });
function handleAdd() { categoryFormApi.open(); }
function handleEdit(row: CategoryApi.CategoryVO) { categoryFormApi.setData({ record: row }); categoryFormApi.open(); }
async function handleDelete(row: CategoryApi.CategoryVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.categoryName}」吗？`, '删除确认', { type: 'warning' });
    await deleteCategoryApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="流程分类">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <CategoryFormModal @success="gridApi.query()" />
  </Page>
</template>
