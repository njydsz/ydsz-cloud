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

/** 表单控件行为默认配置，由 setupYDSZForm 在启动时合并宿主传入的覆盖项 */
export const DEFAULT_FORM_COMMON_CONFIG: FormCommonConfig = {};

/** 基础表单控件类型到渲染组件的映射表，可由宿主通过 globalShareState 注册的组件按需覆盖 */
export const COMPONENT_MAP: Record<BaseFormComponentType, Component> = {
  DefaultButton: h(YDSZButton, { size: 'sm', variant: 'outline' }),
  PrimaryButton: h(YDSZButton, { size: 'sm', variant: 'default' }),
  YDSZCheckbox,
  YDSZInput,
  YDSZInputPassword,
  YDSZPinInput,
  YDSZSelect,
};

/** 各表单控件默认绑定的 v-model prop 名映射，如复选框使用 `checked` 而非 `modelValue` */
export const COMPONENT_BIND_EVENT_MAP: Partial<
  Record<BaseFormComponentType, string>
> = {
  YDSZCheckbox: 'checked',
};

/**
 * 初始化表单适配层，把宿主应用的 UI 组件库对接到 form-ui。
 *
 * @remarks
 * 应在应用启动阶段（创建 app 之后、渲染表单之前）调用**一次**。它做了四件事：
 * 1. 把控件行为默认值写入模块级单例 {@link DEFAULT_FORM_COMMON_CONFIG}；
 * 2. 通过 vee-validate 的 `defineRule` 注册全局校验规则；
 * 3. 从 {@link globalShareState} 读取宿主注册的组件，合并进 {@link COMPONENT_MAP}；
 * 4. 为这些组件推导 v-model 的 prop 名，写入 {@link COMPONENT_BIND_EVENT_MAP}。
 *
 * 需要特别注意的副作用与顺序约束：
 * - 三个映射表都是**模块级可变单例**，此函数为原地修改而非返回新配置。
 *   重复调用会以后一次为准覆盖同名项，但**不会清除**上一次注册的多余项；
 * - 必须在 `globalShareState.setComponents()` 之后调用，否则第 3 步读到空对象，
 *   自定义控件全部注册失败，表现为字段渲染空白；
 * - 同名 key 会直接覆盖内置控件（如自定义 `YDSZInput` 将替换默认实现），
 *   这是有意保留的定制能力，但也意味着命名冲突不会有任何告警。
 *
 * `disabledOnChangeListener` / `disabledOnInputListener` 缺省均为 `true`，
 * 即默认关闭 change/input 监听以避免与 v-model 重复触发校验；
 * `emptyStateValue` 缺省为 `undefined`，对接 naive-ui 等要求 `null` 的库时必须显式指定。
 *
 * @param options - 适配器配置，含控件行为配置与内置规则实现，详见 {@link YDSZFormAdapterOptions}
 */
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
