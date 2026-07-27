<script lang="ts" setup>
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteFileApi, getFilePageApi, type FileApi } from '#/api/file';
import FileForm from './file-form.vue';
defineOptions({ name: 'FileManagement' });
const gridOptions: VxeGridProps<FileApi.FileVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'fileName', title: '文件名称', width: 250 },
    { field: 'fileSize', title: '大小', width: 100 },
    { field: 'fileType', title: '类型', width: 80 },
    { field: 'uploadBy', title: '上传者', width: 100 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getFilePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'fileName', title: 'fileName', itemRender: { name: 'Input', props: { placeholder: 'fileName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [FileFormModal, fileFormApi] = useVbenModal({ connectedComponent: FileForm });
function handleAdd() { fileFormApi.open(); }
function handleEdit(row: FileApi.FileVO) { fileFormApi.setData({ record: row }); fileFormApi.open(); }
async function handleDelete(row: FileApi.FileVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.fileName}」吗？`, '删除确认', { type: 'warning' });
    await deleteFileApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="文件管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <FileFormModal @success="gridApi.query()" />
  </Page>
</template>
