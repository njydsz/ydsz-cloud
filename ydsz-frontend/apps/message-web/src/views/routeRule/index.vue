<script lang="ts" setup>
/**
 * 消息路由（列表页）
 * <p>消息路由规则的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteRouteRuleApi, getRouteRulePageApi, type RouteRuleApi } from '#/api/routeRule';
import RouteRuleForm from './routeRule-form.vue';
defineOptions({ name: 'RouteRuleManagement' });
const gridOptions: VxeGridProps<RouteRuleApi.RouteRuleVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'ruleName', title: '规则名称', width: 200 },
    { field: 'channel', title: '通道', width: 100 },
    { field: 'priority', title: '优先级', width: 80 },
    { field: 'targetChannel', title: '目标通道', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getRouteRulePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'ruleName', title: 'ruleName', itemRender: { name: 'Input', props: { placeholder: 'ruleName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [RouteRuleFormModal, routeRuleFormApi] = useVbenModal({ connectedComponent: RouteRuleForm });
function handleAdd() { routeRuleFormApi.open(); }
function handleEdit(row: RouteRuleApi.RouteRuleVO) { routeRuleFormApi.setData({ record: row }); routeRuleFormApi.open(); }
async function handleDelete(row: RouteRuleApi.RouteRuleVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.ruleName}」吗？`, '删除确认', { type: 'warning' });
    await deleteRouteRuleApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="路由规则">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <RouteRuleFormModal @success="gridApi.query()" />
  </Page>
</template>
