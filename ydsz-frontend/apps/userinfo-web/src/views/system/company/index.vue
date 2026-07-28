<script lang="ts" setup>
/**
 * 公司（列表页）
 * <p>公司（{@code ydsz_company}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag, ElInput } from 'element-plus';
import { computed, onMounted, ref } from 'vue';

import {
  createCompanyApi,
  deleteCompanyApi,
  getCompanyListApi,
  updateCompanyApi,
  type CompanyApi,
} from '#/api/company';

import CompanyForm from './company-form.vue';

defineOptions({ name: 'CompanyManagement' });

const loading = ref(false);
const searchText = ref('');
const tableData = ref<CompanyApi.CompanyVO[]>([]);

async function loadData() {
  loading.value = true;
  try {
    tableData.value = await getCompanyListApi();
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);

const filteredData = computed(() => {
  if (!searchText.value) return tableData.value;
  return tableData.value.filter((item) => item.companyName.includes(searchText.value));
});

const [CompanyFormModal, companyFormApi] = useVbenModal({ connectedComponent: CompanyForm });

function handleAdd() {
  companyFormApi.open();
}

function handleEdit(row: CompanyApi.CompanyVO) {
  companyFormApi.setData({ record: row });
  companyFormApi.open();
}

async function handleDelete(row: CompanyApi.CompanyVO) {
  try {
    await ElMessageBox.confirm(`确定删除公司「${row.companyName}」吗？`, '删除确认', { type: 'warning' });
    await deleteCompanyApi(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch {
    // cancelled
  }
}
</script>

<template>
  <Page auto-content-height>
    <div class="p-4">
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-lg font-semibold">公司管理</h3>
        <div class="flex gap-2">
          <ElInput v-model="searchText" placeholder="搜索公司名称" clearable style="width: 200px" />
          <ElButton @click="loadData">刷新</ElButton>
          <ElButton type="primary" @click="handleAdd">新增公司</ElButton>
        </div>
      </div>
      <ElTable v-loading="loading" :data="filteredData" border row-key="id">
        <ElTableColumn type="index" label="序号" width="60" align="center" />
        <ElTableColumn prop="companyName" label="公司名称" min-width="180" />
        <ElTableColumn prop="legalPerson" label="法人" width="100" />
        <ElTableColumn prop="contactPhone" label="联系电话" width="130" />
        <ElTableColumn prop="contactEmail" label="联系邮箱" width="180" />
        <ElTableColumn prop="address" label="地址" min-width="200" />
        <ElTableColumn label="状态" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="160" />
        <ElTableColumn label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" link type="primary" @click="handleEdit(row)">编辑</ElButton>
            <ElButton size="small" link type="danger" @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
    <CompanyFormModal @success="loadData" />
  </Page>
</template>
