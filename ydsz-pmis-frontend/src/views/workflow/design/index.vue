<script setup lang="ts">
/**
 * @file 流程设计器页面
 * @module views/workflow/design
 * @description P0-01: 双模式设计器 — 经典模式（自绘 SVG） + BPMN 2.0 专业模式（bpmn.js）
 */
import { ref } from 'vue'
import FlowDesigner from '../components/FlowDesigner.vue'
import BpmnDesigner from '../components/BpmnDesigner.vue'
import { PC } from '@/constants/permissionCodes'

const designerMode = ref<'bpmn' | 'classic'>('bpmn')
</script>

<template>
  <div class="page-workflow-design">
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <h2>流程设计器</h2>
          <p class="page-header__sub">可视化建模 → 一键部署到工作流引擎</p>
        </div>
        <el-radio-group v-model="designerMode" size="small">
          <el-radio-button value="bpmn">BPMN 2.0 专业模式</el-radio-button>
          <el-radio-button value="classic">经典模式</el-radio-button>
        </el-radio-group>
      </div>
    </div>
    <div class="page-body">
      <BpmnDesigner v-if="designerMode === 'bpmn'" />
      <FlowDesigner v-else />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-workflow-design {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  padding: 16px;

  .page-header {
    margin-bottom: 12px;
    flex-shrink: 0;

    &-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }

    h2 {
      margin: 0;
      font-size: 20px;
      color: #1e293b;
    }

    &__sub {
      margin: 4px 0 0;
      color: #64748b;
      font-size: 13px;
    }
  }

  .page-body {
    flex: 1;
    min-height: 0;
    background: #fff;
    border-radius: 6px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    overflow: hidden;
  }
}
</style>