<!--
 * apps 列表/管理页面组件
 *
 * @path apps\project-web\src\views\contract\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 项目合同（列表页）
 * <p>项目合同（{@code ydsz_project_contract}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteContractApi, getContractPageApi, type ContractApi } from '#/api/contract';
import ContractForm from './contract-form.vue';
defineOptions({ name: 'ContractManagement' });
const gridOptions: VxeGridProps<ContractApi.ContractVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'contractCode', title: '合同编号', width: 150 },
    { field: 'contractName', title: '合同名称', width: 200 },
    { field: 'customerName', title: '客户名称', width: 150 },
    { field: 'contractAmount', title: '合同金额', width: 120 },
    { field: 'contractType', title: '合同类型', width: 100 },
    { field: 'signDate', title: '签订日期', width: 120 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getContractPageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'contractName', title: 'contractName', itemRender: { name: 'Input', props: { placeholder: 'contractName' } } },
      { field: 'contractCode', title: 'contractCode', itemRender: { name: 'Input', props: { placeholder: 'contractCode' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [ContractFormModal, contractFormApi] = useVbenModal({ connectedComponent: ContractForm });
function handleAdd() { contractFormApi.open(); }
function handleEdit(row: ContractApi.ContractVO) { contractFormApi.setData({ record: row }); contractFormApi.open(); }
async function handleDelete(row: ContractApi.ContractVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.contractName}」吗？`, '删除确认', { type: 'warning' });
    await deleteContractApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="合同管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <ContractFormModal @success="gridApi.query()" />
  </Page>
</template>
