<!--
 * apps 列表/管理页面组件
 *
 * @path apps\message-web\src\views\message\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 消息（列表页）
 * <p>消息发送记录的查询页，支持站内/邮件/短信/企微/钉钉/飞书多渠道。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteMessageApi, getMessagePageApi, type MessageApi } from '#/api/message';
import MessageForm from './message-form.vue';
defineOptions({ name: 'MessageManagement' });
const gridOptions: VxeGridProps<MessageApi.MessageVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'messageId', title: '消息ID', width: 150 },
    { field: 'channel', title: '通道', width: 100 },
    { field: 'recipient', title: '接收者', width: 150 },
    { field: 'subject', title: '主题', width: 200 },
    { field: 'status', title: '状态', width: 100 },
    { field: 'sendTime', title: '发送时间', width: 160 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getMessagePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'channel', title: 'channel', itemRender: { name: 'Input', props: { placeholder: 'channel' } } },
      { field: 'status', title: 'status', itemRender: { name: 'Input', props: { placeholder: 'status' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [MessageFormModal, messageFormApi] = useVbenModal({ connectedComponent: MessageForm });
function handleAdd() { messageFormApi.open(); }
function handleEdit(row: MessageApi.MessageVO) { messageFormApi.setData({ record: row }); messageFormApi.open(); }
async function handleDelete(row: MessageApi.MessageVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.messageId}」吗？`, '删除确认', { type: 'warning' });
    await deleteMessageApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="消息管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <MessageFormModal @success="gridApi.query()" />
  </Page>
</template>
