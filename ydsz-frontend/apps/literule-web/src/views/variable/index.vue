<!--
 * 系统变量管理列表页面
 *
 * @path apps\literule-web\src\views\variable\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 系统变量（列表页）
 * <p>系统变量（{@code ydsz_system_variable}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteVariableApi, getVariablePageApi, type VariableApi } from '#/api/variable';
import VariableForm from './variable-form.vue';
defineOptions({ name: 'VariableManagement' });
const gridOptions: VxeGridProps<VariableApi.VariableVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'variableName', title: '变量名称', width: 200 },
    { field: 'variableType', title: '类型', width: 100 },
    { field: 'defaultValue', title: '默认值', width: 150 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getVariablePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'variableName', title: 'variableName', itemRender: { name: 'Input', props: { placeholder: 'variableName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [VariableFormModal, variableFormApi] = useVbenModal({ connectedComponent: VariableForm });
function handleAdd() { variableFormApi.open(); }
function handleEdit(row: VariableApi.VariableVO) { variableFormApi.setData({ record: row }); variableFormApi.open(); }
async function handleDelete(row: VariableApi.VariableVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.variableName}」吗？`, '删除确认', { type: 'warning' });
    await deleteVariableApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="规则变量">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <VariableFormModal @success="gridApi.query()" />
  </Page>
</template>
