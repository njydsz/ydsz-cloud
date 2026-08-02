/**
 * use-design-tokens 组合式函数
 *
 * @path comm\effects\hooks\src\use-design-tokens.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { reactive, watch } from 'vue';

import { preferences, usePreferences } from '@ydsz/preferences';
import { convertToRgb, updateCSSVariables } from '@ydsz/utils';

/**
 * 用于适配各个框架的设计系统
 */

export function useAntdDesignTokens() {
  const rootStyles = getComputedStyle(document.documentElement);

  const tokens = reactive({
    borderRadius: '' as any,
    colorBgBase: '',
    colorBgContainer: '',
    colorBgElevated: '',
    colorBgLayout: '',
    colorBgMask: '',
    colorBorder: '',
    colorBorderSecondary: '',
    colorError: '',
    colorInfo: '',
    colorPrimary: '',
    colorSuccess: '',
    colorTextBase: '',
    colorWarning: '',
    zIndexPopupBase: 2000, // 调整基础弹层层级，避免下拉等组件被弹窗或者最大化状态下的表格遮挡
  });

  const getCssVariableValue = (variable: string, isColor: boolean = true) => {
    const value = rootStyles.getPropertyValue(variable);
    return isColor ? `hsl(${value})` : value;
  };

  watch(
    () => preferences.theme,
    () => {
      tokens.colorPrimary = getCssVariableValue('--primary');

      tokens.colorInfo = getCssVariableValue('--primary');

      tokens.colorError = getCssVariableValue('--destructive');

      tokens.colorWarning = getCssVariableValue('--warning');

      tokens.colorSuccess = getCssVariableValue('--success');

      tokens.colorTextBase = getCssVariableValue('--foreground');

      getCssVariableValue('--primary-foreground');

      tokens.colorBorderSecondary = tokens.colorBorder =
        getCssVariableValue('--border');

      tokens.colorBgElevated = getCssVariableValue('--popover');

      tokens.colorBgContainer = getCssVariableValue('--card');

      tokens.colorBgBase = getCssVariableValue('--background');

      const radius = Number.parseFloat(getCssVariableValue('--radius', false));
      // 1rem = 16px
      tokens.borderRadius = radius * 16;

      tokens.colorBgLayout = getCssVariableValue('--background-deep');
      tokens.colorBgMask = getCssVariableValue('--overlay');
    },
    { immediate: true },
  );

  return {
    tokens,
  };
}

/**
 * 把项目 CSS 变量映射为 Naive UI 的 `commonTokens`，实现主题联动。
 *
 * @remarks
 * 入参：无。
 *
 * 返回值：`{ commonTokens }`，可直接作为 `NConfigProvider` 的 `theme-overrides.common` 传入。
 * 该对象是 `reactive` 的，主题切换后引用不变、内部字段自动更新，无需重新赋值。
 *
 * 副作用与生命周期：
 * - 调用时立即（`immediate: true`）读取一次 CSS 变量并填充 tokens；
 * - 侦听 `preferences.theme` 变化重新计算，**未显式停止侦听**，
 *   因此建议在组件 `setup` 中调用（作用域销毁时自动回收），避免在模块顶层反复调用造成侦听器堆积；
 * - 依赖 `document.documentElement`，只能在浏览器端调用，SSR 环境会直接报错；
 * - `getComputedStyle` 的结果在调用时取得一次，若运行时替换整份主题样式表，需要重新调用本函数。
 *
 * 取值约定：项目 CSS 变量存的是不带 `hsl()` 包裹的裸值，这里统一补上 `hsl()`
 * 再由 `convertToRgb` 转成 Naive UI 可识别的 rgb 字符串；圆角 `--radius` 属于非颜色值，原样返回带单位的字符串。
 *
 * @returns `commonTokens` —— Naive UI 通用主题变量集合（响应式）
 */
