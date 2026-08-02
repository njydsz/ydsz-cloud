/**
 * use-form-context 模块
 *
 * @path comm\@core\ui-kit\form-ui\src\use-form-context.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ZodRawShape } from 'zod';

import type { ComputedRef } from 'vue';

import type { ExtendedFormApi, FormActions, YDSZFormProps } from './types';

import { computed, unref, useSlots } from 'vue';

import { createContext } from '@ydsz-core/shadcn-ui';
import { isString, mergeWithArrayOverride, set } from '@ydsz-core/shared/utils';

import { useForm } from 'vee-validate';
import { object, ZodIntersection, ZodNumber, ZodObject, ZodString } from 'zod';
import { getDefaultsForSchema } from 'zod-defaults';

type ExtendFormProps = YDSZFormProps & { formApi: ExtendedFormApi };

export const [injectFormProps, provideFormProps] =
  createContext<[ComputedRef<ExtendFormProps> | ExtendFormProps, FormActions]>(
    'YDSZFormProps',
  );

export const [injectComponentRefMap, provideComponentRefMap] =
  createContext<Map<string, unknown>>('ComponentRefMap');

/**
 * 依据表单 schema 推导初始值并创建 vee-validate 表单实例。
 *
 * @remarks
 * 解决的核心问题是「初始值从哪来」。优先级为：
 * 1. schema 中显式声明的 `defaultValue`——用 `Reflect.has` 判断而非真值判断，
 *    因此显式写 `undefined`/`null`/`0`/`''` 都会被尊重；
 * 2. 从 zod 规则推导——`ZodString` 给 `''`、`ZodNumber` 给 `null`
 *    （刻意不用 `0`，否则数字框初始就显示 0，用户无法区分「未填」与「填了 0」），
 *    `ZodObject` 递归推导，`ZodIntersection` 合并左右两侧且左侧优先，其余类型不给默认值；
 * 3. zod schema 上通过 `.default()` 声明的值。
 *
 * 最终用 `mergeWithArrayOverride` 合并，数组类型**整体替换而非按下标合并**，
 * 避免多选字段出现新旧选项混杂的脏数据。字段名支持 `a.b` 嵌套路径，由 `set` 展开为嵌套对象。
 *
 * 重要约束：
 * - 初始值**只在此函数执行时计算一次**。后续动态修改 `props.schema` 不会重算 initialValues，
 *   新增字段的默认值需通过 formApi 主动设置；
 * - 必须在组件 setup 期间调用（内部依赖 `useSlots` 与 `useForm`）；
 * - 当推导结果为空对象时不传 `initialValues` 给 `useForm`，以免覆盖其内部默认行为。
 *
 * @param props - 表单 props，可为普通对象或 computed；内部通过 `unref` 取值，
 *                传 computed 也**不会**建立响应式追踪
 * @returns `form` 为 vee-validate 表单实例；`delegatedSlots` 是需要透传给渲染层的具名插槽名列表
 *          （已剔除 `default`，因为默认插槽由表单组件自身消费）
 */
export function useFormInitial(
  props: ComputedRef<YDSZFormProps> | YDSZFormProps,
) {
  const slots = useSlots();
  const initialValues = generateInitialValues();

  const form = useForm({
    ...(Object.keys(initialValues)?.length ? { initialValues } : {}),
  });

  const delegatedSlots = computed(() => {
    const resultSlots: string[] = [];

    for (const key of Object.keys(slots)) {
      if (key !== 'default') {
        resultSlots.push(key);
      }
    }
    return resultSlots;
  });

  function generateInitialValues() {
    const initialValues: Record<string, any> = {};

    const zodObject: ZodRawShape = {};
    (unref(props).schema || []).forEach((item) => {
      if (Reflect.has(item, 'defaultValue')) {
        set(initialValues, item.fieldName, item.defaultValue);
      } else if (item.rules && !isString(item.rules)) {
        // 检查规则是否适合提取默认值
        const customDefaultValue = getCustomDefaultValue(item.rules);
        zodObject[item.fieldName] = item.rules;
        if (customDefaultValue !== undefined) {
          initialValues[item.fieldName] = customDefaultValue;
        }
      }
    });

    const schemaInitialValues = getDefaultsForSchema(object(zodObject));

    const zodDefaults: Record<string, any> = {};
    for (const key in schemaInitialValues) {
      set(zodDefaults, key, schemaInitialValues[key]);
    }
    return mergeWithArrayOverride(initialValues, zodDefaults);
  }
  // 自定义默认值提取逻辑
  function getCustomDefaultValue(rule: any): any {
    if (rule instanceof ZodString) {
      return ''; // 默认为空字符串
    } else if (rule instanceof ZodNumber) {
      return null; // 默认为 null（避免显示 0）
    } else if (rule instanceof ZodObject) {
      // 递归提取嵌套对象的默认值
      const defaultValues: Record<string, any> = {};
      for (const [key, valueSchema] of Object.entries(rule.shape)) {
        defaultValues[key] = getCustomDefaultValue(valueSchema);
      }
      return defaultValues;
    } else if (rule instanceof ZodIntersection) {
      // 对于交集类型，从schema 提取默认值
      const leftDefaultValue = getCustomDefaultValue(rule._def.left);
      const rightDefaultValue = getCustomDefaultValue(rule._def.right);

      // 如果左右两边都能提取默认值，合并它们
      if (
        typeof leftDefaultValue === 'object' &&
        typeof rightDefaultValue === 'object'
      ) {
        return { ...leftDefaultValue, ...rightDefaultValue };
      }

      // 否则优先使用左边的默认值
      return leftDefaultValue ?? rightDefaultValue;
    } else {
      return undefined; // 其他类型不提供默认值
    }
  }

  return {
    delegatedSlots,
    form,
  };
}
