<script lang="ts" setup>
/**
 * 文件评论（列表页）
 * <p>文件评论的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteCommentApi, getCommentPageApi, type CommentApi } from '#/api/comment';
import CommentForm from './comment-form.vue';
defineOptions({ name: 'CommentManagement' });
const gridOptions: VxeGridProps<CommentApi.CommentVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'fileName', title: '文件名称', width: 200 },
    { field: 'userId', title: '评论人', width: 100 },
    { field: 'content', title: '评论内容', width: 300 },
    { field: 'createTime', title: '评论时间', width: 160 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getCommentPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'fileId', title: 'fileId', itemRender: { name: 'Input', props: { placeholder: 'fileId' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [CommentFormModal, commentFormApi] = useVbenModal({ connectedComponent: CommentForm });
function handleAdd() { commentFormApi.open(); }
function handleEdit(row: CommentApi.CommentVO) { commentFormApi.setData({ record: row }); commentFormApi.open(); }
async function handleDelete(row: CommentApi.CommentVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.fileName}」吗？`, '删除确认', { type: 'warning' });
    await deleteCommentApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="文件评论">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <CommentFormModal @success="gridApi.query()" />
  </Page>
</template>
