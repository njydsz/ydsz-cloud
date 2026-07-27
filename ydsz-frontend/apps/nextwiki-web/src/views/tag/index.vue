<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteTagApi, getTagPageApi, type TagApi } from '#/api/tag';
import TagForm from './tag-form.vue';
defineOptions({ name: 'TagManagement' });
const gridOptions: VxeGridProps<TagApi.TagVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'tagName', title: '标签名称', width: 200 },
    { field: 'tagColor', title: '颜色', width: 100 },
    { field: 'fileCount', title: '文件数', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getTagPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'tagName', title: 'tagName', itemRender: { name: 'Input', props: { placeholder: 'tagName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [TagFormModal, tagFormApi] = useVbenModal({ connectedComponent: TagForm });
function handleAdd() { tagFormApi.open(); }
function handleEdit(row: TagApi.TagVO) { tagFormApi.setData({ record: row }); tagFormApi.open(); }
async function handleDelete(row: TagApi.TagVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.tagName}」吗？`, '删除确认', { type: 'warning' });
    await deleteTagApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="标签管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <TagFormModal @success="gridApi.query()" />
  </Page>
</template>
