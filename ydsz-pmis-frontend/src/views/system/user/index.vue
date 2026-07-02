<!--
  @file 用户管理
  @description 用户管理页面：提供用户分页查询、新增/编辑/删除、重置密码、启停状态切换及角色分配；删除与重置密码等敏感操作需二次认证（密码/TOTP/备用码）。对应路由 /system/user。
  @module views/system/user
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listUsers,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
  toggleUserStatus,
  assignUserRoles,
  listUserRoles,
} from '@/api/system/user'
import { listAllRoles } from '@/api/system/role'
import { get2faStatus } from '@/api/user/two-factor'
import ReAuthDialog from '@/components/common/ReAuthDialog.vue'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'
import { useReAuth } from '@/composables/useReAuth'
import { isHandledError } from '@/utils/error'
import type { ReAuthMethod } from '@/api/user/reauth'
import type { UserVO, UserCreateDTO } from '@/api/system/user/types'
import type { RoleVO } from '@/api/system/role/types'

const { t } = useI18n()

const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
// 用户分页查询条件
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
})

const queryForm = ref<InstanceType<any> | null>(null)

// 角色选项
const roleList = ref<RoleVO[]>([])

/** 拉取全部角色（用于角色下拉选项） */
async function fetchRoles() {
  try {
    const { data } = await listAllRoles()
    roleList.value = data || []
  } catch (e) {
    roleList.value = []
    if (!isHandledError(e)) {
      ElMessage.error(t('system.user.messages.roleLoadFailed'))
    }
  }
}

/** 拉取用户分页列表 */
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

/** 重置查询条件并刷新列表 */
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

/** 打开新增用户弹窗，初始化表单默认值 */
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

/**
 * 打开编辑弹窗：回填用户基础信息并拉取已分配角色
 * @param row 待编辑的用户行数据
 */
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
  } catch (e) {
    form.roleIds = []
    if (!isHandledError(e)) {
      ElMessage.error('用户角色加载失败，请刷新重试')
    }
  }
  formDialogVisible.value = true
}

/** 提交表单：根据 formMode 执行创建或更新，并在创建/更新后分配角色 */
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
    ElMessage.success(t('system.user.messages.createSuccess'))
    if (form.roleIds && form.roleIds.length) {
      try {
        await assignUserRoles(userId, form.roleIds)
      } catch (e) {
        /* 角色分配失败不阻断主流程，但需提示用户 */
        if (!isHandledError(e)) {
          ElMessage.warning(t('system.user.messages.roleAssignFailed'))
        }
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
      ElMessage.success(t('system.user.messages.updateSuccess'))
    }
  }
  formDialogVisible.value = false
  fetchList()
}

/**
 * 删除用户（敏感操作，需二次认证）
 * @param row 待删除的用户行数据
 */
async function handleDelete(row: UserVO) {
  try {
    await ElMessageBox.confirm(t('system.user.messages.confirmDelete', { name: row.realName }), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  await deleteReAuth.withReAuth(async (token) => {
    await deleteUser(row.id, token)
    ElMessage.success(t('system.user.messages.deleteSuccess'))
    await fetchList()
  })
}

/**
 * 切换用户启停状态，二次确认后执行
 * @param row 待切换状态的用户行数据
 */
async function handleToggleStatus(row: UserVO) {
  const next = (row as any).status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await ElMessageBox.confirm(
      t('system.user.messages.confirmToggle', { action: next === 'ENABLED' ? t('system.user.buttons.enable') : t('system.user.buttons.disable'), name: row.realName }),
      t('common.confirm'),
      { type: 'warning' },
    )
    await toggleUserStatus(row.id, next)
    ElMessage.success(t('system.user.messages.statusUpdated'))
    fetchList()
  } catch {
    /* 取消 */
  }
}

// 重置密码弹窗
const resetDialogVisible = ref(false)
const resetUserId = ref<number | null>(null)
const newPassword = ref('')
/**
 * 打开重置密码弹窗
 * @param row 待重置密码的用户行数据
 */
async function openResetPwd(row: UserVO) {
  resetUserId.value = row.id
  newPassword.value = ''
  resetDialogVisible.value = true
}
/** 提交重置密码（敏感操作，需二次认证） */
async function submitResetPwd() {
  if (!resetUserId.value) return
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning(t('system.user.messages.passwordMinLength'))
    return
  }
  const id = resetUserId.value
  const pwd = newPassword.value
  await resetPwdReAuth.withReAuth(async (token) => {
    await resetPassword(id, pwd, token)
    ElMessage.success(t('system.user.messages.passwordReset'))
    resetDialogVisible.value = false
  })
}

