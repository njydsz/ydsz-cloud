<!--
  @file DMN 决策表编辑弹窗
  @description 新建/编辑决策表：基本信息、命中策略、输入/输出列定义、规则表格
  @module views/workflow/dmn/components/DmnEditDialog
-->
<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  saveDmnTable,
  parseColumns,
  parseRules,
  stringifyColumns,
  stringifyRules,
  type FlowDmnTableDTO,
  type DmnColumn,
  type DmnRule,
  type DmnHitPolicy,
  type DmnCollectOperator,
} from '@/api/workflow/dmn'
import { isHandledError } from '@/utils/error'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  /** 传入的决策表数据（null 表示新建） */
  data: FlowDmnTableDTO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'saved'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const isEdit = computed(() => !!props.data?.id)

// ==================== 表单状态 ====================
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  id: undefined as number | undefined,
  tableKey: '',
  tableName: '',
  description: '',
  hitPolicy: 'UNIQUE' as DmnHitPolicy,
  collectOperator: 'LIST' as DmnCollectOperator,
})

const rules = computed<FormRules>(() => ({
  tableKey: [
    { required: true, message: t('workflow.dmn.validate.tableKeyRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: t('workflow.dmn.validate.tableKeyPattern'), trigger: 'blur' },
  ],
  tableName: [{ required: true, message: t('workflow.dmn.validate.tableNameRequired'), trigger: 'blur' }],
  hitPolicy: [{ required: true, message: t('workflow.dmn.validate.hitPolicyRequired'), trigger: 'change' }],
}))

// 命中策略选项
const hitPolicyOptions: Array<{ value: DmnHitPolicy; label: string }> = [
  { value: 'UNIQUE', label: 'UNIQUE' },
  { value: 'FIRST', label: 'FIRST' },
  { value: 'ANY', label: 'ANY' },
  { value: 'PRIORITY', label: 'PRIORITY' },
  { value: 'COLLECT', label: 'COLLECT' },
]

// COLLECT 聚合运算符选项
const collectOperatorOptions: Array<{ value: DmnCollectOperator; label: string }> = [
  { value: 'LIST', label: 'LIST' },
  { value: 'SUM', label: 'SUM' },
  { value: 'MIN', label: 'MIN' },
  { value: 'MAX', label: 'MAX' },
  { value: 'COUNT', label: 'COUNT' },
]

// 列类型选项
const columnTypeOptions = ['string', 'number', 'boolean', 'date']

// ==================== 输入/输出列定义 ====================
const inputColumns = ref<DmnColumn[]>([])
const outputColumns = ref<DmnColumn[]>([])

function addInputColumn() {
  inputColumns.value.push({ id: `in_${Date.now()}`, name: '', type: 'string' })
  // 同步已有规则的输入条件长度
  ruleList.value.forEach((r) => r.inputEntries.push(''))
}

function removeInputColumn(idx: number) {
  inputColumns.value.splice(idx, 1)
  ruleList.value.forEach((r) => r.inputEntries.splice(idx, 1))
}

function addOutputColumn() {
  outputColumns.value.push({ id: `out_${Date.now()}`, name: '', type: 'string' })
  ruleList.value.forEach((r) => r.outputEntries.push(''))
}

function removeOutputColumn(idx: number) {
  outputColumns.value.splice(idx, 1)
  ruleList.value.forEach((r) => r.outputEntries.splice(idx, 1))
}

// ==================== 规则列表 ====================
const ruleList = ref<DmnRule[]>([])

function addRule() {
  ruleList.value.push({
    id: `rule_${Date.now()}`,
    inputEntries: inputColumns.value.map(() => ''),
    outputEntries: outputColumns.value.map(() => ''),
    description: '',
  })
}

function removeRule(idx: number) {
  ruleList.value.splice(idx, 1)
}

// ==================== 弹窗打开时回填数据 ====================
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    if (props.data) {
      form.id = props.data.id
      form.tableKey = props.data.tableKey
      form.tableName = props.data.tableName
      form.description = props.data.description || ''
      form.hitPolicy = props.data.hitPolicy || 'UNIQUE'
      form.collectOperator = props.data.collectOperator || 'LIST'
      inputColumns.value = parseColumns(props.data.inputsJson)
      outputColumns.value = parseColumns(props.data.outputsJson)
      ruleList.value = parseRules(props.data.rulesJson)
    } else {
      form.id = undefined
      form.tableKey = ''
      form.tableName = ''
      form.description = ''
      form.hitPolicy = 'UNIQUE'
      form.collectOperator = 'LIST'
      inputColumns.value = []
      outputColumns.value = []
      ruleList.value = []
    }
  },
)

