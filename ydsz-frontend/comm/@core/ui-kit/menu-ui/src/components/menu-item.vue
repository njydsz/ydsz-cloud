<!--
 * menu-item 通用组件
 *
 * @path comm\@core\ui-kit\menu-ui\src\components\menu-item.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { MenuItemProps, MenuItemRegistered } from '../types';

import { computed, onBeforeUnmount, onMounted, reactive, useSlots } from 'vue';

import { useNamespace } from '@ydsz-core/composables';
import { YDSZIcon, YDSZTooltip } from '@ydsz-core/shadcn-ui';

import { MenuBadge } from '../components';
import { useMenu, useMenuContext, useSubMenuContext } from '../hooks';
import { getPreloadManager } from '@ydsz/micro-kernel';

interface Props extends MenuItemProps {}

defineOptions({ name: 'MenuItem' });

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
});

const emit = defineEmits<{ click: [MenuItemRegistered] }>();

const slots = useSlots();
const { b, e, is } = useNamespace('menu-item');
const nsMenu = useNamespace('menu');
const rootMenu = useMenuContext();
const subMenu = useSubMenuContext();
const { parentMenu, parentPaths } = useMenu();

const active = computed(() => props.path === rootMenu?.activePath);
const menuIcon = computed(() =>
  active.value ? props.activeIcon || props.icon : props.icon,
);

const isTopLevelMenuItem = computed(
  () => parentMenu.value?.type.name === 'Menu',
);

const collapseShowTitle = computed(
  () =>
    rootMenu.props?.collapseShowTitle &&
    isTopLevelMenuItem.value &&
    rootMenu.props.collapse,
);

const showTooltip = computed(
  () =>
    rootMenu.props.mode === 'vertical' &&
    isTopLevelMenuItem.value &&
    rootMenu.props?.collapse &&
    slots.title,
);

const item: MenuItemRegistered = reactive({
  active,
  parentPaths: parentPaths.value,
  path: props.path || '',
});

/**
 * 菜单项点击事件
 */
function handleClick() {
  if (props.disabled) {
    return;
  }
  rootMenu?.handleMenuItemClick?.({
    parentPaths: parentPaths.value,
    path: props.path,
  });
  emit('click', item);
}

/**
 * 菜单项 hover 事件 - 触发子应用预加载
 *
 * P1-3.2: 基于菜单 hover/权限动态调整 prefetch
 * - 权限检查：无权限则跳过预加载
 * - 频率统计：记录访问频率用于智能预加载
 */
function handleMouseEnter() {
  if (props.disabled || !props.path) {
    return;
  }

  const preloadManager = getPreloadManager();
  const menuPath = props.path;

  // 查找匹配的子应用（根据路径前缀）
  const pathSegments = menuPath.split('/').filter(Boolean);
  if (pathSegments.length > 0) {
    const appName = pathSegments[0];

    // 权限检查：无权限则跳过
    if (!preloadManager.hasPermission(appName)) {
      return;
    }

    // 触发预加载（异步，不阻塞）
    preloadManager.triggerPreload(appName).catch(() => {
      // 预加载失败不影响用户体验，静默处理
    });
  }
}

onMounted(() => {
  subMenu?.addSubMenu?.(item);
  rootMenu?.addMenuItem?.(item);
});

onBeforeUnmount(() => {
  subMenu?.removeSubMenu?.(item);
  rootMenu?.removeMenuItem?.(item);
});
</script>
<template>
  <li
    :class="[
      rootMenu.theme,
      b(),
      is('active', active),
      is('disabled', disabled),
      is('collapse-show-title', collapseShowTitle),
    ]"
    role="menuitem"
    @click.stop="handleClick"
    @mouseenter="handleMouseEnter"
  >
    <YDSZTooltip
      v-if="showTooltip"
      :content-class="[rootMenu.theme]"
      side="right"
    >
      <template #trigger>
        <div :class="[nsMenu.be('tooltip', 'trigger')]">
          <YDSZIcon :class="nsMenu.e('icon')" :icon="menuIcon" fallback />
          <slot></slot>
          <span v-if="collapseShowTitle" :class="nsMenu.e('name')">
            <slot name="title"></slot>
          </span>
        </div>
      </template>
      <slot name="title"></slot>
    </YDSZTooltip>
    <div v-show="!showTooltip" :class="[e('content')]">
      <MenuBadge
        v-if="rootMenu.props.mode !== 'horizontal'"
        class="right-2"
        v-bind="props"
      />
      <YDSZIcon :class="nsMenu.e('icon')" :icon="menuIcon" />
      <slot></slot>
      <slot name="title"></slot>
    </div>
  </li>
</template>
