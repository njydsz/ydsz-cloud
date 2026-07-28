<!--
 * AvatarImage Vue 组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\avatar\AvatarImage.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { AvatarImageProps } from 'radix-vue';

import { AvatarImage } from 'radix-vue';

const props = defineProps<AvatarImageProps>();
</script>

<template>
  <AvatarImage v-bind="props" class="h-full w-full object-cover" />
</template>