export function useNaiveDesignTokens() {
  const rootStyles = getComputedStyle(document.documentElement);

  const commonTokens = reactive({
    baseColor: '',
    bodyColor: '',
    borderColor: '',
    borderRadius: '',
    cardColor: '',
    dividerColor: '',
    errorColor: '',
    errorColorHover: '',
    errorColorPressed: '',
    errorColorSuppl: '',
    invertedColor: '',
    modalColor: '',
    popoverColor: '',
    primaryColor: '',
    primaryColorHover: '',
    primaryColorPressed: '',
    primaryColorSuppl: '',
    successColor: '',
    successColorHover: '',
    successColorPressed: '',
    successColorSuppl: '',
    tableColor: '',
    textColorBase: '',
    warningColor: '',
    warningColorHover: '',
    warningColorPressed: '',
    warningColorSuppl: '',
  });

  const getCssVariableValue = (variable: string, isColor: boolean = true) => {
    const value = rootStyles.getPropertyValue(variable);
    return isColor ? convertToRgb(`hsl(${value})`) : value;
  };

  watch(
    () => preferences.theme,
    () => {
      commonTokens.primaryColor = getCssVariableValue('--primary');
      commonTokens.primaryColorHover = getCssVariableValue('--primary-600');
      commonTokens.primaryColorPressed = getCssVariableValue('--primary-700');
      commonTokens.primaryColorSuppl = getCssVariableValue('--primary-800');

      commonTokens.errorColor = getCssVariableValue('--destructive');
      commonTokens.errorColorHover = getCssVariableValue('--destructive-600');
      commonTokens.errorColorPressed = getCssVariableValue('--destructive-700');
      commonTokens.errorColorSuppl = getCssVariableValue('--destructive-800');

      commonTokens.warningColor = getCssVariableValue('--warning');
      commonTokens.warningColorHover = getCssVariableValue('--warning-600');
      commonTokens.warningColorPressed = getCssVariableValue('--warning-700');
      commonTokens.warningColorSuppl = getCssVariableValue('--warning-800');

      commonTokens.successColor = getCssVariableValue('--success');
      commonTokens.successColorHover = getCssVariableValue('--success-600');
      commonTokens.successColorPressed = getCssVariableValue('--success-700');
      commonTokens.successColorSuppl = getCssVariableValue('--success-800');

      commonTokens.textColorBase = getCssVariableValue('--foreground');

      commonTokens.baseColor = getCssVariableValue('--primary-foreground');

      commonTokens.dividerColor = commonTokens.borderColor =
        getCssVariableValue('--border');

      commonTokens.modalColor = commonTokens.popoverColor =
        getCssVariableValue('--popover');

      commonTokens.tableColor = commonTokens.cardColor =
        getCssVariableValue('--card');

      commonTokens.bodyColor = getCssVariableValue('--background');
      commonTokens.invertedColor = getCssVariableValue('--background-deep');

      commonTokens.borderRadius = getCssVariableValue('--radius', false);
    },
    { immediate: true },
  );
  return {
    commonTokens,
  };
}

/**
 * 把项目 CSS 变量映射为 Element Plus 的 `--el-*` 变量，实现主题联动。
 *
 * @remarks
 * 入参：无。返回值：无——与 Antd / Naive 版本不同，本函数**不返回 token 对象**，
 * 而是直接把变量写入文档，Element Plus 组件通过 CSS 变量自动生效。
 *
 * 副作用与生命周期：
 * - 侦听 `preferences.theme`（`immediate: true`，调用即执行一次），
 *   每次变化都调用 `updateCSSVariables` 覆写 id 为 `__ydsz_design_styles__` 的全局 `<style>` 标签，
 *   属于**全局副作用**，多次调用会互相覆盖；
 * - 侦听器未显式停止，应在 `setup` 中调用以便随组件作用域回收；
 * - 依赖 `document.documentElement`，仅浏览器端可用。
 *
 * 明暗适配：Element Plus 的 `light-N` 阶梯在亮色下由浅到深、暗色下需要反转，
 * 因此这里按 `isDark` 分别映射到不同的色阶，保证暗色模式下对比度正确。
 * `--el-mask-color` 单独硬编码，用于修复 ElLoading 遮罩在暗色下过亮的问题。
 */
