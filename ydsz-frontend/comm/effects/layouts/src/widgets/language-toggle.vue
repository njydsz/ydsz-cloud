<!--
 * language-toggle 布局组件
 *
 * @path comm\effects\layouts\src\widgets\language-toggle.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { SupportedLanguagesType } from '@ydsz/locales';

import { SUPPORT_LANGUAGES } from '@ydsz/constants';
import { Languages } from '@ydsz/icons';
import { loadLocaleMessages } from '@ydsz/locales';
import { preferences, updatePreferences } from '@ydsz/preferences';

import { YDSZDropdownRadioMenu, YDSZIconButton } from '@ydsz-core/shadcn-ui';

defineOptions({
  name: 'LanguageToggle',
});

async function handleUpdate(value: string | undefined) {
  if (!value) return;
  const locale = value as SupportedLanguagesType;
  updatePreferences({
    app: {
      locale,
    },
  });
  await loadLocaleMessages(locale);
}
</script>

<template>
  <div>
    <YDSZDropdownRadioMenu
      :menus="SUPPORT_LANGUAGES"
      :model-value="preferences.app.locale"
      @update:model-value="handleUpdate"
    >
      <YDSZIconButton>
        <Languages class="text-foreground size-4" />
      </YDSZIconButton>
    </YDSZDropdownRadioMenu>
  </div>
</template>
