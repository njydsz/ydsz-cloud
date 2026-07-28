<!--
 * apps 列表/管理页面组件
 *
 * @path apps\project-web\src\views\risk\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目风险（列表页）
 * <p>项目风险（{@code ydsz_project_risk}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteRiskApi, getRiskPageApi, type RiskApi } from '#/api/risk';
import RiskForm from './risk-form.vue';
defineOptions({ name: 'RiskManagement' });
const gridOptions: VxeGridProps<RiskApi.RiskVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'riskName', title: '风险名称', width: 200 },
    { field: 'riskType', title: '风险类型', width: 100 },
    { field: 'probability', title: '概率', width: 80 },
    { field: 'impact', title: '影响', width: 80 },
    { field: 'riskLevel', title: '风险等级', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getRiskPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'riskName', title: 'riskName', itemRender: { name: 'Input', props: { placeholder: 'riskName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [RiskFormModal, riskFormApi] = useVbenModal({ connectedComponent: RiskForm });
function handleAdd() { riskFormApi.open(); }
function handleEdit(row: RiskApi.RiskVO) { riskFormApi.setData({ record: row }); riskFormApi.open(); }
async function handleDelete(row: RiskApi.RiskVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.riskName}」吗？`, '删除确认', { type: 'warning' });
    await deleteRiskApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="风险管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <RiskFormModal @success="gridApi.query()" />
  </Page>
</template>
