import type {
  BaseFormComponentType,
  ExtendedFormApi,
  YDSZFormProps,
} from './types';

import { defineComponent, h, isReactive, onBeforeUnmount, watch } from 'vue';

import { useStore } from '@ydsz-core/shared/store';

import { FormApi } from './form-api';
import YDSZUseForm from './ydsz-use-form.vue';

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
