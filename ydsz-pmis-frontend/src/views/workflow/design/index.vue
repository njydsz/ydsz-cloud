<script setup lang="ts">
/**
 * @file 流程设计器页面
 * @module views/workflow/design
 * @description 管理员通过此页面对流程建模，部署到工作流引擎。
 * P0-5: 流程设计器（对标 Activiti-Modeler / 钉钉审批设计器 / 飞书审批设计器）。
 */
import FlowDesigner from '../components/FlowDesigner.vue'
import { PC } from '@/constants/permissionCodes'
</script>

<template>
  <div class="page-workflow-design">
    <div class="page-header">
      <h2>流程设计器</h2>
      <p class="page-header__sub">可视化建模 → 一键部署到工作流引擎</p>
    </div>
    <div class="page-body">
      <FlowDesigner />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-workflow-design {
  padding: 16px;

  .page-header {
    margin-bottom: 16px;

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
    background: #fff;
    border-radius: 6px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    overflow: hidden;
  }
}
</style>
