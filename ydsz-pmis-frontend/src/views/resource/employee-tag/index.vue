<!--
  @file 员工标签管理
  @description 员工标签管理页面：提供「按员工查询标签」与「按标签筛选候选人」两个 Tab，支持为员工新增/删除标签（技术栈/技术方向/行业经验/可用时间）。对应路由 /resource/employee-tag，后端服务 ydsz-pmis-userinfo（端口 9002）。
  @module views/resource/employee-tag
-->
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  addEmployeeTag,
  removeEmployeeTag,
  listEmployeeTags,
  findCandidates,
} from '@/api/resource/employee-tag'
import type { EmployeeTagVO, EmployeeTagCreateDTO } from '@/api/resource/employee-tag/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

const tab = ref<'byEmployee' | 'candidates'>('byEmployee')
const employeeId = ref<number | null>(null)
const tags = ref<EmployeeTagVO[]>([])
const tagsLoading = ref(false)

const candidateForm = reactive({
  tagType: 'SKILL',
  tagCode: '',
})
const candidates = ref<EmployeeTagVO[]>([])
const candidatesLoading = ref(false)
const submitting = ref(false)
const deleting = ref(false)

const tagTypeMap = computed<Record<string, string>>(() => ({
  SKILL: t('resource.employeeTag.tagType.SKILL'),
  TECH: t('resource.employeeTag.tagType.TECH'),
  INDUSTRY: t('resource.employeeTag.tagType.INDUSTRY'),
  AVAILABILITY: t('resource.employeeTag.tagType.AVAILABILITY'),
}))

const dialogVisible = ref(false)
const form = reactive<EmployeeTagCreateDTO>({
  employeeId: 0,
  tagType: 'SKILL',
  tagCode: '',
  tagName: '',
  weight: 1,
  description: '',
})

const formRules = computed(() => ({
  employeeId: [{ required: true, message: t('resource.employeeTag.rules.employeeIdRequired'), trigger: 'blur' }],
  tagType: [{ required: true, message: t('resource.employeeTag.rules.typeRequired'), trigger: 'change' }],
  tagCode: [{ required: true, message: t('resource.employeeTag.rules.codeRequired'), trigger: 'blur' }],
  tagName: [{ required: true, message: t('resource.employeeTag.rules.nameRequired'), trigger: 'blur' }],
}))

/** 拉取指定员工的标签列表 */
async function fetchTags() {
  if (!employeeId.value) return
  tagsLoading.value = true
  try {
    const { data } = await listEmployeeTags(employeeId.value)
    tags.value = data || []
  } catch {
    tags.value = []
  } finally {
    tagsLoading.value = false
  }
}

/** 按标签类型/编码筛选具备该标签的候选员工 */
async function fetchCandidates() {
  if (!candidateForm.tagType) return
  candidatesLoading.value = true
  try {
    const { data } = await findCandidates(candidateForm.tagType, candidateForm.tagCode || undefined)
    candidates.value = data || []
  } catch {
    candidates.value = []
  } finally {
    candidatesLoading.value = false
  }
}

/** 打开新增标签弹窗，初始化表单默认值 */
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

/** 提交新增标签，成功后关闭弹窗并刷新员工标签列表 */
async function submitForm() {
  submitting.value = true
  try {
    await addEmployeeTag(form)
    ElMessage.success(t('resource.employeeTag.messages.added'))
    dialogVisible.value = false
    fetchTags()
  } finally {
    submitting.value = false
  }
}

/**
 * 删除员工标签，二次确认后执行
 * @param row 待删除的标签行数据
 */
