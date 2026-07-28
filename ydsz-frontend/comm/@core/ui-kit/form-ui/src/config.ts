/**
 * config 模块
 *
 * @path comm\@core\ui-kit\form-ui\src\config.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component } from 'vue';

import type {
  BaseFormComponentType,
  FormCommonConfig,
  YDSZFormAdapterOptions,
} from './types';

import { h } from 'vue';

import {
  YDSZButton,
  YDSZCheckbox,
  Input as YDSZInput,
  YDSZInputPassword,
  YDSZPinInput,
  YDSZSelect,
} from '@ydsz-core/shadcn-ui';
import { globalShareState } from '@ydsz-core/shared/global-state';

import { defineRule } from 'vee-validate';

const DEFAULT_MODEL_PROP_NAME = 'modelValue';

export const DEFAULT_FORM_COMMON_CONFIG: FormCommonConfig = {};

export const COMPONENT_MAP: Record<BaseFormComponentType, Component> = {
  DefaultButton: h(YDSZButton, { size: 'sm', variant: 'outline' }),
  PrimaryButton: h(YDSZButton, { size: 'sm', variant: 'default' }),
  YDSZCheckbox,
  YDSZInput,
  YDSZInputPassword,
  YDSZPinInput,
  YDSZSelect,
};

export const COMPONENT_BIND_EVENT_MAP: Partial<
  Record<BaseFormComponentType, string>
> = {
  YDSZCheckbox: 'checked',
};

export function setupYDSZForm<
  T extends BaseFormComponentType = BaseFormComponentType,
>(options: YDSZFormAdapterOptions<T>) {
  const { config, defineRules } = options;

  const {
    disabledOnChangeListener = true,
    disabledOnInputListener = true,
    emptyStateValue = undefined,
  } = (config || {}) as FormCommonConfig;

  Object.assign(DEFAULT_FORM_COMMON_CONFIG, {
    disabledOnChangeListener,
    disabledOnInputListener,
    emptyStateValue,
  });

  if (defineRules) {
    for (const key of Object.keys(defineRules)) {
      defineRule(key, defineRules[key as never]);
    }
  }

  const baseModelPropName =
    config?.baseModelPropName ?? DEFAULT_MODEL_PROP_NAME;
  const modelPropNameMap = config?.modelPropNameMap as
    | Record<BaseFormComponentType, string>
    | undefined;

  const components = globalShareState.getComponents();

  for (const component of Object.keys(components)) {
    const key = component as BaseFormComponentType;
    COMPONENT_MAP[key] = components[component as never];

    if (baseModelPropName !== DEFAULT_MODEL_PROP_NAME) {
      COMPONENT_BIND_EVENT_MAP[key] = baseModelPropName;
    }

    // 覆盖特殊组件的modelPropName
    if (modelPropNameMap && modelPropNameMap[key]) {
      COMPONENT_BIND_EVENT_MAP[key] = modelPropNameMap[key];
    }
  }
}
