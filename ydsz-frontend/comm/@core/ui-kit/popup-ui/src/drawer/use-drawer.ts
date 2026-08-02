/**
 * use-drawer 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\drawer\use-drawer.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  DrawerApiOptions,
  DrawerProps,
  ExtendedDrawerApi,
} from './drawer';

import {
  defineComponent,
  h,
  inject,
  nextTick,
  provide,
  reactive,
  ref,
} from 'vue';

import { useStore } from '@ydsz-core/shared/store';

import { DrawerApi } from './drawer-api';
import YDSZDrawer from './drawer.vue';

const USER_DRAWER_INJECT_KEY = Symbol('YDSZ_DRAWER_INJECT');

const DEFAULT_DRAWER_PROPS: Partial<DrawerProps> = {};

/**
 * 设置抽屉的全局默认 props，统一整个应用的抽屉外观与交互。
 *
 * @remarks
 * 应在应用启动阶段调用，用于集中约定「点遮罩是否可关」「默认从哪侧滑出」等策略，
 * 避免每处调用重复传参。
 *
 * 副作用与约束：
 * - 通过 `Object.assign` **原地修改模块级单例**，多次调用为累加式覆盖，
 *   不会清除此前设置的其他键；
 * - 优先级最低：单个抽屉在 `useYDSZDrawer(options)` 中传入的同名项会覆盖它；
 * - **仅对之后创建的抽屉生效**，已创建的实例不受影响，因此不要在运行中动态调用它来批量改样式。
 *
 * @param props - 要合并进全局默认值的抽屉配置
 */
export function setDefaultDrawerProps(props: Partial<DrawerProps>) {
  Object.assign(DEFAULT_DRAWER_PROPS, props);
}

/**
 * 创建一对「抽屉组件 + 命令式 API」，支持内联使用与独立组件两种模式。
 *
 * @remarks
 * 函数依据是否传入 `connectedComponent` 走**两条完全不同的分支**：
 *
 * **模式一：内联（不传 `connectedComponent`）**
 * 直接创建 DrawerApi 与渲染组件，抽屉内容写在当前组件的插槽里，适合简单场景。
 * 配置按 `全局默认值 → 父级 inject 的配置 → 本次 options` 的顺序合并，后者优先。
 *
 * **模式二：独立组件（传入 `connectedComponent`）**
 * 抽屉内容被抽到独立 SFC 中，父组件拿到的是一个「壳」。此时：
 * - API 通过 provide/inject 由子组件反向注入给父组件，因此**父组件拿到的 API 在子组件完成
 *   初始化之前是空对象**，不能在 setup 同步阶段立即调用其方法；
 * - 扩展 API 时使用 `Object.setPrototypeOf` 而非 `Object.assign`——前者能保留类的原型方法，
 *   后者只会拷贝自有属性导致方法丢失，同时又必须避免直接给 reactive 对象赋值以免丢响应性；
 * - 开发期会校验传参：若在壳组件上传递了与抽屉 state 同名的 props/slots，
 *   会打印 warn 提示改用 API 修改（`class` 属性除外），因为两条修改路径并存会让状态来源难以追踪；
 * - 配置了 `destroyOnClose` 时，关闭动画结束后会通过切换渲染标记强制重建子组件，
 *   从而彻底重置其内部状态；未配置时抽屉内容**会保留上一次的状态**。
 *
 * 两种模式下 `onOpenChange` 都会被包装成「先调本次 options 的回调、再调 inject 来的回调」，
 * 两者都会执行，不存在覆盖关系。
 *
 * @param options - 抽屉初始化配置；传入 `connectedComponent` 即切换为独立组件模式
 * @returns 只读元组 `[Drawer, drawerApi]`——`Drawer` 放入模板渲染，`drawerApi` 用于命令式开关与传值
 *
 * @example
 * ```ts
 * const [Drawer, drawerApi] = useYDSZDrawer({ connectedComponent: DetailDrawer });
 * drawerApi.setData(row).open();
 * ```
 */
export function useYDSZDrawer<
  TParentDrawerProps extends DrawerProps = DrawerProps,
>(options: DrawerApiOptions = {}) {
  // Drawer一般会抽离出来，所以如果有传入 connectedComponent，则表示为外部调用，与内部组件进行连接
  // 外部的Drawer通过provide/inject传递api

  const { connectedComponent } = options;
  if (connectedComponent) {
    const extendedApi = reactive({});
    const isDrawerReady = ref(true);
    const Drawer = defineComponent(
      (props: TParentDrawerProps, { attrs, slots }) => {
        provide(USER_DRAWER_INJECT_KEY, {
          extendApi(api: ExtendedDrawerApi) {
            // 不能直接给 reactive 赋值，会丢失响应
            // 不能用 Object.assign,会丢失 api 的原型函数
            Object.setPrototypeOf(extendedApi, api);
          },
          options,
          async reCreateDrawer() {
            isDrawerReady.value = false;
            await nextTick();
            isDrawerReady.value = true;
          },
        });
        checkProps(extendedApi as ExtendedDrawerApi, {
          ...props,
          ...attrs,
          ...slots,
        });
        return () =>
          h(
            isDrawerReady.value ? connectedComponent : 'div',
            { ...props, ...attrs },
            slots,
          );
      },
      // eslint-disable-next-line vue/one-component-per-file
      {
        name: 'YDSZParentDrawer',
        inheritAttrs: false,
      },
    );

    return [Drawer, extendedApi as ExtendedDrawerApi] as const;
  }

  const injectData = inject<any>(USER_DRAWER_INJECT_KEY, {});

  const mergedOptions = {
    ...DEFAULT_DRAWER_PROPS,
    ...injectData.options,
    ...options,
  } as DrawerApiOptions;

  mergedOptions.onOpenChange = (isOpen: boolean) => {
    options.onOpenChange?.(isOpen);
    injectData.options?.onOpenChange?.(isOpen);
  };

  const onClosed = mergedOptions.onClosed;
  mergedOptions.onClosed = () => {
    onClosed?.();
    if (mergedOptions.destroyOnClose) {
      injectData.reCreateDrawer?.();
    }
  };
  const api = new DrawerApi(mergedOptions);

  const extendedApi: ExtendedDrawerApi = api as never;

  extendedApi.useStore = (selector) => {
    return useStore(api.store, selector);
  };

  const Drawer = defineComponent(
    (props: DrawerProps, { attrs, slots }) => {
      return () =>
        h(YDSZDrawer, { ...props, ...attrs, drawerApi: extendedApi }, slots);
    },
    // eslint-disable-next-line vue/one-component-per-file
    {
      name: 'YDSZDrawer',
      inheritAttrs: false,
    },
  );
  injectData.extendApi?.(extendedApi);
  return [Drawer, extendedApi] as const;
}

async function checkProps(api: ExtendedDrawerApi, attrs: Record<string, any>) {
  if (!attrs || Object.keys(attrs).length === 0) {
    return;
  }
  await nextTick();

  const state = api?.store?.state;

  if (!state) {
    return;
  }

  const stateKeys = new Set(Object.keys(state));

  for (const attr of Object.keys(attrs)) {
    if (stateKeys.has(attr) && !['class'].includes(attr)) {
      // connectedComponent存在时，不要传入Drawer的props，会造成复杂度提升，如果你需要修改Drawer的props，请使用 useYDSZDrawer 或者api
      console.warn(
        `[YDSZ Drawer]: When 'connectedComponent' exists, do not set props or slots '${attr}', which will increase complexity. If you need to modify the props of Drawer, please use useYDSZDrawer or api.`,
      );
    }
  }
}
