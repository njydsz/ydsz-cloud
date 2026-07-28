<script lang="ts" setup>
/**
 * EVM 挣值管理（列表页）
 * <p>EVM 挣值分析的列表页，展示 PV/EV/AC/CPI/SPI 等指标。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteEvmApi, getEvmPageApi, type EvmApi } from '#/api/evm';
import EvmForm from './evm-form.vue';
defineOptions({ name: 'EvmManagement' });
const gridOptions: VxeGridProps<EvmApi.EvmVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'measureDate', title: '测量日期', width: 120 },
    { field: 'pv', title: 'PV', width: 100 },
    { field: 'ev', title: 'EV', width: 100 },
    { field: 'ac', title: 'AC', width: 100 },
    { field: 'sv', title: 'SV', width: 100 },
    { field: 'cv', title: 'CV', width: 100 },
    { field: 'spi', title: 'SPI', width: 80 },
    { field: 'cpi', title: 'CPI', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getEvmPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      // 无搜索项
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [EvmFormModal, evmFormApi] = useVbenModal({ connectedComponent: EvmForm });
function handleAdd() { evmFormApi.open(); }
function handleEdit(row: EvmApi.EvmVO) { evmFormApi.setData({ record: row }); evmFormApi.open(); }
async function handleDelete(row: EvmApi.EvmVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.measureDate}」吗？`, '删除确认', { type: 'warning' });
    await deleteEvmApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="EVM 挣值管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <EvmFormModal @success="gridApi.query()" />
  </Page>
</template>
