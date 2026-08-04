<!--
 * excel-export-button 通用组件
 *
 * @path comm\effects\shared-business\src\components\excel-export-button.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * Excel 导出按钮 — 声明式导出，绑定列定义与数据源即用
 */
import { ElButton } from 'element-plus';

import {
  useExcelExport,
  type ExcelExportColumn,
} from '../composables/use-excel-export';

interface Props<T = any> {
  /** 导出列定义 */
  columns: ExcelExportColumn<T>[];
  /** 导出数据 */
  data: T[];
  /** 文件名（不含扩展名） */
  fileName?: string;
  /** 按钮文字 */
  text?: string;
  /** 导出前回调（可返回 false 阻止导出） */
  beforeExport?: () => boolean | Promise<boolean>;
}

const props = withDefaults(defineProps<Props>(), {
  fileName: 'export',
  text: '导出',
  beforeExport: undefined,
});

const { exportExcel } = useExcelExport();

async function handleClick() {
  if (props.beforeExport) {
    const canProceed = await props.beforeExport();
    if (!canProceed) return;
  }
  exportExcel({
    fileName: props.fileName,
    columns: props.columns,
    data: props.data,
  });
}
</script>

<template>
  <el-button size="small" type="primary" plain @click="handleClick">
    {{ text }}
  </el-button>
</template>
