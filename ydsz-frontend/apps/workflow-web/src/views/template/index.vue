<!--
 * 流程模板（列表页）
 *
 * @path apps\workflow-web\src\views\template\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 流程模板（列表页）
 * <p>流程模板（{@code ydsz_flow_definition}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteTemplateApi, getTemplatePageApi, type TemplateApi } from '#/api/template';
import TemplateForm from './template-form.vue';
defineOptions({ name: 'TemplateManagement' });
const gridOptions: VxeGridProps<TemplateApi.TemplateVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'templateCode', title: '模板编码', width: 150 },
    { field: 'templateName', title: '模板名称', width: 200 },
    { field: 'category', title: '分类', width: 100 },
    { field: 'version', title: '版本', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getTemplatePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'templateName', title: 'templateName', itemRender: { name: 'Input', props: { placeholder: 'templateName' } } },
      { field: 'templateCode', title: 'templateCode', itemRender: { name: 'Input', props: { placeholder: 'templateCode' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [TemplateFormModal, templateFormApi] = useVbenModal({ connectedComponent: TemplateForm });
function handleAdd() { templateFormApi.open(); }
function handleEdit(row: TemplateApi.TemplateVO) { templateFormApi.setData({ record: row }); templateFormApi.open(); }
async function handleDelete(row: TemplateApi.TemplateVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.templateName}」吗？`, '删除确认', { type: 'warning' });
    await deleteTemplateApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="流程模板">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <TemplateFormModal @success="gridApi.query()" />
  </Page>
</template>
