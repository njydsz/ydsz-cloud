<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listUsers,
  getUser,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
  toggleUserStatus,
  assignUserRoles,
  listUserRoles,
} from '@/api/system/user'
import { listAllRoles } from '@/api/system/role'
import type { UserVO, UserCreateDTO } from '@/api/system/user/types'
import type { RoleVO } from '@/api/system/role/types'

const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
})

const queryForm = ref<InstanceType<any> | null>(null)

// 角色选项
const roleList = ref<RoleVO[]>([])

async function fetchRoles() {
  try {
    const { data } = await listAllRoles()
    roleList.value = data || []
  } catch {
    /* 静默失败 */
  }
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await listUsers(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.page = 1
  fetchList()
}

// 新增/编辑弹窗
const formDialogVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive<UserCreateDTO & { id?: number }>({
  id: undefined,
  username: '',
  realName: '',
  password: '',
  email: '',
  phone: '',
  levelCode: '',
  departmentId: undefined,
  positionId: undefined,
  roleIds: [],
  status: 'ENABLED',
})

const formRules = {
  username: [{ required: true, message: '用户名必填', trigger: 'blur' }],
  realName: [{ required: true, message: '姓名必填', trigger: 'blur' }],
  password: [
    {
      validator: (_: any, v: string, cb: any) => {
        if (formMode.value === 'create' && !v) return cb(new Error('密码必填'))
        if (v && v.length < 6) return cb(new Error('密码长度至少 6 位'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

function openCreate() {
  formMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    username: '',
    realName: '',
    password: '',
    email: '',
    phone: '',
    levelCode: '',
    departmentId: undefined,
    positionId: undefined,
    roleIds: [],
    status: 'ENABLED',
  })
  formDialogVisible.value = true
}

async function openEdit(row: UserVO) {
  formMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    password: '',
    email: row.email ?? '',
    phone: row.phone ?? '',
    levelCode: row.levelCode ?? '',
    departmentId: row.departmentId,
    positionId: row.positionId,
    roleIds: [],
    status: (row as any).status ?? 'ENABLED',
  })
  try {
    const { data } = await listUserRoles(row.id)
    form.roleIds = data || []
  } catch {
    /* 静默 */
  }
  formDialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (formMode.value === 'create') {
    const { data: userId } = await createUser({
      username: form.username,
      realName: form.realName,
      password: form.password,
      email: form.email,
      phone: form.phone,
      levelCode: form.levelCode,
      departmentId: form.departmentId,
      positionId: form.positionId,
      roleIds: form.roleIds,
      status: form.status,
    } as any)
    ElMessage.success('创建成功')
    if (form.roleIds && form.roleIds.length) {
      try {
        await assignUserRoles(userId, form.roleIds)
      } catch {
        /* 角色分配失败不阻断 */
      }
    }
  } else {
    if (form.id) {
      await updateUser(form.id, {
        realName: form.realName,
        email: form.email,
        phone: form.phone,
        levelCode: form.levelCode,
        departmentId: form.departmentId,
        positionId: form.positionId,
        status: form.status,
        roleIds: form.roleIds,
      } as any)
      await assignUserRoles(form.id, form.roleIds ?? [])
      ElMessage.success('更新成功')
    }
  }
  formDialogVisible.value = false
  fetchList()
}

async function handleDelete(row: UserVO) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.realName}」吗？`, '提示', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    /* 取消 */
  }
}

async function handleToggleStatus(row: UserVO) {
  const next = (row as any).status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'ENABLED' ? '启用' : '停用'}用户「${row.realName}」吗？`,
      '提示',
      { type: 'warning' },
    )
    await toggleUserStatus(row.id, next)
    ElMessage.success('状态已更新')
    fetchList()
  } catch {
    /* 取消 */
  }
}

// 重置密码弹窗
const resetDialogVisible = ref(false)
const resetUserId = ref<number | null>(null)
const newPassword = ref('')
async function openResetPwd(row: UserVO) {
  resetUserId.value = row.id
  newPassword.value = ''
  resetDialogVisible.value = true
}
async function submitResetPwd() {
  if (!resetUserId.value) return
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('密码长度至少 6 位')
    return
  }
  await resetPassword(resetUserId.value, newPassword.value)
  ElMessage.success('密码已重置')
  resetDialogVisible.value = false
}

onMounted(() => {
  fetchRoles()
  fetchList()
})
</script>

<template>
  <div class="user-page">
    <el-card shadow="never">
      <el-form inline :model="query" ref="queryForm" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
            <el-option label="锁定" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['auth:user:create']" type="primary" :icon="'Plus'" @click="openCreate">
          新增用户
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchList">刷新</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="username" title="用户名" width="140" />
        <vxe-column field="realName" title="姓名" width="120" />
        <vxe-column field="levelName" title="职级" width="100" />
        <vxe-column field="departmentName" title="部门" min-width="160" />
        <vxe-column field="phone" title="手机号" width="140" />
        <vxe-column field="email" title="邮箱" min-width="180" />
        <vxe-column field="status" title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="(row as any).status === 'ENABLED' ? 'success' : 'info'">
              {{ ({ ENABLED: '启用', DISABLED: '停用', LOCKED: '锁定' } as any)[(row as any).status] || (row as any).status }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['auth:user:update']" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="['auth:user:reset-password']" link type="primary" size="small" @click="openResetPwd(row)">
              重置密码
            </el-button>
            <el-button v-permission="['auth:user:toggle']" link type="primary" size="small" @click="handleToggleStatus(row)">
              {{ (row as any).status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="['auth:user:delete']" link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="formMode === 'create' ? '新增用户' : '编辑用户'"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :disabled="formMode === 'edit'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="form.realName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="formMode === 'create'" :gutter="16">
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" type="password" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="职级">
              <el-input v-model="form.levelCode" placeholder="例如: L8" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门 ID">
              <el-input-number v-model="form.departmentId" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%" placeholder="选择角色">
            <el-option v-for="r in roleList" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetDialogVisible" title="重置密码" width="420px">
      <el-form label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.user-page {
  .search-form { margin-bottom: $spacing-md; }
  .toolbar { margin-bottom: $spacing-md; }
  .pagination { margin-top: $spacing-md; display: flex; justify-content: flex-end; }
}
</style>
