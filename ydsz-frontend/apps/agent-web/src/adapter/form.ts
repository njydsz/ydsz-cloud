/**
 * form 适配器模块
 *
 * @path apps\agent-web\src\adapter\form.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  YDSZFormSchema as FormSchema,
  YDSZFormProps,
} from '@ydsz/common-ui';

import type { ComponentType } from './component';

import { setupYDSZForm, useYDSZForm as useForm, z } from '@ydsz/common-ui';
import { $t } from '@ydsz/locales';

/**
 * 初始化 ydsz-form 适配器：绑定组件类型并注册全局表单校验规则。
 *
 * 需在应用启动时调用一次；通过 {@link z} 复用统一校验能力，避免各页面重复声明。
 */
async function initSetupYDSZForm() {
  setupYDSZForm<ComponentType>({
    config: {
      modelPropNameMap: {
        Upload: 'fileList',
        CheckboxGroup: 'model-value',
      },
    },
    defineRules: {
      required: (value, _params, ctx) => {
        if (value === undefined || value === null || value.length === 0) {
          return $t('ui.formRules.required', [ctx.label]);
        }
        return true;
      },
      selectRequired: (value, _params, ctx) => {
        if (value === undefined || value === null) {
          return $t('ui.formRules.selectRequired', [ctx.label]);
        }
        return true;
      },
    },
  });
}

/** 绑定 ComponentType 的 useYDSZForm 组合式函数，供表单页面统一引入。 */
const useYDSZForm = useForm<ComponentType>;

export { initSetupYDSZForm, useYDSZForm, z };

/** 基于 agent-web 组件类型约束的表单 Schema 类型别名。 */
export type YDSZFormSchema = FormSchema<ComponentType>;
export type { YDSZFormProps };