export function useElementPlusDesignTokens() {
  const { isDark } = usePreferences();
  const rootStyles = getComputedStyle(document.documentElement);

  const getCssVariableValueRaw = (variable: string) => {
    return rootStyles.getPropertyValue(variable);
  };

  const getCssVariableValue = (variable: string, isColor: boolean = true) => {
    const value = getCssVariableValueRaw(variable);
    return isColor ? convertToRgb(`hsl(${value})`) : value;
  };

  watch(
    () => preferences.theme,
    () => {
      const background = getCssVariableValue('--background');
      const border = getCssVariableValue('--border');
      const accent = getCssVariableValue('--accent');

      const variables: Record<string, string> = {
        '--el-bg-color': background,
        '--el-bg-color-overlay': getCssVariableValue('--popover'),
        '--el-bg-color-page': getCssVariableValue('--background-deep'),
        '--el-border-color': border,
        '--el-border-color-dark': border,
        '--el-border-color-extra-light': border,
        '--el-border-color-hover': accent,
        '--el-border-color-light': border,
        '--el-border-color-lighter': border,

        '--el-border-radius-base': getCssVariableValue('--radius', false),
        '--el-color-danger': getCssVariableValue('--destructive-500'),
        '--el-color-danger-dark-2': isDark.value
          ? getCssVariableValue('--destructive-400')
          : getCssVariableValue('--destructive-600'),
        '--el-color-danger-light-3': isDark.value
          ? getCssVariableValue('--destructive-600')
          : getCssVariableValue('--destructive-400'),
        '--el-color-danger-light-5': isDark.value
          ? getCssVariableValue('--destructive-700')
          : getCssVariableValue('--destructive-300'),
        '--el-color-danger-light-7': isDark.value
          ? getCssVariableValue('--destructive-800')
          : getCssVariableValue('--destructive-200'),
        '--el-color-danger-light-8': isDark.value
          ? getCssVariableValue('--destructive-900')
          : getCssVariableValue('--destructive-100'),
        '--el-color-danger-light-9': isDark.value
          ? getCssVariableValue('--destructive-950')
          : getCssVariableValue('--destructive-50'),

        '--el-color-error': getCssVariableValue('--destructive-500'),
        '--el-color-error-dark-2': isDark.value
          ? getCssVariableValue('--destructive-400')
          : getCssVariableValue('--destructive-600'),
        '--el-color-error-light-3': isDark.value
          ? getCssVariableValue('--destructive-600')
          : getCssVariableValue('--destructive-400'),
        '--el-color-error-light-5': isDark.value
          ? getCssVariableValue('--destructive-700')
          : getCssVariableValue('--destructive-300'),
        '--el-color-error-light-7': isDark.value
          ? getCssVariableValue('--destructive-800')
          : getCssVariableValue('--destructive-200'),
        '--el-color-error-light-8': isDark.value
          ? getCssVariableValue('--destructive-900')
          : getCssVariableValue('--destructive-100'),
        '--el-color-error-light-9': isDark.value
          ? getCssVariableValue('--destructive-950')
          : getCssVariableValue('--destructive-50'),

        '--el-color-info-light-5': border,
        '--el-color-info-light-8': border,
        '--el-color-info-light-9': getCssVariableValue('--info'), // getCssVariableValue('--secondary'),

        '--el-color-primary': getCssVariableValue('--primary-500'),
        '--el-color-primary-dark-2': isDark.value
          ? getCssVariableValue('--primary-400')
          : getCssVariableValue('--primary-600'),
        '--el-color-primary-light-3': isDark.value
          ? getCssVariableValue('--primary-600')
          : getCssVariableValue('--primary-400'),
        '--el-color-primary-light-5': isDark.value
          ? getCssVariableValue('--primary-700')
          : getCssVariableValue('--primary-300'),
        '--el-color-primary-light-7': isDark.value
          ? getCssVariableValue('--primary-800')
          : getCssVariableValue('--primary-200'),
        '--el-color-primary-light-8': isDark.value
          ? getCssVariableValue('--primary-900')
          : getCssVariableValue('--primary-100'),
        '--el-color-primary-light-9': isDark.value
          ? getCssVariableValue('--primary-950')
          : getCssVariableValue('--primary-50'),

        '--el-color-success': getCssVariableValue('--success-500'),
        '--el-color-success-dark-2': isDark.value
          ? getCssVariableValue('--success-400')
          : getCssVariableValue('--success-600'),
        '--el-color-success-light-3': isDark.value
          ? getCssVariableValue('--success-600')
          : getCssVariableValue('--success-400'),
        '--el-color-success-light-5': isDark.value
          ? getCssVariableValue('--success-700')
          : getCssVariableValue('--success-300'),
        '--el-color-success-light-7': isDark.value
          ? getCssVariableValue('--success-800')
          : getCssVariableValue('--success-200'),
        '--el-color-success-light-8': isDark.value
          ? getCssVariableValue('--success-900')
          : getCssVariableValue('--success-100'),
        '--el-color-success-light-9': isDark.value
          ? getCssVariableValue('--success-950')
          : getCssVariableValue('--success-50'),

        '--el-color-warning': getCssVariableValue('--warning-500'),
        '--el-color-warning-dark-2': isDark.value
          ? getCssVariableValue('--warning-400')
          : getCssVariableValue('--warning-600'),
        '--el-color-warning-light-3': isDark.value
          ? getCssVariableValue('--warning-600')
          : getCssVariableValue('--warning-400'),
        '--el-color-warning-light-5': isDark.value
          ? getCssVariableValue('--warning-700')
          : getCssVariableValue('--warning-300'),
        '--el-color-warning-light-7': isDark.value
          ? getCssVariableValue('--warning-800')
          : getCssVariableValue('--warning-200'),
        '--el-color-warning-light-8': isDark.value
          ? getCssVariableValue('--warning-900')
          : getCssVariableValue('--warning-100'),
        '--el-color-warning-light-9': isDark.value
          ? getCssVariableValue('--warning-950')
          : getCssVariableValue('--warning-50'),

        '--el-fill-color': getCssVariableValue('--accent'),
        '--el-fill-color-blank': background,
        '--el-fill-color-light': getCssVariableValue('--accent'),
        '--el-fill-color-lighter': getCssVariableValue('--accent-lighter'),

        '--el-fill-color-dark': getCssVariableValue('--accent-dark'),
        '--el-fill-color-darker': getCssVariableValue('--accent-darker'),

        // 解决ElLoading背景色问题
        '--el-mask-color': isDark.value
          ? 'rgba(0,0,0,.8)'
          : 'rgba(255,255,255,.9)',

        '--el-text-color-primary': getCssVariableValue('--foreground'),

        '--el-text-color-regular': getCssVariableValue('--foreground'),
      };

      updateCSSVariables(variables, `__ydsz_design_styles__`);
    },
    { immediate: true },
  );
}