// ==================== 保存 ====================
async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload: FlowDmnTableDTO = {
      id: form.id,
      tableKey: form.tableKey,
      tableName: form.tableName,
      description: form.description,
      hitPolicy: form.hitPolicy,
      collectOperator: form.hitPolicy === 'COLLECT' ? form.collectOperator : undefined,
      inputsJson: stringifyColumns(inputColumns.value),
      outputsJson: stringifyColumns(outputColumns.value),
      rulesJson: stringifyRules(ruleList.value),
    }
    await saveDmnTable(payload)
    ElMessage.success(isEdit.value ? t('common.success') : t('common.success'))
    visible.value = false
    emit('saved')
  } catch (e) {
    if (!isHandledError(e)) {
      ElMessage.error((e as Error).message)
    }
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? t('workflow.dmn.editTitle') : t('workflow.dmn.createTitle')"
    width="960px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <!-- 基本信息 -->
      <el-divider content-position="left">{{ t('workflow.dmn.section.basic') }}</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="tableKey" prop="tableKey">
            <el-input
              v-model="form.tableKey"
              :placeholder="t('workflow.dmn.placeholder.tableKey')"
              :disabled="isEdit"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('workflow.dmn.tableName')" prop="tableName">
            <el-input v-model="form.tableName" :placeholder="t('workflow.dmn.placeholder.tableName')" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item :label="t('workflow.dmn.description')" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="t('workflow.dmn.placeholder.description')" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item :label="t('workflow.dmn.hitPolicy')" prop="hitPolicy">
            <el-select v-model="form.hitPolicy" style="width: 100%">
              <el-option
                v-for="opt in hitPolicyOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="form.hitPolicy === 'COLLECT'" :span="12">
          <el-form-item :label="t('workflow.dmn.collectOperator')" prop="collectOperator">
            <el-select v-model="form.collectOperator" style="width: 100%">
              <el-option
                v-for="opt in collectOperatorOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 输入列定义 -->
      <el-divider content-position="left">{{ t('workflow.dmn.section.inputs') }}</el-divider>
      <el-table :data="inputColumns" border size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column :label="t('workflow.dmn.column.name')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.name" :placeholder="t('workflow.dmn.placeholder.columnName')" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.dmn.column.type')" width="160">
          <template #default="{ row }">
            <el-select v-model="row.type" size="small" style="width: 100%">
              <el-option v-for="tp in columnTypeOptions" :key="tp" :label="tp" :value="tp" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.dmn.column.expression')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.expression" :placeholder="t('workflow.dmn.placeholder.expression')" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="t('common.delete')" width="80" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeInputColumn($index)">×</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" style="margin-top: 8px" @click="addInputColumn">
        + {{ t('workflow.dmn.addInput') }}
      </el-button>

      <!-- 输出列定义 -->
      <el-divider content-position="left">{{ t('workflow.dmn.section.outputs') }}</el-divider>
      <el-table :data="outputColumns" border size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column :label="t('workflow.dmn.column.name')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.name" :placeholder="t('workflow.dmn.placeholder.columnName')" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.dmn.column.type')" width="160">
          <template #default="{ row }">
            <el-select v-model="row.type" size="small" style="width: 100%">
              <el-option v-for="tp in columnTypeOptions" :key="tp" :label="tp" :value="tp" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.delete')" width="80" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeOutputColumn($index)">×</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" style="margin-top: 8px" @click="addOutputColumn">
        + {{ t('workflow.dmn.addOutput') }}
      </el-button>

      <!-- 规则列表 -->
      <el-divider content-position="left">{{ t('workflow.dmn.section.rules') }}</el-divider>
      <div v-if="inputColumns.length === 0 && outputColumns.length === 0" class="rules-empty">
        {{ t('workflow.dmn.rulesEmptyTip') }}
      </div>
      <el-table v-else :data="ruleList" border size="small">
        <el-table-column type="index" label="#" width="50" align="center" fixed />
        <el-table-column
          v-for="(col, idx) in inputColumns"
          :key="`in_${idx}`"
          :label="`${t('workflow.dmn.inputPrefix')}: ${col.name || idx}`"
          min-width="140"
        >
          <template #default="{ row }">
            <el-input v-model="row.inputEntries[idx]" :placeholder="t('workflow.dmn.placeholder.ruleInput')" size="small" />
          </template>
        </el-table-column>
        <el-table-column
          v-for="(col, idx) in outputColumns"
          :key="`out_${idx}`"
          :label="`${t('workflow.dmn.outputPrefix')}: ${col.name || idx}`"
          min-width="140"
        >
          <template #default="{ row }">
            <el-input v-model="row.outputEntries[idx]" :placeholder="t('workflow.dmn.placeholder.ruleOutput')" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="t('common.delete')" width="80" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeRule($index)">×</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" style="margin-top: 8px" @click="addRule">
        + {{ t('workflow.dmn.addRule') }}
      </el-button>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSave">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.rules-empty {
  padding: 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
  background: #fafafa;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
}
</style>
