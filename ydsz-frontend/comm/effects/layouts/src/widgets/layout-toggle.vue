<!--
 * layout-toggle 布局组件
 *
 * @path comm\effects\layouts\src\widgets\layout-toggle.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { AuthPageLayoutType } from '@ydsz/types';

import type { YDSZDropdownMenuItem } from '@ydsz-core/shadcn-ui';

import { computed } from 'vue';

import { InspectionPanel, PanelLeft, PanelRight } from '@ydsz/icons';
import { $t } from '@ydsz/locales';
import {
  preferences,
  updatePreferences,
  usePreferences,
} from '@ydsz/preferences';

import { YDSZDropdownRadioMenu, YDSZIconButton } from '@ydsz-core/shadcn-ui';

defineOptions({
  name: 'AuthenticationLayoutToggle',
});

const menus = computed((): YDSZDropdownMenuItem[] => [
  {
    icon: PanelLeft,
    label: $t('authentication.layout.alignLeft'),
    value: 'panel-left',
  },
  {
    icon: InspectionPanel,
    label: $t('authentication.layout.center'),
    value: 'panel-center',
  },
  {
    icon: PanelRight,
    label: $t('authentication.layout.alignRight'),
    value: 'panel-right',
  },
]);

const { authPanelCenter, authPanelLeft, authPanelRight } = usePreferences();

function handleUpdate(value: string | undefined) {
  if (!value) return;
  updatePreferences({
    app: {
      authPageLayout: value as AuthPageLayoutType,
    },
  });
}
</script>

<template>
  <YDSZDropdownRadioMenu
    :menus="menus"
    :model-value="preferences.app.authPageLayout"
    @update:model-value="handleUpdate"
  >
    <YDSZIconButton>
      <PanelRight v-if="authPanelRight" class="size-4" />
      <PanelLeft v-if="authPanelLeft" class="size-4" />
      <InspectionPanel v-if="authPanelCenter" class="size-4" />
    </YDSZIconButton>
  </YDSZDropdownRadioMenu>
</template>
