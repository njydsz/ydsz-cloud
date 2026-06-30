<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDeptTree, createDept, updateDept, deleteDept } from '@/api/system/dept'
import type { DeptVO, DeptFormDTO } from '@/api/system/dept/types'

const loading = ref(false)
const treeData = ref<DeptVO[]>([])
const currentNode = ref<DeptVO | null>(null)

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
  deptCode: [{ required: true, message: '部门编码必填', trigger: 'blur' }],
  deptName: [{ required: true, message: '部门名称必填', trigger: 'blur' }],
  parentId: [{ required: true, message: '上级部门必填', trigger: 'change' }],
}

async function fetchTree() {
  loading.value = true
  try {
    const { data } = await listDeptTree()
    treeData.value = data || []
  } finally {
    loading.value = false
  }
}

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

async function submitForm() {
  await formRef.value?.validate()
  if (dialogMode.value === 'create') {
    await createDept(form)
    ElMessage.success('创建成功')
  } else {
    await updateDept(form)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  fetchTree()
}

async function handleDelete(node: DeptVO) {
  if (node.children && node.children.length > 0) {
    ElMessage.warning('该部门下存在子部门,请先删除子部门')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除部门「${node.deptName}」吗？`, '提示', { type: 'warning' })
    await deleteDept(node.id)
    ElMessage.success('删除成功')
    fetchTree()
  } catch {
    /* 取消 */
  }
}

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
          新增根部门
        </el-button>
        <el-button :icon="'Refresh'" @click="fetchTree">刷新</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="true"
        border
        default-expand-all
        @row-click="onNodeClick"
      >
        <el-table-column prop="deptName" label="部门名称" min-width="220" />
        <el-table-column prop="deptCode" label="部门编码" width="160" />
        <el-table-column prop="deptPath" label="层级路径" width="200" />
        <el-table-column prop="leaderName" label="负责人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['org:dept:create']" type="primary" link @click.stop="openCreate(row)">
              新增下级
            </el-button>
            <el-button v-permission="['org:dept:update']" type="primary" link @click.stop="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="['org:dept:delete']" type="danger" link @click.stop="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增部门' : '编辑部门'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="上级部门" prop="parentId">
          <el-input-number v-model="form.parentId" :min="0" :max="99999" />
          <span class="form-hint">0 表示根部门</span>
        </el-form-item>
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model="form.deptCode" placeholder="例如: DEV-01" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="例如: 研发部" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
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
