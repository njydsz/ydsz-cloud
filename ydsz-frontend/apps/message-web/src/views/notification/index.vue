<!--
 * apps 列表/管理页面组件
 *
 * @path apps\message-web\src\views\notification\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 站内通知（列表页）
 * <p>站内消息收件箱的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteNotificationApi, getNotificationPageApi, type NotificationApi } from '#/api/notification';
import NotificationForm from './notification-form.vue';
defineOptions({ name: 'NotificationManagement' });
const gridOptions: VxeGridProps<NotificationApi.NotificationVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'title', title: '标题', width: 200 },
    { field: 'type', title: '类型', width: 100 },
    { field: 'isRead', title: '已读', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getNotificationPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'title', title: 'title', itemRender: { name: 'Input', props: { placeholder: 'title' } } },
      { field: 'type', title: 'type', itemRender: { name: 'Input', props: { placeholder: 'type' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [NotificationFormModal, notificationFormApi] = useVbenModal({ connectedComponent: NotificationForm });
function handleAdd() { notificationFormApi.open(); }
function handleEdit(row: NotificationApi.NotificationVO) { notificationFormApi.setData({ record: row }); notificationFormApi.open(); }
async function handleDelete(row: NotificationApi.NotificationVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '删除确认', { type: 'warning' });
    await deleteNotificationApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="站内通知">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <NotificationFormModal @success="gridApi.query()" />
  </Page>
</template>
