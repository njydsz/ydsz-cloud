<!--
  @fileoverview 运行时表单渲染组件
  @description
    P1-1：基于 form-create 渲染动态表单，根据后端返回的字段权限
    （EDIT / READONLY / HIDDEN）控制每个字段的编辑 / 只读 / 隐藏状态。
    流程实例详情页审批区域使用：
      - 接收 instanceId 和 formSchema 作为 props；
      - 调用 getFormRenderData 获取字段权限；
      - 暴露 getFormData / validate 方法供父组件调用。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/FormRenderer
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 运行时表单渲染组件
 * @module components/FormRenderer
 * @description P1-1: 基于 form-create 渲染动态表单，根据后端返回的字段权限（EDIT/READONLY/HIDDEN）
 *   控制每个字段的编辑/只读/隐藏状态。供流程实例详情页审批区域使用。
 *
 *   - 接收 instanceId 和 formSchema 作为 props
 *   - 调用 getFormRenderData 获取字段权限
 *   - 基于 form-create 渲染动态表单
 *   - 暴露 getFormData / validate 方法供父组件调用
 */
import { ref, watch, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFormRenderData } from '@/api/workflow'
import type { FieldPermission } from '@/api/workflow/types'
import type { Rule, Options } from '@form-create/element-ui'

// ==================== Props ====================
const props = defineProps<{
  /** 流程实例 ID（用于获取字段权限） */
  instanceId?: string
  /** 表单 schema（form-create rule JSON，字符串或对象） */
  formSchema?: Record<string, unknown> | string
  /** 是否只读模式（查看详情时整体只读） */
  readonly?: boolean
}>()

// ==================== Emits ====================
const emit = defineEmits<{
  /** 后端返回 formSchema 时触发（父组件未传 formSchema 的场景） */
  (e: 'schema-loaded', schema: string): void
}>()

// ==================== State ====================
/** form-create 渲染器 API 实例（formData/validate/resetFields 等） */
interface FormCreateApi {
  formData: () => Record<string, unknown>
  validate: () => Promise<void>
  resetFields: () => void
}
const fApi = ref<FormCreateApi | null>(null)
const loading = ref(false)
const fieldPermissions = ref<Record<string, FieldPermission>>({})
const formData = ref<Record<string, unknown>>({})
/** 后端返回的 formSchema（父组件未传 formSchema 时使用） */
const backendSchema = ref<string>('')

/**
 * 实际生效的 schema：优先使用 props.formSchema，否则回退到后端返回的 backendSchema。
 * 解决父组件未传 formSchema 但后端返回了 schema 时不渲染表单的问题。
 */
const effectiveSchema = computed(() => props.formSchema || backendSchema.value)

// ==================== 计算渲染规则 ====================
/** 解析原始 rule 数组 */
const parsedRules = computed<Rule[]>(() => {
  const schemaSrc = effectiveSchema.value
  if (!schemaSrc) return []
  try {
    let schema: unknown = schemaSrc
    if (typeof schema === 'string') {
      const parsed = JSON.parse(schema) as { rule?: unknown } | unknown[]
      // 兼容 { rule: [...], options: {...} } 或直接 [...]
      schema = Array.isArray(parsed) ? parsed : (parsed.rule ?? parsed)
    }
    if (!Array.isArray(schema)) return []
    return schema as Rule[]
  } catch {
    return []
  }
})

/** 解析 form-create 配置选项 */
const parsedOptions = computed<Options>(() => {
  const schemaSrc = effectiveSchema.value
  if (!schemaSrc || typeof schemaSrc !== 'string') {
    return { submitBtn: false, resetBtn: false } as Options
  }
  try {
    const parsed = JSON.parse(schemaSrc)
    if (parsed.options) {
      return { ...parsed.options, submitBtn: false, resetBtn: false }
    }
  } catch {
    // ignore
  }
  return { submitBtn: false, resetBtn: false } as Options
})

/** 根据字段权限处理后的渲染规则 */
const renderRules = computed<Rule[]>(() => {
  const perms = fieldPermissions.value
  const readonly = props.readonly

  return parsedRules.value
    .map((rule: Rule) => {
      // 深拷贝避免污染原始数据
      const newRule = JSON.parse(JSON.stringify(rule)) as Rule & {
        hidden?: boolean
        props?: { disabled?: boolean }
      }
      const ruleRecord = newRule as unknown as { field?: string; name?: string }
      const field = ruleRecord.field || ruleRecord.name

      if (field && perms[field]) {
        const perm = perms[field]
        if (perm === 'HIDDEN') {
          // HIDDEN: 隐藏字段
          newRule.hidden = true
        } else if (perm === 'READONLY' || readonly) {
          // READONLY 或全局只读: 禁用字段
          if (!newRule.props) newRule.props = {}
          newRule.props.disabled = true
        }
        // EDIT: 正常可编辑，无需额外处理
      } else if (readonly) {
        // 无明确权限但全局只读
        if (!newRule.props) newRule.props = {}
        newRule.props.disabled = true
      }

      return newRule
    })
    .filter((rule: Rule & { hidden?: boolean }) => !rule.hidden) as Rule[]
})

/** 是否有可渲染的表单 */
const hasForm = computed(() => renderRules.value.length > 0)

// ==================== 生命周期 ====================
onMounted(() => {
  if (props.instanceId) {
    loadFieldPermissions()
  }
})

watch(() => props.instanceId, (newVal) => {
  if (newVal) {
    loadFieldPermissions()
  }
})

// ==================== 方法 ====================

/** 加载字段权限 */
async function loadFieldPermissions() {
  if (!props.instanceId) return
  loading.value = true
  try {
    const res = await getFormRenderData(props.instanceId)
    if (res.data?.code === 0 && res.data?.data) {
      fieldPermissions.value = res.data.data.fieldPermissions || {}
      // 父组件未传 formSchema 但后端返回了：回填 backendSchema 并通知父组件
      if (!props.formSchema && res.data.data.formSchema) {
        const schema = res.data.data.formSchema as string
        backendSchema.value = schema
        emit('schema-loaded', schema)
      }
    }
  } catch (e) {
    ElMessage.warning('加载字段权限失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

/** 获取表单数据（供父组件调用） */
function getFormData(): Record<string, unknown> {
  if (fApi.value) {
    return fApi.value.formData() || formData.value
  }
  return { ...formData.value }
}

/** 表单校验（供父组件调用） */
async function validate(): Promise<boolean> {
  if (!fApi.value) return true
  try {
    await fApi.value.validate()
    return true
  } catch {
    ElMessage.warning('请完善表单必填项')
    return false
  }
}

/** 重置表单 */
function resetForm() {
  if (fApi.value) {
    fApi.value.resetFields()
  }
}

// ==================== 暴露方法 ====================
defineExpose({
  getFormData,
  validate,
  resetForm,
  loading,
  hasForm,
})
</script>

<template>
  <div class="form-renderer" v-loading="loading">
    <div v-if="hasForm" class="form-renderer-body">
      <FormCreate
        v-model="formData"
        :rule="renderRules"
        :option="parsedOptions"
        @api="(api: FormCreateApi) => (fApi = api)"
      />
    </div>
    <el-empty v-else description="暂无表单数据" :image-size="60" />
  </div>
</template>

<style scoped>
.form-renderer {
  width: 100%;
}

.form-renderer-body {
  padding: 8px 0;
}
</style>
