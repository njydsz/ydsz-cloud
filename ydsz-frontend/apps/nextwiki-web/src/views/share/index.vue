<!--
 * apps 列表/管理页面组件
 *
 * @path apps\nextwiki-web\src\views\share\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 文件分享（列表页）
 * <p>文件分享链接的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteShareApi, getSharePageApi, type ShareApi } from '#/api/share';
import ShareForm from './share-form.vue';
defineOptions({ name: 'ShareManagement' });
const gridOptions: VxeGridProps<ShareApi.ShareVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'fileName', title: '文件名称', width: 200 },
    { field: 'shareTo', title: '分享给', width: 100 },
    { field: 'permission', title: '权限', width: 100 },
    { field: 'expireDate', title: '过期日期', width: 120 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getSharePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      // 无搜索项
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [ShareFormModal, shareFormApi] = useVbenModal({ connectedComponent: ShareForm });
function handleAdd() { shareFormApi.open(); }
function handleEdit(row: ShareApi.ShareVO) { shareFormApi.setData({ record: row }); shareFormApi.open(); }
async function handleDelete(row: ShareApi.ShareVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.fileName}」吗？`, '删除确认', { type: 'warning' });
    await deleteShareApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="文件分享">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <ShareFormModal @success="gridApi.query()" />
  </Page>
</template>
