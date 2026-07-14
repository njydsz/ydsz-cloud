<!--
  @fileoverview 表单字段权限配置组件
  @description
    BPMN 用户任务节点的"字段级权限"配置器：按用户 / 角色控制字段的
    EDIT（可编辑）/ READONLY（只读）/ HIDDEN（隐藏）。
    与 FormRenderer 配合，审批页面按权限动态渲染字段状态。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/FormFieldPermissions
  @author ydsz-pmis-team
  @since 1.0.0
-->
<template>
  <div class="form-field-permissions">
    <div class="ffp__header">
      <span class="ffp__title">{{ $t('common.fieldPermissionConfig') }}</span>
      <div class="ffp__actions">
        <el-button size="small" plain @click="addField">
          <el-icon><Plus /></el-icon> {{ $t('common.addField') }}
        </el-button>
        <el-button size="small" type="primary" :loading="saving" @click="save">
          {{ $t('common.save') }}
        </el-button>
      </div>
    </div>

    <el-table
      v-if="fieldList.length > 0"
      :data="fieldList"
      size="small"
      border
      class="ffp__table"
    >
      <el-table-column prop="name" label="字段名" min-width="140">
        <template #default="{ row }">
          <el-input
            v-model="row.name"
            size="small"
            placeholder="如 amount / reason"
          />
        </template>
      </el-table-column>
      <el-table-column label="权限" width="130">
        <template #default="{ row }">
          <el-select v-model="row.permission" size="small">
            <el-option label="可编辑" value="EDIT" />
            <el-option label="只读" value="READONLY" />
            <el-option label="隐藏" value="HIDDEN" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="60" align="center">
        <template #default="{ $index }">
          <el-button
            size="small"
            text
            type="danger"
            @click="removeField($index)"
          >
            {{ $t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-else
      description="暂无字段权限配置，点击'添加字段'开始"
      :image-size="50"
    />

    <div class="ffp__tip">
      {{ $t('common.fieldPermissionTip') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getFormConfig, saveFormConfig } from '@/api/workflow'
import type { FieldPermission } from '@/api/workflow/types'

interface FieldRow {
  name: string
  permission: FieldPermission
}

const props = defineProps<{
  definitionId?: number | null
  nodeCode?: string | null
}>()

const fieldList = ref<FieldRow[]>([])
const saving = ref(false)
const loaded = ref(false)

watch(
  () => [props.definitionId, props.nodeCode],
  ([defId, code]) => {
    if (defId && code) {
      loadConfig(Number(defId), String(code))
    } else {
      fieldList.value = []
      loaded.value = false
    }
  },
  { immediate: true },
)

async function loadConfig(defId: number, code: string) {
  try {
    const res = await getFormConfig(defId, code)
    const data = res.data?.data
    if (data?.fieldPermissions) {
      fieldList.value = Object.entries(data.fieldPermissions).map(
        ([name, permission]) => ({ name, permission }),
      )
    } else {
      fieldList.value = []
    }
    loaded.value = true
  } catch {
    fieldList.value = []
    loaded.value = false
  }
}

function addField() {
  fieldList.value.push({ name: '', permission: 'EDIT' })
}

function removeField(index: number) {
  fieldList.value.splice(index, 1)
}

async function save() {
  if (!props.definitionId || !props.nodeCode) {
    ElMessage.warning('请先选择节点')
    return
  }
  const perms: Record<string, FieldPermission> = {}
  for (const row of fieldList.value) {
    const name = row.name.trim()
    if (!name) {
      ElMessage.warning('字段名不能为空')
      return
    }
    perms[name] = row.permission
  }
  saving.value = true
  try {
    await saveFormConfig(Number(props.definitionId), String(props.nodeCode), {
      fieldPermissions: perms,
    })
    ElMessage.success('字段权限配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-field-permissions {
  width: 100%;
}

.ffp__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ffp__title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.ffp__actions {
  display: flex;
  gap: 6px;
}

.ffp__table {
  margin-bottom: 6px;
}

.ffp__tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.4;
}
</style>
