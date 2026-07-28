<!--
 * apps 列表/管理页面组件
 *
 * @path apps\literule-web\src\views\rule\index.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 规则定义（列表页）
 * <p>规则定义的列表页，支持决策表/决策树/评分卡/脚本多种规则类型。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps } from '@ydsz/plugins/vxe-table';
import { Page, useVbenModal } from '@ydsz/common-ui';
import { ElButton, ElMessage, ElMessageBox, ElTag, h } from 'element-plus';
import { useYDSZVxeGrid } from '#/adapter/vxe-table';
import { deleteRuleApi, getRulePageApi, type RuleApi } from '#/api/rule';
import RuleForm from './rule-form.vue';
defineOptions({ name: 'RuleManagement' });
const gridOptions: VxeGridProps<RuleApi.RuleVO> = {
  columns: [
    { type: 'seq', width: 50, title: '序号' },
    { field: 'ruleCode', title: '规则编码', width: 150 },
    { field: 'ruleName', title: '规则名称', width: 200 },
    { field: 'ruleType', title: '类型', width: 100 },
    { field: 'priority', title: '优先级', width: 80 },
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
  proxyConfig: { ajax: { query: async ({ page }, formValues) => await getRulePageApi({ pageNum: page.currentPage, pageSize: page.pageSize, ...formValues }) } },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, search: true, zoom: true },
  formConfig: { enabled: true, items: [
      { field: 'ruleName', title: 'ruleName', itemRender: { name: 'Input', props: { placeholder: 'ruleName' } } },
      { field: 'ruleCode', title: 'ruleCode', itemRender: { name: 'Input', props: { placeholder: 'ruleCode' } } },
  ] },
};
const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
const [RuleFormModal, ruleFormApi] = useVbenModal({ connectedComponent: RuleForm });
function handleAdd() { ruleFormApi.open(); }
function handleEdit(row: RuleApi.RuleVO) { ruleFormApi.setData({ record: row }); ruleFormApi.open(); }
async function handleDelete(row: RuleApi.RuleVO) {
  try { await ElMessageBox.confirm(`确定删除「${row.ruleName}」吗？`, '删除确认', { type: 'warning' });
    await deleteRuleApi(row.id); ElMessage.success('删除成功'); gridApi.query();
  } catch {}
}
</script>
<template>
  <Page auto-content-height>
    <Grid table-title="规则管理">
      <template #toolbar-tools><ElButton type="primary" @click="handleAdd">新增</ElButton></template>
    </Grid>
    <RuleFormModal @success="gridApi.query()" />
  </Page>
</template>
