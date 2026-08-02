/**
 * dependencies 模块
 *
 * @path comm\@core\ui-kit\form-ui\src\form-render\dependencies.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  FormItemDependencies,
  FormSchemaRuleType,
  MaybeComponentProps,
} from '../types';

import { computed, ref, watch } from 'vue';

import { isBoolean, isFunction } from '@ydsz-core/shared/utils';

import { useFormValues } from 'vee-validate';

import { injectRenderFormProps } from './context';

/**
 * 计算单个表单项的联动状态（显隐、禁用、必填、动态 props 与规则）。
 *
 * @remarks
 * 每次触发字段变化时，先调用 `resetConditionState()` 把所有状态**重置为默认值**再重新计算，
 * 因此联动结果是「全量覆盖」而非增量叠加——回调没返回的状态会回到初始值，不会保留上一次的结果。
 *
 * 求值顺序经过精心编排，且带**短路**行为：
 * 1. 先算 `if`，为假立即 return——DOM 都不渲染了，后续状态计算无意义，可省去开销；
 * 2. 再算 `show`，为假同样 return——注意这意味着字段被 CSS 隐藏时，
 *    `disabled` / `required` / 动态 `rules` 都**不会被计算**，仍保持重置后的默认值。
 *    若隐藏字段原本有必填规则，此时规则实际已失效，这是隐藏字段不阻塞提交的原因；
 * 3. 最后依次计算 componentProps、rules、disabled、required，并执行 `trigger` 副作用钩子。
 *
 * 其他注意点：
 * - 所有回调均 `await`，支持异步；但 watch 回调本身**不做并发控制**，
 *   快速连续变更时后发请求可能先返回，造成状态回退，异步逻辑需自行处理竞态；
 * - watch 配置为 `deep: true` + `immediate: true`：深度监听保证对象/数组类型的触发字段
 *   内部变更也能感知，代价是大表单下比较开销较高；立即执行保证首屏即应用联动；
 * - 未配置 `dependencies` 或 `triggerFields` 为空时直接返回，联动完全不生效。
 *
 * @param getDependencies - 返回当前联动配置的 getter；写成函数而非直接传值，
 *                          是为了让 schema 动态替换时 watch 能重新求值
 * @returns 六个响应式状态，供字段渲染组件直接绑定
 * @throws 当脱离 `<YDSZForm>` 上下文调用（拿不到 vee-validate 表单值）时抛出 Error
 */
export default function useDependencies(
  getDependencies: () => FormItemDependencies | undefined,
) {
  const values = useFormValues();

  const formRenderProps = injectRenderFormProps();

  // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
  const formApi = formRenderProps.form!;

  if (!values) {
    throw new Error('useDependencies should be used within <YDSZForm>');
  }

  const isIf = ref(true);
  const isDisabled = ref(false);
  const isShow = ref(true);
  const isRequired = ref(false);
  const dynamicComponentProps = ref<MaybeComponentProps>({});
  const dynamicRules = ref<FormSchemaRuleType>();

  const triggerFieldValues = computed(() => {
    // 该字段可能会被多个字段触发
    const triggerFields = getDependencies()?.triggerFields ?? [];
    return triggerFields.map((dep) => {
      return values.value[dep];
    });
  });

  const resetConditionState = () => {
    isDisabled.value = false;
    isIf.value = true;
    isShow.value = true;
    isRequired.value = false;
    dynamicRules.value = undefined;
    dynamicComponentProps.value = {};
  };

  watch(
    [triggerFieldValues, getDependencies],
    async ([_values, dependencies]) => {
      if (!dependencies || !dependencies?.triggerFields?.length) {
        return;
      }
      resetConditionState();
      const {
        componentProps,
        disabled,
        if: whenIf,
        required,
        rules,
        show,
        trigger,
      } = dependencies;

      // 1. 优先判断if，如果if为false，则不渲染dom，后续判断也不再执行
      const formValues = values.value;

      if (isFunction(whenIf)) {
        isIf.value = !!(await whenIf(formValues, formApi));
        // 不渲染
        if (!isIf.value) return;
      } else if (isBoolean(whenIf)) {
        isIf.value = whenIf;
        if (!isIf.value) return;
      }

      // 2. 判断show，如果show为false，则隐藏
      if (isFunction(show)) {
        isShow.value = !!(await show(formValues, formApi));
        if (!isShow.value) return;
      } else if (isBoolean(show)) {
        isShow.value = show;
        if (!isShow.value) return;
      }

      if (isFunction(componentProps)) {
        dynamicComponentProps.value = await componentProps(formValues, formApi);
      }

      if (isFunction(rules)) {
        dynamicRules.value = await rules(formValues, formApi);
      }

      if (isFunction(disabled)) {
        isDisabled.value = !!(await disabled(formValues, formApi));
      } else if (isBoolean(disabled)) {
        isDisabled.value = disabled;
      }

      if (isFunction(required)) {
        isRequired.value = !!(await required(formValues, formApi));
      }

      if (isFunction(trigger)) {
        await trigger(formValues, formApi);
      }
    },
    { deep: true, immediate: true },
  );

  return {
    dynamicComponentProps,
    dynamicRules,
    isDisabled,
    isIf,
    isRequired,
    isShow,
  };
}
