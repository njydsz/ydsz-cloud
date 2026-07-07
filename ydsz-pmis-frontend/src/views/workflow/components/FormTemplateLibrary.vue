<!--
  @fileoverview 表单模板库弹窗组件
  @description
    P2-7：提供分类筛选 + 卡片网格 + 一键导入的表单模板选择体验。
    对标流程模板库（design/index.vue）的 UI 模式。
    数据来源：../form-design/templates.ts 中的 FORM_TEMPLATES 预置模板。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/FormTemplateLibrary
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * Props:
 *  - visible: 是否显示（v-model）
 *
 * Events:
 *  - update:visible: 关闭弹窗
 *  - select: 选中模板，返回 { rule, options }
 */
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { filterTemplatesByCategory, TEMPLATE_CATEGORIES, type FormTemplate } from '../form-design/templates'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  select: [template: { rule: Record<string, unknown>[]; options: Record<string, unknown> }]
}>()

const activeCategory = ref<string>('ALL')

const filteredTemplates = computed<FormTemplate[]>(() => {
  return filterTemplatesByCategory(activeCategory.value)
})

/** 选中模板 */
async function handleSelectTemplate(tpl: FormTemplate) {
  try {
    await ElMessageBox.confirm(
      `确定要导入模板"${tpl.name}"吗？当前设计器中的表单将被替换。`,
      '导入模板',
      { confirmButtonText: '确定导入', cancelButtonText: '取消', type: 'warning' },
    )
    emit('select', { rule: tpl.rule, options: tpl.options })
    emit('update:visible', false)
    ElMessage.success(`已导入模板：${tpl.name}`)
  } catch {
    // 用户取消
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="表单模板库"
    width="800px"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <div class="template-library">
      <!-- 分类筛选 -->
      <div class="template-filter">
        <el-radio-group v-model="activeCategory" size="small">
          <el-radio-button
            v-for="cat in TEMPLATE_CATEGORIES"
            :key="cat.value"
            :value="cat.value"
          >
            {{ cat.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- 模板卡片网格 -->
      <div class="template-grid">
        <div
          v-for="tpl in filteredTemplates"
          :key="tpl.code"
          class="template-card"
          @click="handleSelectTemplate(tpl)"
        >
          <div class="template-card__header">
            <el-icon class="template-card__icon">
              <component :is="tpl.icon" />
            </el-icon>
            <span class="template-card__name">{{ tpl.name }}</span>
            <el-tag size="small" type="info" class="template-card__category">
              {{ tpl.category }}
            </el-tag>
          </div>
          <div class="template-card__desc">{{ tpl.description }}</div>
          <div class="template-card__footer">
            <span class="template-card__code">{{ tpl.code }}</span>
            <el-button size="small" type="primary" link>使用此模板</el-button>
          </div>
        </div>
      </div>

      <el-empty
        v-if="filteredTemplates.length === 0"
        description="该分类下暂无模板"
        :image-size="80"
      />
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.template-library {
  max-height: 60vh;
  overflow-y: auto;
}

.template-filter {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.template-card {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 8px;

  &:hover {
    border-color: var(--el-color-primary);
    box-shadow: 0 2px 12px rgba(var(--el-color-primary-rgb), 0.15);
    transform: translateY(-2px);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__icon {
    font-size: 18px;
    color: var(--el-color-primary);
  }

  &__name {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
    flex: 1;
  }

  &__category {
    flex-shrink: 0;
  }

  &__desc {
    font-size: 12px;
    color: #64748b;
    line-height: 1.5;
    min-height: 36px;
  }

  &__footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-top: 6px;
    border-top: 1px dashed var(--el-border-color-lighter);
  }

  &__code {
    font-size: 11px;
    color: #94a3b8;
    font-family: 'Consolas', monospace;
  }
}

/* P2-6: 移动端 H5 适配 */
@media (max-width: 768px) {
  .template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
