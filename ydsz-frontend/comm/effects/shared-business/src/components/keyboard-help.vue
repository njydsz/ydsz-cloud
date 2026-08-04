<!--
 * keyboard-help 通用组件 — 快捷键帮助面板
 *
 * @path comm\effects\shared-business\src\components\keyboard-help.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 快捷键帮助面板 — 按 `?` 或配置键唤起，展示已注册快捷键
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { ElDialog, ElTag } from 'element-plus';

import { bindGlobalShortcut, type ShortcutDescriptor } from '../composables/use-keyboard-shortcut';

interface Props {
  /** 快捷键列表 */
  shortcuts: Array<ShortcutDescriptor & { label: string }>;
  /** 唤起键，默认 '?' */
  triggerKey?: string;
}

const props = withDefaults(defineProps<Props>(), {
  shortcuts: () => [],
  triggerKey: '?',
});

const visible = ref(false);
let unbindFn: (() => void) | null = null;

/** 修饰键 → 展示文案 */
function formatShortcut(desc: ShortcutDescriptor): string {
  const parts: string[] = [];
  if (desc.modifiers?.includes('ctrl')) parts.push('Ctrl');
  if (desc.modifiers?.includes('shift')) parts.push('Shift');
  if (desc.modifiers?.includes('alt')) parts.push('Alt');
  if (desc.modifiers?.includes('meta')) parts.push('⌘');
  parts.push(desc.key.toUpperCase());
  return parts.join(' + ');
}

const formatted = computed(() =>
  props.shortcuts.map((item) => ({
    ...item,
    display: formatShortcut(item),
  })),
);

onMounted(() => {
  unbindFn = bindGlobalShortcut(
    { key: props.triggerKey.toLowerCase(), allowInInput: true, scope: 'keyboard-help' },
    () => {
      visible.value = !visible.value;
    },
  );
});

onBeforeUnmount(() => {
  unbindFn?.();
});
</script>

<template>
  <el-dialog v-model="visible" title="键盘快捷键" width="480px" append-to-body>
    <div class="keyboard-help">
      <div
        v-for="item in formatted"
        :key="`${item.display}-${item.label}`"
        class="keyboard-help__row"
      >
        <span class="keyboard-help__label">{{ item.label }}</span>
        <el-tag size="small" effect="plain">{{ item.display }}</el-tag>
      </div>
      <div v-if="formatted.length === 0" class="keyboard-help__empty">
        暂无快捷键配置
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.keyboard-help__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  border-bottom: 1px solid #f0f0f0;
}
.keyboard-help__label {
  font-size: 14px;
  color: #303133;
}
.keyboard-help__empty {
  text-align: center;
  color: #909399;
  padding: 24px 0;
  font-size: 13px;
}
</style>