// ============= 二次认证 =============
const has2fa = ref(false)
/** 拉取当前管理员是否启用 2FA（用于决定二次认证可用方式） */
async function fetch2faStatus() {
  try {
    const { data } = await get2faStatus()
    has2fa.value = data?.enabled || false
  } catch (e) {
    has2fa.value = false
    if (!isHandledError(e)) {
      ElMessage.error(t('system.user.messages.twoFaLoadFailed'))
    }
  }
}

const deleteReAuth = useReAuth({
  operationCode: 'USER_DELETE',
  operationName: '删除用户',
})
const resetPwdReAuth = useReAuth({
  operationCode: 'USER_RESET_PASSWORD',
  operationName: '重置用户密码',
})

/**
 * 二次认证确认回调：将所选方式与凭证写入 reauth 对话框状态并触发确认
 * @param reauth useReAuth 实例
 * @param payload 认证方式与凭证
 */
function onReAuthConfirm(
  reauth: ReturnType<typeof useReAuth>,
  payload: { method: ReAuthMethod; password?: string; otp?: string; backupCode?: string },
) {
  reauth.dialog.method = payload.method
  if (payload.method === 'PASSWORD') reauth.dialog.password = payload.password || ''
  else if (payload.method === 'TOTP') reauth.dialog.otp = payload.otp || ''
  else if (payload.method === 'BACKUP_CODE') reauth.dialog.backupCode = payload.backupCode || ''
  reauth.handleConfirm()
}

/**
 * 二次认证取消回调
 * @param reauth useReAuth 实例
 */
function onReAuthCancel(reauth: ReturnType<typeof useReAuth>) {
  reauth.handleCancel()
}

onMounted(() => {
  fetchRoles()
  fetchList()
  fetch2faStatus()
})
</script>

