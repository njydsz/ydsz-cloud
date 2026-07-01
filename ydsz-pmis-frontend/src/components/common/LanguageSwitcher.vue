<!--
  @file 顶部语言切换组件
  @description 下拉式语言切换，支持 zh-CN / en-US；基于 useI18n composable 实现
  @module components/common/LanguageSwitcher
  (批次 20 P2-2)
-->
<script setup lang="ts">
import { useI18n, supportedLocales } from '@/composables/useI18n'
import type { Locale } from '@/composables/useI18n'
import { ElTooltip } from 'element-plus'

const { locale, setLocale, t } = useI18n()

/** 切换语言回调 */
function handleSelect(next: Locale) {
  if (next === locale.value) return
  setLocale(next)
  // 触发轻量提示
  // eslint-disable-next-line no-console
  console.info(`[i18n] switched to ${next}`)
}
</script>

<template>
  <el-tooltip :content="t('user.profile')" placement="bottom">
    <el-dropdown trigger="click" @command="handleSelect">
      <el-button text :aria-label="t('user.profile')">
        <el-icon :size="18"><Position /></el-icon>
        <span class="lang-label">{{ locale }}</span>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="item in supportedLocales"
            :key="item.code"
            :command="item.code"
            :disabled="item.code === locale"
          >
            <span class="lang-option">
              <el-icon v-if="item.code === locale"><Check /></el-icon>
              <span class="lang-option-label">{{ item.label }}</span>
              <span class="lang-option-code">{{ item.code }}</span>
            </span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </el-tooltip>
</template>

<style lang="scss" scoped>
.lang-label {
  margin-left: 4px;
  font-size: 12px;
  color: $text-secondary;
}

.lang-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 140px;

  &-label {
    flex: 1;
  }

  &-code {
    font-size: 11px;
    color: $text-placeholder;
    font-family: $font-family-mono;
  }
}
</style>
