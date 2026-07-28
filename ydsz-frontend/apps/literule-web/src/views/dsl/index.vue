<!--
 * 规则 DSL 脚本管理列表页面
 *
 * @path apps\literule-web\src\views\dsl\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 规则 DSL（列表页）
 * <p>规则 DSL 脚本的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteDslApi, getDslPageApi, type DslApi } from '#/api/dsl';
import DslForm from './dsl-form.vue';
defineOptions({ name: 'DslManagement' });
const gridOptions: VxeGridProps<DslApi.DslVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'dslName', title: 'DSL名称', width: 200 },
    { field: 'dslType', title: '类型', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getDslPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'dslName', title: 'dslName', itemRender: { name: 'Input', props: { placeholder: 'dslName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [DslFormModal, dslFormApi] = useVbenModal({ connectedComponent: DslForm });
function handleAdd() { dslFormApi.open(); }
function handleEdit(row: DslApi.DslVO) { dslFormApi.setData({ record: row }); dslFormApi.open(); }
async function handleDelete(row: DslApi.DslVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.dslName}」吗？`, '删除确认', { type: 'warning' });
    await deleteDslApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="DSL管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <DslFormModal @success="gridApi.query()" />
  </Page>
</template>
