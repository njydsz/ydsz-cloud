<script lang="ts" setup>
/**
 * 规则断点（列表页）
 * <p>规则断点调试的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteBreakpointApi, getBreakpointPageApi, type BreakpointApi } from '#/api/breakpoint';
import BreakpointForm from './breakpoint-form.vue';
defineOptions({ name: 'BreakpointManagement' });
const gridOptions: VxeGridProps<BreakpointApi.BreakpointVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'ruleCode', title: '规则编码', width: 200 },
    { field: 'condition', title: '断点条件', width: 250 },
    { field: 'hitCount', title: '命中次数', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getBreakpointPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'ruleCode', title: 'ruleCode', itemRender: { name: 'Input', props: { placeholder: 'ruleCode' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [BreakpointFormModal, breakpointFormApi] = useVbenModal({ connectedComponent: BreakpointForm });
function handleAdd() { breakpointFormApi.open(); }
function handleEdit(row: BreakpointApi.BreakpointVO) { breakpointFormApi.setData({ record: row }); breakpointFormApi.open(); }
async function handleDelete(row: BreakpointApi.BreakpointVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.ruleCode}」吗？`, '删除确认', { type: 'warning' });
    await deleteBreakpointApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="断点调试">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <BreakpointFormModal @success="gridApi.query()" />
  </Page>
</template>
