<script lang="ts" setup>
/**
 * 岗位（列表页）
 * <p>岗位（{@code ydsz_post}）的列表页。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { Page, useVbenModal } from '@ydsz/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag, ElInput, ElInputNumber } from 'element-plus';
import { onMounted, ref } from 'vue';

import {
  createPostApi,
  deletePostApi,
  getPostListApi,
  updatePostApi,
  type PostApi,
} from '#/api/post';

import PostForm from './post-form.vue';

defineOptions({ name: 'PostManagement' });

const loading = ref(false);
const searchText = ref('');
const tableData = ref<PostApi.PostVO[]>([]);

async function loadData() {
  loading.value = true;
  try {
    tableData.value = await getPostListApi();
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);

const filteredData = computed(() => {
  if (!searchText.value) return tableData.value;
  return tableData.value.filter(
    (item) =>
      item.postName.includes(searchText.value) ||
      item.postCode.includes(searchText.value),
  );
});

import { computed } from 'vue';

// ========== Form ==========
const [PostFormModal, postFormApi] = useVbenModal({ connectedComponent: PostForm });

function handleAdd() {
  postFormApi.open();
}

function handleEdit(row: PostApi.PostVO) {
  postFormApi.setData({ record: row });
  postFormApi.open();
}

async function handleDelete(row: PostApi.PostVO) {
  try {
    await ElMessageBox.confirm(`确定删除岗位「${row.postName}」吗？`, '删除确认', { type: 'warning' });
    await deletePostApi(row.id);
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
        <h3 class="text-lg font-semibold">岗位管理</h3>
        <div class="flex gap-2">
          <ElInput v-model="searchText" placeholder="搜索岗位名称/编码" clearable style="width: 200px" />
          <ElButton @click="loadData">刷新</ElButton>
          <ElButton type="primary" @click="handleAdd">新增岗位</ElButton>
        </div>
      </div>
      <ElTable v-loading="loading" :data="filteredData" border row-key="id">
        <ElTableColumn type="index" label="序号" width="60" align="center" />
        <ElTableColumn prop="postName" label="岗位名称" min-width="150" />
        <ElTableColumn prop="postCode" label="岗位编码" width="150" />
        <ElTableColumn prop="sort" label="排序" width="80" align="center" />
        <ElTableColumn label="状态" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="remark" label="备注" min-width="150" />
        <ElTableColumn prop="createTime" label="创建时间" width="160" />
        <ElTableColumn label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" link type="primary" @click="handleEdit(row)">编辑</ElButton>
            <ElButton size="small" link type="danger" @click="handleDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
    <PostFormModal @success="loadData" />
  </Page>
</template>
