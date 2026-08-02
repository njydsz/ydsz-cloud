/**
 * use-tabbar 模块
 *
 * @path comm\effects\layouts\src\basic\tabbar\use-tabbar.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteLocationNormalizedGeneric } from 'vue-router';

import type { TabDefinition } from '@ydsz/types';

import type { IContextMenuItem } from '@ydsz-core/tabs-ui';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useContentMaximize, useTabs } from '@ydsz/hooks';
import {
  ArrowLeftToLine,
  ArrowRightLeft,
  ArrowRightToLine,
  ExternalLink,
  FoldHorizontal,
  Fullscreen,
  Minimize2,
  Pin,
  PinOff,
  RotateCw,
  X,
} from '@ydsz/icons';
import { $t, useI18n } from '@ydsz/locales';
import { getTabKey, useAccessStore, useTabbarStore } from '@ydsz/stores';
import { filterTree } from '@ydsz/utils';

/**
 * 标签栏容器组件的逻辑聚合，负责标签数据、激活态与右键菜单的组装。
 *
 * @remarks
 * 入参：无。
 *
 * 副作用与生命周期依赖（**必须在组件 `setup` 中调用**，内部使用了 `useRoute` / `useRouter` 与三个 `watch`）：
 * 1. 侦听 `accessStore.accessMenus`（`immediate`）：权限菜单变化后重新扫描路由表，
 *    把带 `meta.affixTab` 的路由写入 store 作为固定标签；
 * 2. 侦听 `route.fullPath`（`immediate`）：每次路由变化自动向 store 追加标签，
 *    取的是 `matched` 链最末级的 `meta`，以保证嵌套路由拿到叶子节点的标题与图标；
 * 3. 侦听标签列表、`updateTime` 与当前语言：任一变化都会重建 `currentTabs`。
 *
 * 这些侦听器都随组件作用域自动销毁，无需手动清理。
 *
 * 国际化约定：`currentTabs` 中的 `meta.title` 已经过 `$t` 处理，是**翻译后的文本副本**，
 * 不要直接回写 store；语言切换时依靠 `locale` 侦听整体重算，这也是把标签标题
 * 以 i18n key 形式存进 store 的原因。
 *
 * 菜单裁剪：`createContextMenus` 生成全量菜单后，会按 `tabbarStore.getMenuList` 过滤，
 * 因此用户在偏好设置里关闭的菜单项不会出现；禁用态由 `getTabDisableState` 统一计算。
 *
 * @returns 标签栏渲染所需的数据与回调：`currentTabs` 为已本地化的标签列表，
 * `currentActive` 为当前激活标签的 key，`handleClick` 点击切换路由，
 * `handleClose` 关闭指定标签，`createContextMenus` 按标签生成右键菜单项
 */
