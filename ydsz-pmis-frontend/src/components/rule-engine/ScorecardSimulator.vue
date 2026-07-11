<!--
  @fileoverview 评分卡模拟器组件 (Vue 3)
  @description 交互式评分卡模拟运行：
  - 动态表单输入评分维度
  - 实时计算总分和等级
  - 评分明细展示
  @module components/rule-engine/ScorecardSimulator
  @author ydsz-pmis-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * ScorecardSimulator - 评分卡模拟器
 *
 * Props:
 *  - definition: 评分卡定义
 */
import { ref, computed, watch } from 'vue'
import { Calculator } from '@element-plus/icons-vue'
import type { ScorecardDefinition } from '@/api/rule-engine'

interface Props {
  definition: ScorecardDefinition
}

const props = defineProps<Props>()

// 输入值
const inputValues = ref<Record<string, any>>({})

// 计算结果
const computedScores = computed(() => {
  if (!props.definition?.dimensions) return []

  const results: Array<{
    dimension: string
    inputValue: any
    matchedBucket: string
    score: number
    weight: number
    weightedScore: number
  }> = []

  for (const dim of props.definition.dimensions) {
    const inputVal = inputValues.value[dim.name]
    let matchedBucket: string = '未匹配'
    let score = 0

    for (const bucket of dim.buckets || []) {
      if (evaluateBucketCondition(bucket.condition, inputVal)) {
        matchedBucket = bucket.label || bucket.condition
        score = bucket.score || 0
        break
      }
    }

    const weight = dim.weight || 1
    results.push({
      dimension: dim.name,
      inputValue: inputVal,
      matchedBucket,
      score,
      weight,
      weightedScore: score * weight
    })
  }

  return results
})

const totalScore = computed(() => {
  return computedScores.value.reduce((sum, r) => sum + r.weightedScore, 0)
})

const grade = computed(() => {
  const score = totalScore.value
  if (!props.definition?.gradeBands) return '—'

  for (const band of props.definition.gradeBands) {
    if (score >= band.minScore && score <= band.maxScore) {
      return band.label || band.grade
    }
  }
  return '—'
})

const gradeColor = computed(() => {
  const g = grade.value
  if (['A', '优秀'].includes(g)) return '#67c23a'
  if (['B', '良好'].includes(g)) return '#409eff'
  if (['C', '一般'].includes(g)) return '#e6a23c'
  if (['D', '差'].includes(g)) return '#f56c6c'
  return '#909399'
})

// 简单的桶条件求值
function evaluateBucketCondition(condition: string, value: any): boolean {
  if (!condition || value === undefined) return false
  try {
    // 支持 [min, max) 格式
    const rangeMatch = condition.match(/\[(-?\d+\.?\d*),\s*(-?\d+\.?\d*)\)/)
    if (rangeMatch) {
      const min = parseFloat(rangeMatch[1])
      const max = parseFloat(rangeMatch[2])
      const numVal = Number(value)
      return numVal >= min && numVal < max
    }
    // 支持 > xxx, < xxx, >= xxx, <= xxx, == xxx 格式
    const opMatch = condition.match(/^(>=|<=|>|<|==)\s*(-?\d+\.?\d*)$/)
    if (opMatch) {
      const op = opMatch[1]
      const threshold = parseFloat(opMatch[2])
      const numVal = Number(value)
      switch (op) {
        case '>': return numVal > threshold
        case '<': return numVal < threshold
        case '>=': return numVal >= threshold
        case '<=': return numVal <= threshold
        case '==': return numVal === threshold
      }
    }
    // 其他条件作为字符串匹配
    return String(value) === condition
  } catch {
    return false
  }
}

// 初始化输入
watch(() => props.definition, (def) => {
  if (def?.dimensions) {
    for (const dim of def.dimensions) {
      if (inputValues.value[dim.name] === undefined) {
        inputValues.value[dim.name] = dim.defaultValue ?? null
      }
    }
  }
}, { immediate: true, deep: true })
</script>

<template>
  <div class="scorecard-simulator">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><Calculator /></el-icon>
          <span>评分卡模拟器</span>
        </div>
      </template>

      <!-- 维度输入表单 -->
      <el-form label-width="140px" class="input-form">
        <el-form-item
          v-for="dim in (definition?.dimensions || [])"
          :key="dim.name"
          :label="dim.label || dim.name"
        >
          <el-input-number
            v-if="dim.type === 'number'"
            v-model="inputValues[dim.name]"
            :min="dim.min"
            :max="dim.max"
            :step="dim.step || 1"
            controls-position="right"
            style="width: 200px"
          />
          <el-select
            v-else-if="dim.type === 'select'"
            v-model="inputValues[dim.name]"
            style="width: 200px"
          >
            <el-option
              v-for="opt in (dim.options || [])"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input
            v-else
            v-model="inputValues[dim.name]"
            style="width: 200px"
          />
          <span class="dim-weight">权重: {{ dim.weight || 1 }}</span>
        </el-form-item>
      </el-form>

      <!-- 计算结果 -->
      <el-divider />

      <div class="result-section">
        <div class="total-score">
          <div class="score-label">总分</div>
          <div class="score-value">{{ totalScore.toFixed(2) }}</div>
        </div>
        <div class="grade-display" :style="{ color: gradeColor }">
          <div class="grade-label">等级</div>
          <div class="grade-value">{{ grade }}</div>
        </div>
      </div>

      <!-- 评分明细 -->
      <el-table :data="computedScores" stripe size="small" style="margin-top: 16px">
        <el-table-column prop="dimension" label="维度" min-width="120" />
        <el-table-column prop="inputValue" label="输入值" width="100" />
        <el-table-column prop="matchedBucket" label="匹配区间" min-width="150" />
        <el-table-column prop="score" label="得分" width="80" sortable />
        <el-table-column prop="weight" label="权重" width="80" />
        <el-table-column prop="weightedScore" label="加权得分" width="100" sortable>
          <template #default="{ row }">
            {{ row.weightedScore.toFixed(2) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.scorecard-simulator {
  max-width: 800px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.input-form {
  margin-top: 10px;
}

.dim-weight {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-section {
  display: flex;
  justify-content: center;
  gap: 60px;
  padding: 20px 0;
}

.total-score, .grade-display {
  text-align: center;
}

.score-label, .grade-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.score-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.grade-value {
  font-size: 36px;
  font-weight: 700;
}
</style>
