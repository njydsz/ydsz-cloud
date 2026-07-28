/**
 * index 模块
 *
 * @path comm\@core\ui-kit\form-ui\src\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export { setupYDSZForm } from './config';

export type {
  BaseFormComponentType,
  ExtendedFormApi,
  YDSZFormProps,
  FormSchema as YDSZFormSchema,
} from './types';

export * from './use-ydsz-form';
// export { default as YDSZForm } from './ydsz-form.vue';
export * as z from 'zod';
