/**
 * Element Plus 表单适配器
 * <p>将 ydsz-common-ui 的表单 Schema 适配为 Element Plus ElForm 组件格式。
 * <p>供 ydsz-form / ydsz-modal / ydsz-drawer 内嵌表单使用。
 *
 * @path apps\workflow-web\src\adapter\form.ts
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

const useYDSZForm = useForm<ComponentType>;

export { initSetupYDSZForm, useYDSZForm, z };

export type YDSZFormSchema = FormSchema<ComponentType>;
export type { YDSZFormProps };
