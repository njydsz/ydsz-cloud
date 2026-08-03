/**
 * index 组合式函数
 *
 * @path comm\@core\composables\src\use-simple-locale\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Locale } from './messages';

import { computed, ref } from 'vue';

import { createSharedComposable } from '@vueuse/core';

import { getMessages } from './messages';

/** 全局共享的极简 i18n 组合式函数，提供 $t 翻译函数与当前语言切换能力 */
export const useSimpleLocale = createSharedComposable(() => {
  const currentLocale = ref<Locale>('zh-CN');

  const setSimpleLocale = (locale: Locale) => {
    currentLocale.value = locale;
  };

  const $t = computed(() => {
    const localeMessages = getMessages(currentLocale.value);
    return (key: string) => {
      return localeMessages[key] || key;
    };
  });
  return {
    $t,
    currentLocale,
    setSimpleLocale,
  };
});