<template>
  <div class="user-page">
    <el-card shadow="never">
      <el-form ref="queryForm" inline :model="query" class="search-form">
        <el-form-item :label="$t('system.user.search.keyword')">
          <el-input v-model="query.keyword" :placeholder="$t('system.user.search.keywordPlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="$t('system.user.search.status')">
          <el-select v-model="query.status" :placeholder="$t('common.all')" clearable style="width: 140px">
            <el-option :label="$t('system.user.status.ENABLED')" value="ENABLED" />
            <el-option :label="$t('system.user.status.DISABLED')" value="DISABLED" />
            <el-option :label="$t('system.user.status.LOCKED')" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['auth:user:create']" type="primary" :icon="'Plus'" @click="openCreate">
          {{ $t('system.user.buttons.create') }}
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchList">{{ $t('common.refresh') }}</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="username" :title="$t('system.user.columns.username')" width="140" />
        <vxe-column field="realName" :title="$t('system.user.columns.realName')" width="120" />
        <vxe-column field="levelName" :title="$t('system.user.columns.levelName')" width="100" />
        <vxe-column field="departmentName" :title="$t('system.user.columns.departmentName')" min-width="160" />
        <vxe-column field="phone" :title="$t('system.user.columns.phone')" width="140" />
        <vxe-column field="email" :title="$t('system.user.columns.email')" min-width="180" />
        <vxe-column field="status" :title="$t('system.user.columns.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="(row as any).status === 'ENABLED' ? 'success' : 'info'">
              {{ $t(`system.user.status.${(row as any).status}`) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column :title="$t('system.user.columns.action')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['auth:user:update']" link type="primary" size="small" @click="openEdit(row)">
              {{ $t('system.user.buttons.edit') }}
            </el-button>
            <el-button v-permission="['auth:user:reset-password']" link type="primary" size="small" @click="openResetPwd(row)">
              {{ $t('system.user.buttons.resetPassword') }}
            </el-button>
            <el-button v-permission="['auth:user:toggle']" link type="primary" size="small" @click="handleToggleStatus(row)">
              {{ (row as any).status === 'ENABLED' ? $t('system.user.buttons.disable') : $t('system.user.buttons.enable') }}
            </el-button>
            <el-button v-permission="['auth:user:delete']" link type="danger" size="small" @click="handleDelete(row)">
              {{ $t('common.delete') }}
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
      :title="formMode === 'create' ? $t('system.user.dialog.createTitle') : $t('system.user.dialog.editTitle')"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item :label="$t('system.user.form.username')" prop="username">
              <el-input v-model="form.username" :disabled="formMode === 'edit'" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item :label="$t('system.user.form.realName')" prop="realName">
              <el-input v-model="form.realName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="formMode === 'create'" :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item :label="$t('system.user.form.password')" prop="password">
              <el-input v-model="form.password" type="password" show-password />
              <PasswordStrengthBar
                :password="form.password || ''"
                :show-input="false"
                :show-rules="true"
                style="margin-top: 6px"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item :label="$t('system.user.form.phone')">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item :label="$t('system.user.form.email')">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item :label="$t('system.user.form.levelCode')">
              <el-input v-model="form.levelCode" placeholder="例如: L8" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item :label="$t('system.user.form.departmentId')">
              <el-input-number v-model="form.departmentId" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('system.user.form.role')">
          <el-select v-model="form.roleIds" multiple style="width: 100%" :placeholder="$t('system.user.form.role')">
            <el-option v-for="r in roleList" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.user.form.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">{{ $t('system.user.status.ENABLED') }}</el-radio>
            <el-radio value="DISABLED">{{ $t('system.user.status.DISABLED') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetDialogVisible" :title="$t('system.user.dialog.resetPwdTitle')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$t('system.user.form.password')">
          <el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位" />
          <PasswordStrengthBar
            :password="newPassword"
            :show-input="false"
            :show-rules="true"
            style="margin-top: 6px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitResetPwd">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 二次认证弹窗：删除用户 -->
    <ReAuthDialog
      v-model:visible="deleteReAuth.dialog.visible"
      v-model:method="deleteReAuth.dialog.method"
      :operation-code="deleteReAuth.options.operationCode"
      :operation-name="deleteReAuth.options.operationName"
      :loading="deleteReAuth.dialog.loading"
      :error-message="deleteReAuth.dialog.errorMessage"
      :has-2fa="has2fa"
      @confirm="(p) => onReAuthConfirm(deleteReAuth, p)"
      @cancel="onReAuthCancel(deleteReAuth)"
    />

    <!-- 二次认证弹窗：重置密码 -->
    <ReAuthDialog
      v-model:visible="resetPwdReAuth.dialog.visible"
      v-model:method="resetPwdReAuth.dialog.method"
      :operation-code="resetPwdReAuth.options.operationCode"
      :operation-name="resetPwdReAuth.options.operationName"
      :loading="resetPwdReAuth.dialog.loading"
      :error-message="resetPwdReAuth.dialog.errorMessage"
      :has-2fa="has2fa"
      @confirm="(p) => onReAuthConfirm(resetPwdReAuth, p)"
      @cancel="onReAuthCancel(resetPwdReAuth)"
    />
  </div>
</template>

<style lang="scss" scoped>
.user-page {
  .search-form { margin-bottom: $spacing-md; }
  .toolbar { margin-bottom: $spacing-md; }
  .pagination { margin-top: $spacing-md; display: flex; justify-content: flex-end; }
}
</style>
