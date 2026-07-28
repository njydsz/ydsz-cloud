<script lang="ts" setup>
/**
 * 死信队列（列表页）
 * <p>死信队列的查询页，管理发送失败的消息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteDeadLetterApi, getDeadLetterPageApi, type DeadLetterApi } from '#/api/deadLetter';
import DeadLetterForm from './deadLetter-form.vue';
defineOptions({ name: 'DeadLetterManagement' });
const gridOptions: VxeGridProps<DeadLetterApi.DeadLetterVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'messageId', title: '消息ID', width: 150 },
    { field: 'channel', title: '通道', width: 100 },
    { field: 'errorMessage', title: '错误信息', width: 200 },
    { field: 'retryCount', title: '重试次数', width: 80 },
    { field: 'status', title: '状态', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getDeadLetterPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'messageId', title: 'messageId', itemRender: { name: 'Input', props: { placeholder: 'messageId' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [DeadLetterFormModal, deadLetterFormApi] = useVbenModal({ connectedComponent: DeadLetterForm });
function handleAdd() { deadLetterFormApi.open(); }
function handleEdit(row: DeadLetterApi.DeadLetterVO) { deadLetterFormApi.setData({ record: row }); deadLetterFormApi.open(); }
async function handleDelete(row: DeadLetterApi.DeadLetterVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.messageId}」吗？`, '删除确认', { type: 'warning' });
    await deleteDeadLetterApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="死信队列">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <DeadLetterFormModal @success="gridApi.query()" />
  </Page>
</template>
