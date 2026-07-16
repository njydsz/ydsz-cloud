<!--
  @fileoverview 顶部语言切换组件
  @description 下拉式语言切换，支持 zh-CN / en-US：
  - 基于 vue-i18n 实现，切换全站生效
  - 通过 setLocale / getLocale 操作 locale
  - 场景: 顶栏多语言切换入口
  @module components/common/LanguageSwitcher
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale, getLocale, type LocaleType } from '@/locales'
import { ElTooltip } from 'element-plus'
import { logger } from '@/utils/logger'

const { t } = useI18n()

/** 支持的语言列表 */
const supportedLocales: ReadonlyArray<{ code: LocaleType; label: string }> = [
  { code: 'zh-CN', label: '简体中文' },
  { code: 'en-US', label: 'English' },
]

/** 当前 locale（响应式，随 vue-i18n global locale 变化） */
const currentLocale = computed(() => getLocale())

/** 切换语言回调 */
function handleSelect(next: LocaleType) {
  if (next === currentLocale.value) return
  setLocale(next)
  logger.info('[i18n]', `switched to ${next}`)
}
</script>

<template>
  <el-tooltip :content="t('common.language') || 'Language'" placement="bottom">
    <el-dropdown trigger="click" @command="handleSelect">
      <el-button text :aria-label="t('common.language') || 'Language'">
        <el-icon :size="18"><Position /></el-icon>
        <span class="lang-label">{{ currentLocale }}</span>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="item in supportedLocales"
            :key="item.code"
            :command="item.code"
            :disabled="item.code === currentLocale"
          >
            <span class="lang-option">
              <el-icon v-if="item.code === currentLocale"><Check /></el-icon>
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
    font-family: 'SFMono-Regular', Consolas, monospace;
  }
}
</style>
