<!--
  @fileoverview 快捷键帮助面板
  @description 展示当前所有已注册的键盘快捷键，支持按作用域分组（全局/页面）
  @module components/common/ShortcutHelpDialog
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 快捷键帮助面板
 *
 * 以弹窗形式展示所有已注册的键盘快捷键，
 * 按作用域分为全局快捷键与页面快捷键两组。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useKeyboardShortcuts } from '@/composables/useKeyboardShortcuts'

const { t } = useI18n()
const { shortcuts, helpVisible, toggleHelp } = useKeyboardShortcuts()

/**
 * 将快捷键定义格式化为可读文本
 * macOS 系统使用 ⌘⌥⇧ 符号，其他系统使用 Ctrl/Alt/Shift + 键名
 */
function formatKey(def: { key: string; ctrl?: boolean; shift?: boolean; alt?: boolean }): string {
  const isMac = navigator.platform.toLowerCase().includes('mac')
  const parts: string[] = []
  if (def.ctrl) parts.push(isMac ? '⌘' : 'Ctrl')
  if (def.shift) parts.push(isMac ? '⇧' : 'Shift')
  if (def.alt) parts.push(isMac ? '⌥' : 'Alt')

  // 特殊键名映射
  const keyMap: Record<string, string> = {
    escape: 'Esc',
    enter: '↵',
    backspace: '⌫',
    arrowup: '↑',
    arrowdown: '↓',
    arrowleft: '←',
    arrowright: '→',
  }
  const displayKey = keyMap[def.key.toLowerCase()] || def.key.toUpperCase()
  parts.push(displayKey)

  return parts.join(isMac ? '' : '+')
}

/** 全局快捷键列表（非 page scope 且有描述） */
const globalShortcuts = computed(() =>
  shortcuts.value.filter((s) => s.scope !== 'page' && s.description),
)

/** 页面快捷键列表（page scope 且有描述） */
const pageShortcuts = computed(() =>
  shortcuts.value.filter((s) => s.scope === 'page' && s.description),
)
</script>

<template>
  <el-dialog
    :model-value="helpVisible"
    :title="t('common.shortcutHelp')"
    width="520px"
    @update:model-value="toggleHelp"
  >
    <div class="shortcut-help">
      <div v-if="globalShortcuts.length" class="shortcut-group">
        <div class="shortcut-group__title">{{ t('common.shortcutGlobal') }}</div>
        <div v-for="(s, idx) in globalShortcuts" :key="'g' + idx" class="shortcut-item">
          <span class="shortcut-desc">{{ s.description }}</span>
          <kbd class="shortcut-key">{{ formatKey(s) }}</kbd>
        </div>
      </div>

      <div v-if="pageShortcuts.length" class="shortcut-group">
        <div class="shortcut-group__title">{{ t('common.shortcutPage') }}</div>
        <div v-for="(s, idx) in pageShortcuts" :key="'p' + idx" class="shortcut-item">
          <span class="shortcut-desc">{{ s.description }}</span>
          <kbd class="shortcut-key">{{ formatKey(s) }}</kbd>
        </div>
      </div>

      <div v-if="!globalShortcuts.length && !pageShortcuts.length" class="shortcut-empty">
        {{ t('common.shortcutEmpty') }}
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.shortcut-help {
  .shortcut-group {
    margin-bottom: 16px;

    &__title {
      font-size: 13px;
      color: $text-secondary;
      margin-bottom: 8px;
      font-weight: 600;
    }
  }

  .shortcut-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    border-bottom: 1px solid $border-extra-light;
  }

  .shortcut-desc {
    font-size: 14px;
    color: $text-primary;
  }

  .shortcut-key {
    display: inline-block;
    padding: 2px 8px;
    background: $bg-page;
    border: 1px solid $border-light;
    border-radius: 4px;
    font-size: 12px;
    font-family: monospace;
    color: $text-regular;
    box-shadow: 0 1px 0 $border-base;
  }

  .shortcut-empty {
    text-align: center;
    color: $text-secondary;
    padding: 24px 0;
  }
}
</style>