export function useTabbar() {
  const router = useRouter();
  const route = useRoute();
  const accessStore = useAccessStore();
  const tabbarStore = useTabbarStore();
  const { contentIsMaximize, toggleMaximize } = useContentMaximize();
  const {
    closeAllTabs,
    closeCurrentTab,
    closeLeftTabs,
    closeOtherTabs,
    closeRightTabs,
    closeTabByKey,
    getTabDisableState,
    openTabInNewWindow,
    refreshTab,
    toggleTabPin,
  } = useTabs();

  /**
   * 当前路径对应的tab的key
   */
  const currentActive = computed(() => {
    return getTabKey(route);
  });

  const { locale } = useI18n();
  const currentTabs = ref<RouteLocationNormalizedGeneric[]>();
  watch(
    [
      () => tabbarStore.getTabs,
      () => tabbarStore.updateTime,
      () => locale.value,
    ],
    ([tabs]) => {
      currentTabs.value = tabs.map((item) => wrapperTabLocale(item));
    },
  );

  /**
   * 初始化固定标签页
   */
  const initAffixTabs = () => {
    const affixTabs = filterTree(router.getRoutes(), (route) => {
      return !!route.meta?.affixTab;
    });
    tabbarStore.setAffixTabs(affixTabs);
  };

  // 点击tab,跳转路由
  const handleClick = (key: string) => {
    const { fullPath, path } = tabbarStore.getTabByKey(key);
    router.push(fullPath || path);
  };

  // 关闭tab
  const handleClose = async (key: string) => {
    await closeTabByKey(key);
  };

  function wrapperTabLocale(tab: RouteLocationNormalizedGeneric) {
    return {
      ...tab,
      meta: {
        ...tab?.meta,
        title: $t(tab?.meta?.title as string),
      },
    };
  }

  watch(
    () => accessStore.accessMenus,
    () => {
      initAffixTabs();
    },
    { immediate: true },
  );

  watch(
    () => route.fullPath,
    () => {
      const meta = route.matched?.[route.matched.length - 1]?.meta;
      tabbarStore.addTab({
        ...route,
        meta: meta || route.meta,
      });
    },
    { immediate: true },
  );

  const createContextMenus = (tab: TabDefinition) => {
    const {
      disabledCloseAll,
      disabledCloseCurrent,
      disabledCloseLeft,
      disabledCloseOther,
      disabledCloseRight,
      disabledRefresh,
    } = getTabDisableState(tab);

    const affixTab = tab?.meta?.affixTab ?? false;

    const menus: IContextMenuItem[] = [
      {
        disabled: disabledCloseCurrent,
        handler: async () => {
          await closeCurrentTab(tab);
        },
        icon: X,
        key: 'close',
        text: $t('preferences.tabbar.contextMenu.close'),
      },
      {
        handler: async () => {
          await toggleTabPin(tab);
        },
        icon: affixTab ? PinOff : Pin,
        key: 'affix',
        text: affixTab
          ? $t('preferences.tabbar.contextMenu.unpin')
          : $t('preferences.tabbar.contextMenu.pin'),
      },
      {
        handler: async () => {
          if (!contentIsMaximize.value) {
            await router.push(tab.fullPath);
          }
          toggleMaximize();
        },
        icon: contentIsMaximize.value ? Minimize2 : Fullscreen,
        key: contentIsMaximize.value ? 'restore-maximize' : 'maximize',
        text: contentIsMaximize.value
          ? $t('preferences.tabbar.contextMenu.restoreMaximize')
          : $t('preferences.tabbar.contextMenu.maximize'),
      },
      {
        disabled: disabledRefresh,
        handler: () => refreshTab(),
        icon: RotateCw,
        key: 'reload',
        text: $t('preferences.tabbar.contextMenu.reload'),
      },
      {
        handler: async () => {
          await openTabInNewWindow(tab);
        },
        icon: ExternalLink,
        key: 'open-in-new-window',
        separator: true,
        text: $t('preferences.tabbar.contextMenu.openInNewWindow'),
      },

      {
        disabled: disabledCloseLeft,
        handler: async () => {
          await closeLeftTabs(tab);
        },
        icon: ArrowLeftToLine,
        key: 'close-left',
        text: $t('preferences.tabbar.contextMenu.closeLeft'),
      },
      {
        disabled: disabledCloseRight,
        handler: async () => {
          await closeRightTabs(tab);
        },
        icon: ArrowRightToLine,
        key: 'close-right',
        separator: true,
        text: $t('preferences.tabbar.contextMenu.closeRight'),
      },
      {
        disabled: disabledCloseOther,
        handler: async () => {
          await closeOtherTabs(tab);
        },
        icon: FoldHorizontal,
        key: 'close-other',
        text: $t('preferences.tabbar.contextMenu.closeOther'),
      },
      {
        disabled: disabledCloseAll,
        handler: closeAllTabs,
        icon: ArrowRightLeft,
        key: 'close-all',
        text: $t('preferences.tabbar.contextMenu.closeAll'),
      },
    ];

    return menus.filter((item) => tabbarStore.getMenuList.includes(item.key));
  };

  return {
    createContextMenus,
    currentActive,
    currentTabs,
    handleClick,
    handleClose,
  };
}
