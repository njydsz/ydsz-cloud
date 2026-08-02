/**
 * form 适配器模块
 *
 * @path main\src\adapter\form.ts
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
 * 初始化 ydsz-form 表单适配器。
 *
 * 注册组件类型映射与内置校验规则（required / selectRequired），需在应用启动时调用一次。
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

/** 封装后的表单组合式函数，已绑定业务组件类型 {@link ComponentType}。 */
const useYDSZForm = useForm<ComponentType>;

export { initSetupYDSZForm, useYDSZForm, z };

/** 表单 Schema 类型，泛型参数已绑定业务组件类型 {@link ComponentType}。 */
export type YDSZFormSchema = FormSchema<ComponentType>;
export type { YDSZFormProps };
