<script lang="ts" setup>
/**
 * 文件配额（列表页）
 * <p>租户文件配额的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteQuotaApi, getQuotaPageApi, type QuotaApi } from '#/api/quota';
import QuotaForm from './quota-form.vue';
defineOptions({ name: 'QuotaManagement' });
const gridOptions: VxeGridProps<QuotaApi.QuotaVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'userId', title: '用户ID', width: 150 },
    { field: 'totalQuota', title: '总配额', width: 100 },
    { field: 'usedQuota', title: '已用', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getQuotaPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'userId', title: 'userId', itemRender: { name: 'Input', props: { placeholder: 'userId' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [QuotaFormModal, quotaFormApi] = useVbenModal({ connectedComponent: QuotaForm });
function handleAdd() { quotaFormApi.open(); }
function handleEdit(row: QuotaApi.QuotaVO) { quotaFormApi.setData({ record: row }); quotaFormApi.open(); }
async function handleDelete(row: QuotaApi.QuotaVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.userId}」吗？`, '删除确认', { type: 'warning' });
    await deleteQuotaApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="存储配额">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <QuotaFormModal @success="gridApi.query()" />
  </Page>
</template>
