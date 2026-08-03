/**
 * init 模块
 *
 * @path comm\effects\plugins\src\vxe-table\init.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { SetupVxeTable } from './types';

import { defineComponent, watch } from 'vue';

import { usePreferences } from '@ydsz/preferences';

import { useYDSZForm } from '@ydsz-core/form-ui';

import {
  VxeButton,
  VxeCheckbox,

  // VxeFormGather,
  // VxeForm,
  // VxeFormItem,
  VxeIcon,
  VxeInput,
  VxeLoading,
  VxeModal,
  VxeNumberInput,
  VxePager,
  // VxeList,
  // VxeModal,
  // VxeOptgroup,
  // VxeOption,
  // VxePulldown,
  // VxeRadio,
  // VxeRadioButton,
  VxeRadioGroup,
  VxeSelect,
  VxeTooltip,
  VxeUI,
  VxeUpload,
  // VxeSwitch,
  // VxeTextarea,
} from 'vxe-pc-ui';
import enUS from 'vxe-pc-ui/lib/language/en-US';
// 导入默认的语言
import zhCN from 'vxe-pc-ui/lib/language/zh-CN';
import {
  VxeColgroup,
  VxeColumn,
  VxeGrid,
  VxeTable,
  VxeToolbar,
} from 'vxe-table';

import { extendsDefaultFormatter } from './extends';

// 是否加载过
let isInit = false;

/**
 * 延迟绑定的表格表单 API（模块加载后由 init 赋值）。
 *
 * @remarks
 * 由于 `useYDSZForm` 依赖运行时注册的 vxe-table 组件，此处先声明可变的
 * 导出占位，待 {@link initVxeTable} 完成组件注册后再赋值为真正的实现，
 * 供业务侧统一从本模块导入，避免时序问题。
 */
// eslint-disable-next-line import/no-mutable-exports
export let useTableForm: typeof useYDSZForm;

// 部分组件，如果没注册，vxe-table 会报错，这里实际没用组件，只是为了不报错，同时可以减少打包体积
const createVirtualComponent = (name = '') => {
  return defineComponent({
    name,
  });
};

/**
 * 按需注册 vxe-table / vxe-pc-ui 组件。
 *
 * @remarks
 * 幂等：通过模块级 `isInit` 标记保证只执行一次，重复调用直接返回。
 *
 * 为控制打包体积，这里只注册项目实际用到的组件；
 * 其中 `VxeForm` 是用 `defineComponent` 造的**空壳虚拟组件**——
 * vxe-table 内部会强校验其存在，不注册会报错，而项目实际使用的是自研表单，
 * 因此以空组件占位既能规避报错又不引入真实实现的体积。
 *
 * 若业务需要用到被注释掉的组件（如 `VxeSwitch`），必须先在此处注册，否则运行时报错。
 */
export function initVxeTable() {
  if (isInit) {
    return;
  }

  VxeUI.component(VxeTable);
  VxeUI.component(VxeColumn);
  VxeUI.component(VxeColgroup);
  VxeUI.component(VxeGrid);
  VxeUI.component(VxeToolbar);

  VxeUI.component(VxeButton);
  // VxeUI.component(VxeButtonGroup);
  VxeUI.component(VxeCheckbox);
  // VxeUI.component(VxeCheckboxGroup);
  VxeUI.component(createVirtualComponent('VxeForm'));
  // VxeUI.component(VxeFormGather);
  // VxeUI.component(VxeFormItem);
  VxeUI.component(VxeIcon);
  VxeUI.component(VxeInput);
  // VxeUI.component(VxeList);
  VxeUI.component(VxeLoading);
  VxeUI.component(VxeModal);
  VxeUI.component(VxeNumberInput);
  // VxeUI.component(VxeOptgroup);
  // VxeUI.component(VxeOption);
  VxeUI.component(VxePager);
  // VxeUI.component(VxePulldown);
  // VxeUI.component(VxeRadio);
  // VxeUI.component(VxeRadioButton);
  VxeUI.component(VxeRadioGroup);
  VxeUI.component(VxeSelect);
  // VxeUI.component(VxeSwitch);
  // VxeUI.component(VxeTextarea);
  VxeUI.component(VxeTooltip);
  VxeUI.component(VxeUpload);

  isInit = true;
}

/**
 * 完成 vxe-table 在本项目中的全部接入工作，应在应用启动阶段调用一次。
 *
 * @remarks
 * 执行顺序（有依赖关系，勿调整）：
 * 1. `initVxeTable()` 注册组件（幂等）；
 * 2. 把应用层传入的表单实现赋值给模块级变量 `useTableForm`，供表格内置搜索表单使用；
 * 3. 建立 `watch` 同步暗色主题与语言（`immediate: true`，调用即生效）；
 * 4. 注册默认格式化器；
 * 5. 最后执行调用方的 `configVxeTable`，因此**应用层配置可以覆盖上述默认设置**。
 *
 * 副作用：第 3 步的 `watch` 在应用生命周期内常驻不会被停止；
 * 语言映射目前只覆盖 `zh-CN` 与 `en-US`，新增语种需要同步扩展 `localMap`，
 * 否则 `setI18n` 会拿到 `undefined` 导致 vxe-table 文案回退为空。
 *
 * @param setupOptions - 应用层注入的适配参数，见 {@link SetupVxeTable}
 */
export function setupYDSZVxeTable(setupOptions: SetupVxeTable) {
  const { configVxeTable, useYDSZForm } = setupOptions;

  initVxeTable();
  useTableForm = useYDSZForm;

  const { isDark, locale } = usePreferences();

  const localMap = {
    'zh-CN': zhCN,
    'en-US': enUS,
  };

  watch(
    [() => isDark.value, () => locale.value],
    ([isDarkValue, localeValue]) => {
      VxeUI.setTheme(isDarkValue ? 'dark' : 'light');
      VxeUI.setI18n(localeValue, localMap[localeValue]);
      VxeUI.setLanguage(localeValue);
    },
    {
      immediate: true,
    },
  );

  extendsDefaultFormatter(VxeUI);

  configVxeTable(VxeUI);
}
