/**
 * 通用组件共同的使用的基础组件，原先放在 adapter/form 内部，限制了使用范围，这里提取出来，方便其他地方使用
 * 可用于 ydsz-form、ydsz-modal、ydsz-drawer 等组件使用,
 */

import type { Component } from 'vue';

import type { BaseFormComponentType } from '@ydsz/common-ui';
import type { Recordable } from '@ydsz/types';

import { defineAsyncComponent, defineComponent, h, ref } from 'vue';

import { ApiComponent, globalShareState, IconPicker } from '@ydsz/common-ui';
import { $t } from '@ydsz/locales';

import { ElNotification } from 'element-plus';

/**
 * 创建 Element Plus 异步组件工厂。
 *
 * 统一处理组件逻辑与样式 CSS 的并行加载，消除 17 处重复的
 * `Promise.all([import(comp), import(css)]).then(([res]) => res.ElXxx)` 模板。
 *
 * @param componentName - Element Plus 组件名（kebab-case，如 'button'、'input-number'）
 * @param stylePath - 样式路径后缀（默认与 componentName 相同；如 'checkbox-button' 样式与 'checkbox' 逻辑分开）
 * @returns 异步加载的 Element Plus 组件
 */
function createElAsyncComponent<T>(componentName: string, stylePath?: string): T {
  const css = stylePath ?? componentName;
  return defineAsyncComponent(() =>
    Promise.all([
      import(`element-plus/es/components/${componentName}/index`),
      import(`element-plus/es/components/${css}/style/css`),
    ]).then(([res]) => (res as { [key: string]: Component })[
      `El${componentName.replace(/(^|-)([a-z])/g, (_, __, c: string) => c.toUpperCase())}`
    ]),
  ) as unknown as T;
}

const ElButton = createElAsyncComponent<Component>('button');
const ElCheckbox = createElAsyncComponent<Component>('checkbox');
const ElCheckboxButton = createElAsyncComponent<Component>('checkbox', 'checkbox-button');
const ElCheckboxGroup = createElAsyncComponent<Component>('checkbox', 'checkbox-group');
const ElDatePicker = createElAsyncComponent<Component>('date-picker');
const ElDivider = createElAsyncComponent<Component>('divider');
const ElInput = createElAsyncComponent<Component>('input');
const ElInputNumber = createElAsyncComponent<Component>('input-number');
const ElRadio = createElAsyncComponent<Component>('radio');
const ElRadioButton = createElAsyncComponent<Component>('radio', 'radio-button');
const ElRadioGroup = createElAsyncComponent<Component>('radio', 'radio-group');
const ElSelectV2 = createElAsyncComponent<Component>('select-v2');
const ElSpace = createElAsyncComponent<Component>('space');
const ElSwitch = createElAsyncComponent<Component>('switch');
const ElTimePicker = createElAsyncComponent<Component>('time-picker');
const ElTreeSelect = createElAsyncComponent<Component>('tree-select');
const ElUpload = createElAsyncComponent<Component>('upload');

/**
 * 为业务组件包裹默认占位符与方法透传的高阶包装。
 *
 * 按类型（input/select）注入本地化 placeholder，并通过 Proxy 将内部实例暴露的方法透传给外层。
 */
const withDefaultPlaceholder = <T extends Component>(
  component: T,
  type: 'input' | 'select',
  componentProps: Recordable<any> = {},
) => {
  return defineComponent({
    name: component.name,
    inheritAttrs: false,
    setup: (props: any, { attrs, expose, slots }) => {
      const placeholder =
        props?.placeholder ||
        attrs?.placeholder ||
        $t(`ui.placeholder.${type}`);
      // 透传组件暴露的方法
      const innerRef = ref();
      expose(
        new Proxy(
          {},
          {
            get: (_target, key) => innerRef.value?.[key],
            has: (_target, key) => key in (innerRef.value || {}),
          },
        ),
      );
      return () =>
        h(
          component,
          { ...componentProps, placeholder, ...props, ...attrs, ref: innerRef },
          slots,
        );
    },
  });
};

/**
 * 表单支持的组件类型集合。
 *
 * 需随业务组件库自行适配：用到的组件都应在此联合类型中声明。
 */
export type ComponentType =
  | 'ApiSelect'
  | 'ApiTreeSelect'
  | 'Checkbox'
  | 'CheckboxGroup'
  | 'DatePicker'
  | 'Divider'
  | 'IconPicker'
  | 'Input'
  | 'InputNumber'
  | 'RadioGroup'
  | 'Select'
  | 'Space'
  | 'Switch'
  | 'TimePicker'
  | 'TreeSelect'
  | 'Upload'
  | BaseFormComponentType;

