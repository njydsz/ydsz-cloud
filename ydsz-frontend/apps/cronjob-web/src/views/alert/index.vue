<!--
 * 任务告警（列表页）
 *
 * @path apps\cronjob-web\src\views\alert\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 任务告警（列表页）
 * <p>任务告警规则的列表页，配置告警通道、抑制策略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteAlertApi, getAlertPageApi, type AlertApi } from '#/api/alert';
import AlertForm from './alert-form.vue';
defineOptions({ name: 'AlertManagement' });
const gridOptions: VxeGridProps<AlertApi.AlertVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'alertName', title: '告警名称', width: 200 },
    { field: 'alertType', title: '类型', width: 100 },
    { field: 'alertLevel', title: '级别', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getAlertPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'alertName', title: 'alertName', itemRender: { name: 'Input', props: { placeholder: 'alertName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [AlertFormModal, alertFormApi] = useVbenModal({ connectedComponent: AlertForm });
function handleAdd() { alertFormApi.open(); }
function handleEdit(row: AlertApi.AlertVO) { alertFormApi.setData({ record: row }); alertFormApi.open(); }
async function handleDelete(row: AlertApi.AlertVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.alertName}」吗？`, '删除确认', { type: 'warning' });
    await deleteAlertApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="告警管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <AlertFormModal @success="gridApi.query()" />
  </Page>
</template>
