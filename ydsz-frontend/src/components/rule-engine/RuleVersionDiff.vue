<!--
  @fileoverview 规则版本 Diff 组件 (Vue 3)
  @description 结构化展示两个规则版本之间的字段级差异：
  - 左右分栏对比旧值/新值
  - 变更类型颜色编码（新增=绿、修改=橙、删除=红）
  - 变更摘要统计
  @module components/rule-engine/RuleVersionDiff
  @author ydsz-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * RuleVersionDiff - 规则版本 Diff 视图
 *
 * Props:
 *  - ruleCode: 规则编码
 *
 * Usage:
 *  <RuleVersionDiff rule-code="R001" />
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, DocumentCopy } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type { RuleVersion } from '@/api/rule-engine'
import { logger } from '@/utils/logger'

interface Props {
  ruleCode: string
}

const props = defineProps<Props>()

// ===== 状态 =====
const loading = ref(false)
const versions = ref<RuleVersion[]>([])
const oldVersion = ref<number>()
const newVersion = ref<number>()
const diffResult = ref<any>(null)

// ===== 计算属性 =====
const diffEntries = computed(() => {
  if (!diffResult.value?.entries) return []
  return diffResult.value.entries.filter((e: any) => e.type !== 'UNCHANGED')
})

const addedCount = computed(() => diffEntries.value.filter((e: any) => e.type === 'ADDED').length)
const modifiedCount = computed(() => diffEntries.value.filter((e: any) => e.type === 'MODIFIED').length)
const removedCount = computed(() => diffEntries.value.filter((e: any) => e.type === 'REMOVED').length)

// ===== 方法 =====
async function loadVersions() {
  if (!props.ruleCode) return
  loading.value = true
  try {
    const res = await ruleApi.listVersions(props.ruleCode)
    versions.value = res.data || []
    // 默认选择最后两个版本
    if (versions.value.length >= 2) {
      oldVersion.value = versions.value[versions.value.length - 2].version
      newVersion.value = versions.value[versions.value.length - 1].version
      await loadDiff()
    }
  } catch (err) {
    logger.error('加载版本列表失败', err)
  } finally {
    loading.value = false
  }
}

async function loadDiff() {
  if (!oldVersion.value || !newVersion.value) return
  loading.value = true
  try {
    const res = await ruleApi.versionDiff(props.ruleCode, oldVersion.value, newVersion.value)
    diffResult.value = res.data
  } catch (err) {
    logger.error('加载 Diff 失败', err)
    ElMessage.error('版本 Diff 加载失败')
  } finally {
    loading.value = false
  }
}

function getDiffTagType(type: string): string {
  switch (type) {
    case 'ADDED': return 'success'
    case 'MODIFIED': return 'warning'
    case 'REMOVED': return 'danger'
    default: return 'info'
  }
}

function getDiffLabel(type: string): string {
  switch (type) {
    case 'ADDED': return '新增'
    case 'MODIFIED': return '修改'
    case 'REMOVED': return '删除'
    default: return '未变'
  }
}

function copyValue(val: string) {
  navigator.clipboard.writeText(val || '')
  ElMessage.success('已复制')
}

// ===== 监听 =====
watch(() => props.ruleCode, () => {
  loadVersions()
}, { immediate: true })
</script>

<template>
  <div class="version-diff" v-loading="loading">
    <!-- 版本选择器 -->
    <div class="version-selector">
      <el-select v-model="oldVersion" placeholder="旧版本" style="width: 120px" @change="loadDiff">
        <el-option
          v-for="v in versions"
          :key="v.version"
          :label="`v${v.version}`"
          :value="v.version"
        />
      </el-select>
      <el-icon class="arrow-icon"><ArrowRight /></el-icon>
      <el-select v-model="newVersion" placeholder="新版本" style="width: 120px" @change="loadDiff">
        <el-option
          v-for="v in versions"
          :key="v.version"
          :label="`v${v.version}`"
          :value="v.version"
        />
      </el-select>
      <el-button type="primary" @click="loadDiff" :disabled="!oldVersion || !newVersion">
        对比
      </el-button>

      <!-- 变更摘要 -->
      <div v-if="diffResult" class="summary">
        <el-tag type="info" effect="plain">{{ diffResult.summary }}</el-tag>
        <el-tag v-if="addedCount > 0" type="success" size="small">+{{ addedCount }} 新增</el-tag>
        <el-tag v-if="modifiedCount > 0" type="warning" size="small">~{{ modifiedCount }} 修改</el-tag>
        <el-tag v-if="removedCount > 0" type="danger" size="small">-{{ removedCount }} 删除</el-tag>
      </div>
    </div>

    <!-- Diff 列表 -->
    <div v-if="diffEntries.length > 0" class="diff-list">
      <div v-for="entry in diffEntries" :key="entry.field" class="diff-row">
        <div class="diff-type">
          <el-tag :type="getDiffTagType(entry.type)" size="small">
            {{ getDiffLabel(entry.type) }}
          </el-tag>
        </div>
        <div class="diff-field">
          <span class="field-label">{{ entry.fieldLabel }}</span>
          <span class="field-name">{{ entry.field }}</span>
        </div>
        <div class="diff-values">
          <div class="value-old" v-if="entry.oldValue">
            <span class="value-label">旧值:</span>
            <code class="value-code">{{ entry.oldValue }}</code>
            <el-button :icon="DocumentCopy" link size="small" @click="copyValue(entry.oldValue)" />
          </div>
          <div class="value-new" v-if="entry.newValue">
            <span class="value-label">新值:</span>
            <code class="value-code">{{ entry.newValue }}</code>
            <el-button :icon="DocumentCopy" link size="small" @click="copyValue(entry.newValue)" />
          </div>
        </div>
      </div>
    </div>

    <!-- 无变更 -->
    <el-empty v-else-if="diffResult && diffEntries.length === 0" description="两个版本无差异" />
  </div>
</template>

<style scoped>
.version-diff {
  padding: 16px;
}

.version-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.arrow-icon {
  color: var(--el-text-color-secondary);
}

.summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;
}

.diff-list {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

.diff-row {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 16px;
}

.diff-row:last-child {
  border-bottom: none;
}

.diff-type {
  flex-shrink: 0;
  width: 60px;
}

.diff-field {
  flex-shrink: 0;
  width: 200px;
}

.field-label {
  display: block;
  font-weight: 500;
  font-size: 13px;
}

.field-name {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: monospace;
}

.diff-values {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.value-old, .value-new {
  display: flex;
  align-items: center;
  gap: 4px;
}

.value-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  width: 40px;
  flex-shrink: 0;
}

.value-code {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.value-old .value-code {
  background: rgba(245, 108, 108, 0.1);
  color: var(--el-color-danger);
}

.value-new .value-code {
  background: rgba(103, 194, 58, 0.1);
  color: var(--el-color-success);
}
</style>
