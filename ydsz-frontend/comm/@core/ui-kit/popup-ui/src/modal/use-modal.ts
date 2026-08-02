/**
 * use-modal 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\modal\use-modal.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ExtendedModalApi, ModalApiOptions, ModalProps } from './modal';

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

import { ModalApi } from './modal-api';
import YDSZModal from './modal.vue';

const USER_MODAL_INJECT_KEY = Symbol('YDSZ_MODAL_INJECT');

const DEFAULT_MODAL_PROPS: Partial<ModalProps> = {};

/**
 * 设置弹窗的全局默认 props，统一整个应用的弹窗外观与交互策略。
 *
 * @remarks
 * 应在应用启动阶段调用一次，避免每处调用重复传「点遮罩是否可关」「是否居中」等参数。
 *
 * 副作用与约束：
 * - 通过 `Object.assign` **原地修改模块级单例**，多次调用为累加覆盖，不会清空已有键；
 * - 优先级最低，会被 `useYDSZModal(options)` 中的同名项覆盖；
 * - **仅影响之后创建的弹窗**，已存在的实例不受影响。
 *
 * @param props - 要合并进全局默认值的弹窗配置
 */
export function setDefaultModalProps(props: Partial<ModalProps>) {
  Object.assign(DEFAULT_MODAL_PROPS, props);
}

/**
 * 创建一对「弹窗组件 + 命令式 API」，支持内联使用与独立组件两种模式。
 *
 * @remarks
 * 结构与 `useYDSZDrawer` 完全对称，依据是否传入 `connectedComponent` 分为两条分支：
 *
 * **模式一：内联（不传 `connectedComponent`）**
 * 直接创建 ModalApi 与渲染组件，弹窗内容写在当前组件插槽内。
 * 配置合并顺序为 `全局默认值 → 父级 inject 配置 → 本次 options`，后者优先。
 *
 * **模式二：独立组件（传入 `connectedComponent`）**
 * 弹窗内容抽到独立 SFC，父组件只拿到壳组件。需注意：
 * - API 由子组件通过 provide/inject 反向注入，**父组件在子组件初始化完成前拿到的是空对象**，
 *   不可在 setup 同步阶段调用其方法；
 * - 扩展 API 用 `Object.setPrototypeOf` 而非 `Object.assign`，前者保留类原型方法，
 *   同时避免直接给 reactive 赋值造成响应性丢失；
 * - 开发期校验：在壳组件上传递与弹窗 state 同名的 props/slots 会打印 warn
 *   （`class` 除外），因为双通道修改状态会让数据来源难以追踪；
 * - 配置 `destroyOnClose` 时，关闭动画结束后强制重建子组件以彻底重置其内部状态；
 *   未配置则**内容状态会保留到下次打开**。
 *
 * `onOpenChange` 会被包装为「先执行本次 options 的回调、再执行 inject 来的回调」，两者均会触发。
 *
 * @param options - 弹窗初始化配置；传入 `connectedComponent` 即切换为独立组件模式
 * @returns 只读元组 `[Modal, modalApi]`——`Modal` 放入模板渲染，`modalApi` 用于命令式开关与传值
 *
 * @example
 * ```ts
 * const [Modal, modalApi] = useYDSZModal({ connectedComponent: EditModal });
 * modalApi.setData({ id }).open();
 * ```
 */
export function useYDSZModal<TParentModalProps extends ModalProps = ModalProps>(
  options: ModalApiOptions = {},
) {
  // Modal一般会抽离出来，所以如果有传入 connectedComponent，则表示为外部调用，与内部组件进行连接
  // 外部的Modal通过provide/inject传递api

  const { connectedComponent } = options;
  if (connectedComponent) {
    const extendedApi = reactive({});
    const isModalReady = ref(true);
    const Modal = defineComponent(
      (props: TParentModalProps, { attrs, slots }) => {
        provide(USER_MODAL_INJECT_KEY, {
          extendApi(api: ExtendedModalApi) {
            // 不能直接给 reactive 赋值，会丢失响应
            // 不能用 Object.assign,会丢失 api 的原型函数
            Object.setPrototypeOf(extendedApi, api);
          },
          options,
          async reCreateModal() {
            isModalReady.value = false;
            await nextTick();
            isModalReady.value = true;
          },
        });
        checkProps(extendedApi as ExtendedModalApi, {
          ...props,
          ...attrs,
          ...slots,
        });
        return () =>
          h(
            isModalReady.value ? connectedComponent : 'div',
            {
              ...props,
              ...attrs,
            },
            slots,
          );
      },
      // eslint-disable-next-line vue/one-component-per-file
      {
        name: 'YDSZParentModal',
        inheritAttrs: false,
      },
    );

    return [Modal, extendedApi as ExtendedModalApi] as const;
  }

  const injectData = inject<any>(USER_MODAL_INJECT_KEY, {});

  const mergedOptions = {
    ...DEFAULT_MODAL_PROPS,
    ...injectData.options,
    ...options,
  } as ModalApiOptions;

  mergedOptions.onOpenChange = (isOpen: boolean) => {
    options.onOpenChange?.(isOpen);
    injectData.options?.onOpenChange?.(isOpen);
  };

  const onClosed = mergedOptions.onClosed;
  mergedOptions.onClosed = () => {
    onClosed?.();
    if (mergedOptions.destroyOnClose) {
      injectData.reCreateModal?.();
    }
  };

  const api = new ModalApi(mergedOptions);

  const extendedApi: ExtendedModalApi = api as never;

  extendedApi.useStore = (selector) => {
    return useStore(api.store, selector);
  };

  const Modal = defineComponent(
    (props: ModalProps, { attrs, slots }) => {
      return () =>
        h(
          YDSZModal,
          {
            ...props,
            ...attrs,
            modalApi: extendedApi,
          },
          slots,
        );
    },
    // eslint-disable-next-line vue/one-component-per-file
    {
      name: 'YDSZModal',
      inheritAttrs: false,
    },
  );
  injectData.extendApi?.(extendedApi);

  return [Modal, extendedApi] as const;
}

async function checkProps(api: ExtendedModalApi, attrs: Record<string, any>) {
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
      // connectedComponent存在时，不要传入Modal的props，会造成复杂度提升，如果你需要修改Modal的props，请使用 useModal 或者api
      console.warn(
        `[YDSZ Modal]: When 'connectedComponent' exists, do not set props or slots '${attr}', which will increase complexity. If you need to modify the props of Modal, please use useYDSZModal or api.`,
      );
    }
  }
}
