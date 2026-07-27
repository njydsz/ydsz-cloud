<script lang="ts" setup>
import { Settings } from '@ydsz/icons';

import { YDSZIconButton } from '@ydsz-core/shadcn-ui';

import Preferences from './preferences.vue';

const emit = defineEmits<{ clearPreferencesAndLogout: [] }>();

function clearPreferencesAndLogout() {
  emit('clearPreferencesAndLogout');
}
</script>
<template>
  <Preferences @clear-preferences-and-logout="clearPreferencesAndLogout">
    <YDSZIconButton>
      <Settings class="text-foreground size-4" />
    </YDSZIconButton>
  </Preferences>
</template>
