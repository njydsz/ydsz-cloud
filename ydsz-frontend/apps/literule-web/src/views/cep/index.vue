<!--
 * apps 列表/管理页面组件
 *
 * @path apps\literule-web\src\views\cep\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 规则 CEP（列表页）
 * <p>复杂事件处理规则的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteCepApi, getCepPageApi, type CepApi } from '#/api/cep';
import CepForm from './cep-form.vue';
defineOptions({ name: 'CepManagement' });
const gridOptions: VxeGridProps<CepApi.CepVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'cepName', title: 'CEP名称', width: 200 },
    { field: 'cepPattern', title: '匹配模式', width: 200 },
    { field: 'windowSize', title: '窗口大小', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getCepPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'cepName', title: 'cepName', itemRender: { name: 'Input', props: { placeholder: 'cepName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [CepFormModal, cepFormApi] = useVbenModal({ connectedComponent: CepForm });
function handleAdd() { cepFormApi.open(); }
function handleEdit(row: CepApi.CepVO) { cepFormApi.setData({ record: row }); cepFormApi.open(); }
async function handleDelete(row: CepApi.CepVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.cepName}」吗？`, '删除确认', { type: 'warning' });
    await deleteCepApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="CEP复杂事件">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <CepFormModal @success="gridApi.query()" />
  </Page>
</template>
