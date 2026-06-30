<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addEmployeeTag,
  removeEmployeeTag,
  listEmployeeTags,
  findCandidates,
} from '@/api/resource/employee-tag'
import type { EmployeeTagVO, EmployeeTagCreateDTO } from '@/api/resource/employee-tag/types'
import { PC } from '@/constants/permissionCodes'

const tab = ref<'byEmployee' | 'candidates'>('byEmployee')
const employeeId = ref<number | null>(null)
const tags = ref<EmployeeTagVO[]>([])

const candidateForm = reactive({
  tagType: 'SKILL',
  tagCode: '',
})
const candidates = ref<EmployeeTagVO[]>([])

const tagTypeMap: Record<string, string> = {
  SKILL: '技术栈',
  TECH: '技术方向',
  INDUSTRY: '行业经验',
  AVAILABILITY: '可用时间',
}

const dialogVisible = ref(false)
const form = reactive<EmployeeTagCreateDTO>({
  employeeId: 0,
  tagType: 'SKILL',
  tagCode: '',
  tagName: '',
  weight: 1,
  description: '',
})

const formRules = {
  employeeId: [{ required: true, message: '员工 ID 必填', trigger: 'blur' }],
  tagType: [{ required: true, message: '标签类型必填', trigger: 'change' }],
  tagCode: [{ required: true, message: '标签编码必填', trigger: 'blur' }],
  tagName: [{ required: true, message: '标签名称必填', trigger: 'blur' }],
}

async function fetchTags() {
  if (!employeeId.value) return
  try {
    const { data } = await listEmployeeTags(employeeId.value)
    tags.value = data || []
  } catch {
    tags.value = []
  }
}

async function fetchCandidates() {
  if (!candidateForm.tagType) return
  try {
    const { data } = await findCandidates(candidateForm.tagType, candidateForm.tagCode || undefined)
    candidates.value = data || []
  } catch {
    candidates.value = []
  }
}

function openCreate() {
  Object.assign(form, {
    employeeId: employeeId.value ?? 0,
    tagType: 'SKILL',
    tagCode: '',
    tagName: '',
    weight: 1,
    description: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await addEmployeeTag(form)
  ElMessage.success('添加成功')
  dialogVisible.value = false
  fetchTags()
}

async function handleDelete(row: EmployeeTagVO) {
  try {
    await ElMessageBox.confirm(`确认删除标签「${row.tagName}」?`, '提示', { type: 'warning' })
    await removeEmployeeTag(row.id)
    ElMessage.success('已删除')
    fetchTags()
  } catch {
    /* 取消 */
  }
}

onMounted(() => {
  fetchTags()
  fetchCandidates()
})
</script>

<template>
  <div class="tag-page">
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="按员工查询" name="byEmployee">
          <div class="search-row">
            <el-input-number v-model="employeeId" :min="1" placeholder="员工 ID" />
            <el-button type="primary" @click="fetchTags">查询</el-button>
            <el-button v-permission="[PC.RESOURCE_TAG_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">新增标签</el-button>
          </div>
          <vxe-table :data="tags" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="employeeId" title="员工 ID" width="100" />
            <vxe-column field="tagType" title="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ tagTypeMap[row.tagType] || row.tagType }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="tagCode" title="标签编码" width="160" />
            <vxe-column field="tagName" title="标签名称" min-width="160" />
            <vxe-column field="weight" title="权重" width="80" align="center" />
            <vxe-column field="description" title="描述" min-width="200" />
            <vxe-column title="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="[PC.RESOURCE_TAG_DELETE]" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </vxe-column>
          </vxe-table>
        </el-tab-pane>

        <el-tab-pane label="按标签筛选候选人" name="candidates">
          <div class="search-row">
            <el-select v-model="candidateForm.tagType" style="width: 160px" @change="fetchCandidates">
              <el-option v-for="(label, val) in tagTypeMap" :key="val" :label="label" :value="val" />
            </el-select>
            <el-input v-model="candidateForm.tagCode" placeholder="标签编码 (可选)" clearable style="width: 220px" />
            <el-button type="primary" @click="fetchCandidates">查询</el-button>
          </div>
          <vxe-table :data="candidates" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="employeeId" title="员工 ID" width="100" />
            <vxe-column field="tagType" title="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ tagTypeMap[row.tagType] || row.tagType }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="tagCode" title="标签编码" width="160" />
            <vxe-column field="tagName" title="标签名称" min-width="160" />
            <vxe-column field="weight" title="权重" width="80" align="center" />
            <vxe-column field="description" title="描述" min-width="200" />
          </vxe-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增人员标签" width="480px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="员工 ID" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" />
        </el-form-item>
        <el-form-item label="标签类型" prop="tagType">
          <el-select v-model="form.tagType" style="width: 100%">
            <el-option v-for="(label, val) in tagTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签编码" prop="tagCode">
          <el-input v-model="form.tagCode" placeholder="例如: JAVA" />
        </el-form-item>
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="form.tagName" placeholder="例如: Java 开发" />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="0" :max="10" :step="0.5" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
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
.tag-page {
  .search-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: $spacing-md;
  }
}
</style>
