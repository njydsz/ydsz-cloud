<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listUsers, deleteUser } from '@/api/system/user'
import type { UserVO } from '@/api/system/user/types'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
const query = ref({
  page: 1,
  size: 10,
  keyword: '',
})

async function fetchList() {
  loading.value = true
  try {
    const { data } = await listUsers(query.value)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function handleDelete(row: UserVO) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.realName}」吗？`, '提示', {
      type: 'warning',
    })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 用户取消
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="user-page">
    <el-card shadow="never">
      <!-- 搜索栏 -->
      <el-form inline :model="query" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button @click="query.keyword = ''; fetchList()">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-button v-permission="['system:user:create']" type="primary" :icon="'Plus'">新增用户</el-button>
        <el-button :icon="'Refresh'" @click="fetchList">刷新</el-button>
      </div>

      <!-- 表格 -->
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="username" title="用户名" />
        <vxe-column field="realName" title="姓名" />
        <vxe-column field="levelName" title="职级" width="100" />
        <vxe-column field="departmentName" title="部门" />
        <vxe-column field="phone" title="手机号" width="140" />
        <vxe-column field="email" title="邮箱" />
        <vxe-column field="status" title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['system:user:update']" link type="primary" size="small">编辑</el-button>
            <el-button v-permission="['system:user:reset-password']" link type="primary" size="small">重置密码</el-button>
            <el-button
              v-permission="['system:user:delete']"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.user-page {
  .search-form {
    margin-bottom: $spacing-md;
  }

  .toolbar {
    margin-bottom: $spacing-md;
  }

  .pagination {
    margin-top: $spacing-md;
    display: flex;
    justify-content: flex-end;
  }
}
</style>

<script lang="ts">
import { ElMessageBox } from 'element-plus'
export default { name: 'SystemUser' }
</script>
