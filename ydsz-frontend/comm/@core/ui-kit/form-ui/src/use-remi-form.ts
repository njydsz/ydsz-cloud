/**
 * use-remi-form 模块
 *
 * @path comm\@core\ui-kit\form-ui\src\use-remi-form.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  BaseFormComponentType,
  ExtendedFormApi,
  YDSZFormProps,
} from './types';

import { defineComponent, h, isReactive, onBeforeUnmount, watch } from 'vue';

import { useStore } from '@ydsz-core/shared/store';

import { FormApi } from './form-api';
import YDSZUseForm from './ydsz-use-form.vue';

/**
 * 创建一对「表单组件 + 命令式 API」，是业务使用 form-ui 的推荐入口。
 *
 * @remarks
 * 采用「渲染与控制分离」的设计：返回的组件只负责放进模板占位，
 * 所有取值、赋值、校验、提交等操作都通过 API 句柄以命令式方式完成，
 * 避免业务代码里堆满 ref 与模板引用。
 *
 * 行为要点：
 * - **响应式支持有条件**：仅当传入的 `options` 是 `reactive` 对象时才会 watch `schema`
 *   并同步到内部 store。传普通对象（包括含 ref 字段的普通对象）时 schema 是静态快照，
 *   后续修改不生效——这是动态表单不刷新的典型原因；
 * - 即便开启了响应式，也**只监听 `schema` 一个属性**，其他 props 的变更不会自动同步，
 *   需调用 `formApi.setState()`；
 * - 组件卸载时自动调用 `api.unmount()` 释放内部状态，因此 **API 句柄在组件卸载后不可再用**；
 * - 组件设置了 `inheritAttrs: false`，透传的 attrs 会与 props 合并后交给内部表单，
 *   不会落到根 DOM 元素上；
 * - 每次调用都会新建一个独立的 FormApi 实例，**不要**在渲染函数或循环中调用。
 *
 * @param options - 表单配置，包含 schema、布局、提交回调等，详见 {@link YDSZFormProps}
 * @returns 长度为 2 的只读元组：`[Form, formApi]`——`Form` 用于模板渲染，
 *          `formApi` 为扩展后的命令式句柄（含 `useStore` 订阅能力）
 *
 * @example
 * ```ts
 * const [Form, formApi] = useYDSZForm({ schema, handleSubmit });
 * await formApi.validate();
 * ```
 */
export function useYDSZForm<
  T extends BaseFormComponentType = BaseFormComponentType,
>(options: YDSZFormProps<T>) {
  const IS_REACTIVE = isReactive(options);
  const api = new FormApi(options);
  const extendedApi: ExtendedFormApi = api as never;
  extendedApi.useStore = (selector) => {
    return useStore(api.store, selector);
  };

  const Form = defineComponent(
    (props: YDSZFormProps, { attrs, slots }) => {
      onBeforeUnmount(() => {
        api.unmount();
      });
      api.setState({ ...props, ...attrs });
      return () =>
        h(YDSZUseForm, { ...props, ...attrs, formApi: extendedApi }, slots);
    },
    {
      name: 'YDSZUseForm',
      inheritAttrs: false,
    },
  );
  // Add reactivity support
  if (IS_REACTIVE) {
    watch(
      () => options.schema,
      () => {
        api.setState({ schema: options.schema });
      },
      { immediate: true },
    );
  }

  return [Form, extendedApi] as const;
}
