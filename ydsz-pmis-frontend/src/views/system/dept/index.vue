<!--
  @file 部门管理
  @description 部门管理页面：以树形结构展示部门层级，支持新增根部门/下级部门、编辑、删除（存在子部门时不允许删除）。对应路由 /system/dept。
  @module views/system/dept
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDeptTree, createDept, updateDept, deleteDept } from '@/api/system/dept'
import type { DeptVO, DeptFormDTO } from '@/api/system/dept/types'

const { t } = useI18n()

// 树形数据与当前选中节点
const loading = ref(false)
const treeData = ref<DeptVO[]>([])
const currentNode = ref<DeptVO | null>(null)

// 新增/编辑弹窗状态（dialogMode 区分新建与编辑）
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive<DeptFormDTO>({
  id: undefined,
  parentId: 0,
  deptCode: '',
  deptName: '',
  sortOrder: 0,
  leaderId: undefined,
  phone: '',
  email: '',
  status: 'ENABLED',
})

const formRules = {
  deptCode: [{ required: true, message: t('system.dept.rules.deptCodeRequired'), trigger: 'blur' }],
  deptName: [{ required: true, message: t('system.dept.rules.deptNameRequired'), trigger: 'blur' }],
  parentId: [{ required: true, message: t('system.dept.rules.parentIdRequired'), trigger: 'change' }],
}

/** 拉取部门树形数据 */
async function fetchTree() {
  loading.value = true
  try {
    const { data } = await listDeptTree()
    treeData.value = data || []
  } finally {
    loading.value = false
  }
}

/** 打开新增弹窗：parent 为空时新增根部门，否则新增其下级 */
function openCreate(parent: DeptVO | null) {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    parentId: parent?.id ?? 0,
    deptCode: '',
    deptName: '',
    sortOrder: 0,
    leaderId: undefined,
    phone: '',
    email: '',
    status: 'ENABLED',
  })
  dialogVisible.value = true
}

/** 打开编辑弹窗，回填节点数据到表单 */
function openEdit(node: DeptVO) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: node.id,
    parentId: node.parentId,
    deptCode: node.deptCode,
    deptName: node.deptName,
    sortOrder: node.sortOrder ?? 0,
    leaderId: node.leaderId,
    phone: node.phone ?? '',
    email: node.email ?? '',
    status: node.status,
  })
  dialogVisible.value = true
}

/** 提交表单：根据 dialogMode 执行创建或更新，成功后刷新树 */
async function submitForm() {
  await formRef.value?.validate()
  if (dialogMode.value === 'create') {
    await createDept(form)
    ElMessage.success(t('system.dept.messages.createSuccess'))
  } else {
    await updateDept(form)
    ElMessage.success(t('system.dept.messages.updateSuccess'))
  }
  dialogVisible.value = false
  fetchTree()
}

/** 删除部门：存在子部门时阻止删除，否则二次确认后删除 */
async function handleDelete(node: DeptVO) {
  if (node.children && node.children.length > 0) {
    ElMessage.warning(t('system.dept.messages.hasChildren'))
    return
  }
  try {
    await ElMessageBox.confirm(t('system.dept.messages.confirmDelete', { name: node.deptName }), t('common.tip'), { type: 'warning' })
    await deleteDept(node.id)
    ElMessage.success(t('system.dept.messages.deleteSuccess'))
    fetchTree()
  } catch {
    /* 取消 */
  }
}

/** 行点击时记录当前选中节点 */
function onNodeClick(node: DeptVO) {
  currentNode.value = node
}

onMounted(fetchTree)
</script>

<template>
  <div class="dept-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button v-permission="['org:dept:create']" type="primary" :icon="'Plus'" @click="openCreate(null)">
          {{ t('system.dept.buttons.createRoot') }}
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchTree">{{ t('system.dept.buttons.refresh') }}</el-button>
      </div>

      <!-- 部门树形表格 -->
      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="true"
        border
        @row-click="onNodeClick"
      >
        <el-table-column prop="deptName" :label="t('system.dept.columns.deptName')" min-width="220" />
        <el-table-column prop="deptCode" :label="t('system.dept.columns.deptCode')" width="160" />
        <el-table-column prop="deptPath" :label="t('system.dept.columns.deptPath')" width="200" />
        <el-table-column prop="leaderName" :label="t('system.dept.columns.leader')" width="120" />
        <el-table-column prop="phone" :label="t('system.dept.columns.phone')" width="140" />
        <el-table-column prop="sortOrder" :label="t('system.dept.columns.sortOrder')" width="80" align="center" />
        <el-table-column prop="status" :label="t('system.dept.columns.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? t('system.dept.status.ENABLED') : t('system.dept.status.DISABLED') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.dept.columns.action')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['org:dept:create']" type="primary" link @click.stop="openCreate(row as DeptVO)">
              {{ t('system.dept.buttons.createSub') }}
            </el-button>
            <el-button v-permission="['org:dept:update']" type="primary" link @click.stop="openEdit(row as DeptVO)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="['org:dept:delete']" type="danger" link @click.stop="handleDelete(row as DeptVO)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('system.dept.dialog.createTitle') : t('system.dept.dialog.editTitle')"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('system.dept.form.parentId')" prop="parentId">
          <el-input-number v-model="form.parentId" :min="0" :max="99999" />
          <span class="form-hint">{{ t('system.dept.form.parentIdHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('system.dept.form.deptCode')" prop="deptCode">
          <el-input v-model="form.deptCode" :placeholder="t('system.dept.form.deptCodePlaceholder')" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item :label="t('system.dept.form.deptName')" prop="deptName">
          <el-input v-model="form.deptName" :placeholder="t('system.dept.form.deptNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.dept.form.sortOrder')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item :label="t('system.dept.form.phone')">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item :label="t('system.dept.form.email')">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="t('system.dept.form.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">{{ t('system.dept.status.ENABLED') }}</el-radio>
            <el-radio value="DISABLED">{{ t('system.dept.status.DISABLED') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.dept-page {
  .toolbar { margin-bottom: $spacing-md; }
  .form-hint { margin-left: 8px; color: $text-secondary; font-size: 12px; }
}
</style>
