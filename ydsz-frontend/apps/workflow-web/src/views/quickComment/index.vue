<!--
 * 快捷回复（列表页）
 *
 * @path apps\workflow-web\src\views\quickComment\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 快捷回复（列表页）
 * <p>审批快捷回复模板的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteQuickCommentApi, getQuickCommentPageApi, type QuickCommentApi } from '#/api/quickComment';
import QuickCommentForm from './quickComment-form.vue';
defineOptions({ name: 'QuickCommentManagement' });
const gridOptions: VxeGridProps<QuickCommentApi.QuickCommentVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'content', title: '评语内容', width: 300 },
    { field: 'category', title: '分类', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getQuickCommentPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'content', title: 'content', itemRender: { name: 'Input', props: { placeholder: 'content' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [QuickCommentFormModal, quickCommentFormApi] = useVbenModal({ connectedComponent: QuickCommentForm });
function handleAdd() { quickCommentFormApi.open(); }
function handleEdit(row: QuickCommentApi.QuickCommentVO) { quickCommentFormApi.setData({ record: row }); quickCommentFormApi.open(); }
async function handleDelete(row: QuickCommentApi.QuickCommentVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.content}」吗？`, '删除确认', { type: 'warning' });
    await deleteQuickCommentApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="快捷评语">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <QuickCommentFormModal @success="gridApi.query()" />
  </Page>
</template>
