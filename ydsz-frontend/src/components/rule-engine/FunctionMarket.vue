<!--
  @fileoverview 函数市场组件 (Vue 3)
  @description 展示和管理局则引擎可用的表达式函数：
  - 按分类展示（数学/字符串/集合/类型/时间/工具）
  - 函数搜索
  - 函数签名 + 描述 + 示例
  - 点击插入到表达式编辑器
  @module components/rule-engine/FunctionMarket
  @author ydsz-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * FunctionMarket - 函数市场
 *
 * Props:
 *  - functions: 函数列表
 *
 * Events:
 *  - insert: 插入函数到编辑器
 */
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'

interface FunctionDef {
  name: string
  signature: string
  description: string
  category: string
  sample?: string
  engine?: string
}

interface Props {
  functions: FunctionDef[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'insert', func: FunctionDef): void
}>()

/** 搜索关键词 */
const searchText = ref('')
/** 当前选中的分类 */
const activeCategory = ref('all')

/** 提取所有可用的分类列表 */
const categories = computed(() => {
  const cats = new Set<string>()
  for (const f of props.functions) {
    cats.add(f.category || 'utility')
  }
  return ['all', ...Array.from(cats)]
})

/** 分类标签中文映射 */
const categoryLabels: Record<string, string> = {
  all: '全部',
  math: '数学',
  string: '字符串',
  collection: '集合',
  type: '类型转换',
  datetime: '时间',
  utility: '工具'
}

/** 按分类和关键词过滤后的函数列表 */
const filteredFunctions = computed(() => {
  let result = props.functions
  if (activeCategory.value !== 'all') {
    result = result.filter(f => (f.category || 'utility') === activeCategory.value)
  }
  if (searchText.value) {
    const q = searchText.value.toLowerCase()
    result = result.filter(f =>
      f.name.toLowerCase().includes(q) ||
      f.description.toLowerCase().includes(q) ||
      f.signature.toLowerCase().includes(q)
    )
  }
  return result.sort((a, b) => a.name.localeCompare(b.name))
})

/** 按分类分组的函数列表 */
const groupedFunctions = computed(() => {
  const groups: Record<string, FunctionDef[]> = {}
  for (const f of filteredFunctions.value) {
    const cat = f.category || 'utility'
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(f)
  }
  return groups
})

/** 点击函数卡片，触发 insert 事件供表达式编辑器使用 */
function handleInsert(func: FunctionDef) {
  emit('insert', func)
}

/** 获取函数引擎标签文本 */
function getEngineTag(engine?: string): string {
  if (engine === 'liteexpr') return 'LiteExpr'
  return ''
}
</script>

<template>
  <div class="function-market">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchText"
        placeholder="搜索函数名/描述/签名..."
        :prefix-icon="Search"
        clearable
        style="width: 300px"
      />
      <el-radio-group v-model="activeCategory" size="small">
        <el-radio-button
          v-for="cat in categories"
          :key="cat"
          :value="cat"
        >
          {{ categoryLabels[cat] || cat }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 函数列表 -->
    <div class="function-groups">
      <div v-for="(funcs, cat) in groupedFunctions" :key="cat" class="function-group">
        <h4 class="group-title">{{ categoryLabels[cat] || cat }}</h4>
        <!-- 函数卡片列表（点击触发 insert 事件供表达式编辑器使用） -->
        <div class="function-cards">
          <div
            v-for="func in funcs"
            :key="func.name"
            class="function-card"
            @click="handleInsert(func)"
          >
            <div class="func-header">
              <code class="func-name">{{ func.name }}</code>
              <el-tag v-if="getEngineTag(func.engine)" size="small" type="info">
                {{ getEngineTag(func.engine) }}
              </el-tag>
            </div>
            <div class="func-signature">{{ func.signature }}</div>
            <div class="func-description">{{ func.description }}</div>
            <div v-if="func.sample" class="func-sample">
              <span class="sample-label">示例:</span>
              <code>{{ func.sample }}</code>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="filteredFunctions.length === 0" description="未找到匹配的函数" />
  </div>
</template>

<style scoped>
.function-market {
  padding: 12px;
  max-height: 600px;
  overflow-y: auto;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.function-group {
  margin-bottom: 20px;
}

.group-title {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.function-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.function-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.function-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.func-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.func-name {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-weight: 600;
  color: var(--el-color-primary);
  font-size: 13px;
}

.func-signature {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}

.func-description {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.func-sample {
  font-size: 11px;
}

.sample-label {
  color: var(--el-text-color-placeholder);
  margin-right: 4px;
}

.func-sample code {
  font-family: 'Fira Code', 'Consolas', monospace;
  color: var(--el-color-success);
}
</style>
