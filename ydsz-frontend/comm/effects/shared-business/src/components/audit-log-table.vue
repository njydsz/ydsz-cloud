<!--
 * audit-log-table 通用组件 — 操作审计日志表格
 *
 * @path comm\effects\shared-business\src\components\audit-log-table.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 统一审计日志表格 — 集成查询/分页/刷新，子应用注入 fetcher 即用
 */
import { onMounted } from 'vue';

import { ElButton, ElDatePicker, ElInput, ElPagination, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { useAuditLog, type AuditLogFetcher, type AuditLogItem } from '../composables/use-audit-log';

interface Props {
  /** 后端查询函数 */
  fetcher: AuditLogFetcher;
  /** 是否展示模块/操作人筛选 */
  showFilters?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showFilters: true,
});

const {
  fetchData,
  filters,
  items,
  loading,
  pagination,
  search,
  changePage,
  changePageSize,
} = useAuditLog(props.fetcher);

/** 操作类型 → tag 颜色 */
function actionType(action: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    CREATE: 'success',
    UPDATE: 'primary',
    DELETE: 'danger',
    EXPORT: 'warning',
    LOGIN: 'info',
  };
  return map[action] || 'info';
}

function handleSearch() {
  search({ operator: filters.operator, module: filters.module });
}

onMounted(() => {
  fetchData();
});
</script>

<template>
  <div class="audit-log-table">
    <div v-if="showFilters" class="audit-log-table__filters">
      <el-input
        v-model="filters.operator"
        placeholder="操作人"
        clearable
        style="width: 160px"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="filters.module"
        placeholder="操作模块"
        clearable
        style="width: 160px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" size="small" @click="handleSearch">查询</el-button>
      <el-button size="small" @click="fetchData">刷新</el-button>
    </div>

    <el-table :data="items" v-loading="loading" size="small" border stripe>
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column prop="module" label="模块" width="140" />
      <el-table-column label="操作类型" width="100">
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" size="small">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="操作内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="createTime" label="操作时间" width="170" />
    </el-table>

    <div class="audit-log-table__pagination">
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50, 100]"
        background
        @current-change="changePage"
        @size-change="changePageSize"
      />
    </div>

    <!-- 插槽：业务自定义扩展列 -->
    <slot name="extra-columns" :items="items as AuditLogItem[]" />
  </div>
</template>

<style scoped>
.audit-log-table__filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.audit-log-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
