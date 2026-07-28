<!--
 * footer 布局组件
 *
 * @path comm\effects\layouts\src\widgets\preferences\blocks\layout\footer.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import { $t } from '@ydsz/locales';

import SwitchItem from '../switch-item.vue';

const footerEnable = defineModel<boolean>('footerEnable');
const footerFixed = defineModel<boolean>('footerFixed');
</script>

<template>
  <SwitchItem v-model="footerEnable">
    {{ $t('preferences.footer.visible') }}
  </SwitchItem>
  <SwitchItem v-model="footerFixed" :disabled="!footerEnable">
    {{ $t('preferences.footer.fixed') }}
  </SwitchItem>
</template>