/**
 * 初始化组件适配器。
 *
 * 将 Element Plus 组件映射为表单/弹窗/抽屉可用的业务组件，并注册全局消息提示到共享状态。
 */
async function initComponentAdapter() {
  const components: Partial<Record<ComponentType, Component>> = {
    // 如果你的组件体积比较大，可以使用异步加载
    // Button: () =>
    // import('xxx').then((res) => res.Button),
    ApiSelect: withDefaultPlaceholder(
      {
        ...ApiComponent,
        name: 'ApiSelect',
      },
      'select',
      {
        component: ElSelectV2,
        loadingSlot: 'loading',
        visibleEvent: 'onVisibleChange',
      },
    ),
    ApiTreeSelect: withDefaultPlaceholder(
      {
        ...ApiComponent,
        name: 'ApiTreeSelect',
      },
      'select',
      {
        component: ElTreeSelect,
        props: { label: 'label', children: 'children' },
        nodeKey: 'value',
        loadingSlot: 'loading',
        optionsPropName: 'data',
        visibleEvent: 'onVisibleChange',
      },
    ),
    Checkbox: ElCheckbox,
    CheckboxGroup: (props, { attrs, slots }) => {
      let defaultSlot;
      if (Reflect.has(slots, 'default')) {
        defaultSlot = slots.default;
      } else {
        const { options, isButton } = attrs;
        if (Array.isArray(options)) {
          defaultSlot = () =>
            options.map((option) =>
              h(isButton ? ElCheckboxButton : ElCheckbox, option),
            );
        }
      }
      return h(
        ElCheckboxGroup,
        { ...props, ...attrs },
        { ...slots, default: defaultSlot },
      );
    },
    // 自定义默认按钮
    DefaultButton: (props, { attrs, slots }) => {
      return h(ElButton, { ...props, attrs, type: 'info' }, slots);
    },
    // 自定义主要按钮
    PrimaryButton: (props, { attrs, slots }) => {
      return h(ElButton, { ...props, attrs, type: 'primary' }, slots);
    },
    Divider: ElDivider,
    IconPicker: withDefaultPlaceholder(IconPicker, 'select', {
      iconSlot: 'append',
      modelValueProp: 'model-value',
      inputComponent: ElInput,
    }),
    Input: withDefaultPlaceholder(ElInput, 'input'),
    InputNumber: withDefaultPlaceholder(ElInputNumber, 'input'),
    RadioGroup: (props, { attrs, slots }) => {
      let defaultSlot;
      if (Reflect.has(slots, 'default')) {
        defaultSlot = slots.default;
      } else {
        const { options } = attrs;
        if (Array.isArray(options)) {
          defaultSlot = () =>
            options.map((option) =>
              h(attrs.isButton ? ElRadioButton : ElRadio, option),
            );
        }
      }
      return h(
        ElRadioGroup,
        { ...props, ...attrs },
        { ...slots, default: defaultSlot },
      );
    },
    Select: (props, { attrs, slots }) => {
      return h(ElSelectV2, { ...props, attrs }, slots);
    },
    Space: ElSpace,
    Switch: ElSwitch,
    TimePicker: (props, { attrs, slots }) => {
      const { name, id, isRange } = props;
      const extraProps: Recordable<any> = {};
      if (isRange) {
        if (name && !Array.isArray(name)) {
          extraProps.name = [name, `${name}_end`];
        }
        if (id && !Array.isArray(id)) {
          extraProps.id = [id, `${id}_end`];
        }
      }
      return h(
        ElTimePicker,
        {
          ...props,
          ...attrs,
          ...extraProps,
        },
        slots,
      );
    },
    DatePicker: (props, { attrs, slots }) => {
      const { name, id, type } = props;
      const extraProps: Recordable<any> = {};
      if (type && type.includes('range')) {
        if (name && !Array.isArray(name)) {
          extraProps.name = [name, `${name}_end`];
        }
        if (id && !Array.isArray(id)) {
          extraProps.id = [id, `${id}_end`];
        }
      }
      return h(
        ElDatePicker,
        {
          ...props,
          ...attrs,
          ...extraProps,
        },
        slots,
      );
    },
    TreeSelect: withDefaultPlaceholder(ElTreeSelect, 'select'),
    Upload: ElUpload,
  };

  // 将组件注册到全局共享状态中
  globalShareState.setComponents(components);

  // 定义全局共享状态中的消息提示
  globalShareState.defineMessage({
    // 复制成功消息提示
    copyPreferencesSuccess: (title, content) => {
      ElNotification({
        title,
        message: content,
        position: 'bottom-right',
        duration: 0,
        type: 'success',
      });
    },
  });
}

export { initComponentAdapter };
