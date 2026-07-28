<script lang="ts" setup>
/**
 * 项目计费卡（列表页）
 * <p>计费卡（{@code ydsz_project_rate_card}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteRateCardApi, getRateCardPageApi, type RateCardApi } from '#/api/rateCard';
import RateCardForm from './rateCard-form.vue';
defineOptions({ name: 'RateCardManagement' });
const gridOptions: VxeGridProps<RateCardApi.RateCardVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'rateName', title: '费率名称', width: 150 },
    { field: 'roleLevel', title: '角色等级', width: 100 },
    { field: 'standardRate', title: '标准费率', width: 120 },
    { field: 'overtimeRate', title: '加班费率', width: 120 },
    { field: 'currency', title: '币种', width: 80 },
    { field: 'effectiveDate', title: '生效日期', width: 120 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getRateCardPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'rateName', title: 'rateName', itemRender: { name: 'Input', props: { placeholder: 'rateName' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [RateCardFormModal, rateCardFormApi] = useVbenModal({ connectedComponent: RateCardForm });
function handleAdd() { rateCardFormApi.open(); }
function handleEdit(row: RateCardApi.RateCardVO) { rateCardFormApi.setData({ record: row }); rateCardFormApi.open(); }
async function handleDelete(row: RateCardApi.RateCardVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.rateName}」吗？`, '删除确认', { type: 'warning' });
    await deleteRateCardApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="费率卡管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <RateCardFormModal @success="gridApi.query()" />
  </Page>
</template>
