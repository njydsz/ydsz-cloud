<!--
 * dropdown-menu 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\dropdown-menu\dropdown-menu.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type {
  DropdownMenuProps,
  YDSZDropdownMenuItem as IDropdownMenuItem,
} from './interface';

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../../ui';

interface Props extends DropdownMenuProps {}

defineOptions({ name: 'DropdownMenu' });
const props = withDefaults(defineProps<Props>(), {});

function handleItemClick(menu: IDropdownMenuItem) {
  if (menu.disabled) {
    return;
  }
  menu?.handler?.(props);
}
</script>
<template>
  <DropdownMenu>
    <DropdownMenuTrigger class="flex h-full items-center gap-1" aria-haspopup="menu">
      <slot></slot>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="start" role="menu">
      <DropdownMenuGroup>
        <template v-for="menu in menus" :key="menu.value">
          <DropdownMenuItem
            :disabled="menu.disabled"
            class="data-[state=checked]:bg-accent data-[state=checked]:text-accent-foreground text-foreground/80 mb-1 cursor-pointer"
            role="menuitem"
            :aria-label="menu.label"
            @click="handleItemClick(menu)"
          >
            <component :is="menu.icon" v-if="menu.icon" class="mr-2 size-4" aria-hidden="true" />
            {{ menu.label }}
          </DropdownMenuItem>
          <DropdownMenuSeparator v-if="menu.separator" class="bg-border" role="separator" />
        </template>
      </DropdownMenuGroup>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
