<!--
 * tool-more 通用组件
 *
 * @path comm\@core\ui-kit\tabs-ui\src\components\widgets\tool-more.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { DropdownMenuProps } from '@ydsz-core/shadcn-ui';

import { ChevronDown } from '@ydsz-core/icons';
import { YDSZDropdownMenu } from '@ydsz-core/shadcn-ui';

defineProps<DropdownMenuProps>();
</script>

<template>
  <YDSZDropdownMenu :menus="menus" :modal="false">
    <div
      class="flex-center hover:bg-muted hover:text-foreground text-muted-foreground border-border h-full cursor-pointer border-l px-2 text-lg font-semibold"
    >
      <ChevronDown class="size-4" />
    </div>
  </YDSZDropdownMenu>
</template>