async function handleDelete(row: EmployeeTagVO) {
  try {
    await ElMessageBox.confirm(t('resource.employeeTag.messages.deletePrompt', { name: row.tagName }), t('common.tip'), { type: 'warning' })
    deleting.value = true
    try {
      await removeEmployeeTag(row.id)
      ElMessage.success(t('resource.employeeTag.messages.deleted'))
      fetchTags()
    } finally {
      deleting.value = false
    }
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
        <el-tab-pane :label="t('resource.employeeTag.tabs.byEmployee')" name="byEmployee">
          <div class="search-row">
            <el-input-number v-model="employeeId" :min="1" :placeholder="t('resource.employeeTag.search.employeeIdPlaceholder')" />
            <el-button type="primary" @click="fetchTags">{{ t('resource.employeeTag.buttons.query') }}</el-button>
            <el-button v-permission="[PC.RESOURCE_TAG_CREATE]" type="primary" :icon="'Plus'" @click="openCreate">{{ t('resource.employeeTag.buttons.create') }}</el-button>
          </div>
          <vxe-table :data="tags" :loading="tagsLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="employeeId" :title="t('resource.employeeTag.columns.employeeId')" width="100" />
            <vxe-column field="tagType" :title="t('resource.employeeTag.columns.type')" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ tagTypeMap[row.tagType] || row.tagType }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="tagCode" :title="t('resource.employeeTag.columns.code')" width="160" />
            <vxe-column field="tagName" :title="t('resource.employeeTag.columns.name')" min-width="160" />
            <vxe-column field="weight" :title="t('resource.employeeTag.columns.weight')" width="80" align="center" />
            <vxe-column field="description" :title="t('resource.employeeTag.columns.description')" min-width="200" />
            <vxe-column :title="t('resource.employeeTag.columns.action')" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="[PC.RESOURCE_TAG_DELETE]" link type="danger" size="small" :loading="deleting" @click="handleDelete(row)">{{ t('resource.employeeTag.buttons.delete') }}</el-button>
              </template>
            </vxe-column>
          </vxe-table>
        </el-tab-pane>

        <!-- 按标签筛选候选人 -->
        <el-tab-pane :label="t('resource.employeeTag.tabs.candidates')" name="candidates">
          <div class="search-row">
            <el-select v-model="candidateForm.tagType" style="width: 160px" @change="fetchCandidates">
              <el-option v-for="(label, val) in tagTypeMap" :key="val" :label="label" :value="val" />
            </el-select>
            <el-input v-model="candidateForm.tagCode" :placeholder="t('resource.employeeTag.search.tagCodePlaceholder')" clearable style="width: 220px" />
            <el-button type="primary" @click="fetchCandidates">{{ t('resource.employeeTag.buttons.query') }}</el-button>
          </div>
          <vxe-table :data="candidates" :loading="candidatesLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="employeeId" :title="t('resource.employeeTag.columns.employeeId')" width="100" />
            <vxe-column field="tagType" :title="t('resource.employeeTag.columns.type')" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ tagTypeMap[row.tagType] || row.tagType }}</el-tag>
              </template>
            </vxe-column>
            <vxe-column field="tagCode" :title="t('resource.employeeTag.columns.code')" width="160" />
            <vxe-column field="tagName" :title="t('resource.employeeTag.columns.name')" min-width="160" />
            <vxe-column field="weight" :title="t('resource.employeeTag.columns.weight')" width="80" align="center" />
            <vxe-column field="description" :title="t('resource.employeeTag.columns.description')" min-width="200" />
          </vxe-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t('resource.employeeTag.dialog.createTitle')" width="480px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('resource.employeeTag.form.employeeId')" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('resource.employeeTag.form.type')" prop="tagType">
          <el-select v-model="form.tagType" style="width: 100%">
            <el-option v-for="(label, val) in tagTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('resource.employeeTag.form.code')" prop="tagCode">
          <el-input v-model="form.tagCode" :placeholder="t('resource.employeeTag.form.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('resource.employeeTag.form.name')" prop="tagName">
          <el-input v-model="form.tagName" :placeholder="t('resource.employeeTag.form.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('resource.employeeTag.form.weight')">
          <el-input-number v-model="form.weight" :min="0" :max="10" :step="0.5" />
        </el-form-item>
        <el-form-item :label="t('resource.employeeTag.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.ok') }}</el-button>
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
