/**
 * form-api 模块
 *
 * @path comm\@core\ui-kit\form-ui\src\form-api.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  FormState,
  GenericObject,
  ResetFormOpts,
  ValidationOptions,
} from 'vee-validate';

import type { Recordable } from '@ydsz-core/typings';

import type { Ref } from 'vue';

import type { FormActions, FormSchema, YDSZFormProps } from './types';

import { toRaw } from 'vue';

import { Store } from '@ydsz-core/shared/store';
import {
  bindMethods,
  createMerge,
  isDate,
  isDayjsObject,
  isFunction,
  isObject,
  mergeWithArrayOverride,
  StateHandler,
} from '@ydsz-core/shared/utils';

import { FormScrollHelper } from './form-scroll-helper';
import { FormValueTransformer } from './form-value-transformer';

function getDefaultState(): YDSZFormProps {
  return {
    actionWrapperClass: '',
    collapsed: false,
    collapsedRows: 1,
    collapseTriggerResize: false,
    commonConfig: {},
    handleReset: undefined,
    handleSubmit: undefined,
    handleValuesChange: undefined,
    layout: 'horizontal',
    resetButtonOptions: {},
    schema: [],
    scrollToFirstError: false,
    showCollapseButton: false,
    showDefaultActions: true,
    submitButtonOptions: {},
    submitOnChange: false,
    submitOnEnter: false,
    wrapperClass: 'grid-cols-1',
  };
}

/**
 * 表单的命令式操作句柄，承载配置状态与对 vee-validate 实例的所有操作。
 *
 * @remarks
 * 设计目标是让业务代码脱离模板 ref，用 `formApi.xxx()` 的方式完成取值、赋值、校验、提交。
 * 内部维护两份状态，职责不同：`store` 存放表单**配置**（schema、布局、按钮等），
 * `form` 是挂载后注入的 vee-validate 实例，存放**字段值与校验态**。
 *
 * 使用时必须注意的几点：
 *
 * 1. **实例先于组件创建**。`new FormApi()` 时组件尚未挂载，此时 `form` 只是空对象。
 *    因此所有涉及字段值的异步方法内部都会先 `await` 挂载完成信号再执行，
 *    调用方可在 setup 阶段安全地提前调用而不必等 `onMounted`；
 *    但若组件**始终未挂载**，这些 Promise 会一直挂起不 resolve，表现为「调用无响应」而非报错。
 *
 * 2. **卸载后不可复用**。`unmount()` 会重置表单值、清空最近提交值并把挂载标记置回 false，
 *    此后再调用取值类方法会重新进入等待挂载状态。
 *
 * 3. **状态更新为合并语义**。`setState` 使用 `mergeWithArrayOverride`，
 *    对象深合并、数组整体替换。这意味着传入部分字段即可局部更新，
 *    但想「清空某个数组配置」必须显式传空数组。
 *
 * 4. **schema 缩减会连带清值**。当新 schema 的长度小于旧值时，
 *    被移除字段的表单值会被自动置为 `undefined`，避免提交时携带已删除字段的脏数据。
 *    注意该判断以**长度变小**为触发条件，等长替换字段时不会清值。
 *
 * 5. **校验失败不抛异常**。`validate` 系列方法在失败时打印 error 日志、
 *    按需滚动到首个错误字段，并把结果作为返回值交回调用方判断 `valid`，
 *    不要用 try/catch 捕获校验失败。
 *
 * 6. **`merge` 返回的是代理而非本实例**。多表单联合提交时，
 *    通过返回的代理调用 `submitAllForm()` 才能收集全部表单；
 *    其中任一表单校验不通过，对应结果为 `undefined` 而**不会中断**其他表单，
 *    合并模式下该表单的值将静默缺失，调用方需自行校验完整性。
 *
 * @example
 * ```ts
 * const [Form, formApi] = useYDSZForm({ schema });
 * const values = await formApi.getValues();
 * await formApi.validateAndSubmitForm();
 * ```
 */

/**
 * 表单 API 实例。
 *
 * @remarks
 * 由 {@link useYDSZForm} 创建并随表单实例返回，提供命令式操作表单的能力：
 * 获取/设置表单值、触发校验、提交、合并多表单等。
 * 所有方法均为异步 Promise 风格，校验失败不抛异常（详见 useYDSZForm 文档第 5 点）。
 */
export class FormApi {
  public form = {} as FormActions;
  isMounted = false;

  public state: null | YDSZFormProps = null;
  stateHandler: StateHandler;

  public store: Store<YDSZFormProps>;

  private componentRefMap: Map<string, unknown> = new Map();

  private latestSubmissionValues: null | Recordable<any> = null;

  private prevState: null | YDSZFormProps = null;

  private scrollHelper: FormScrollHelper;

  private valueTransformer: FormValueTransformer;

  constructor(options: YDSZFormProps = {}) {
    const { ...storeState } = options;

    const defaultState = getDefaultState();

    this.store = new Store<YDSZFormProps>(
      {
        ...defaultState,
        ...storeState,
      },
      {
        onUpdate: () => {
          this.prevState = this.state;
          this.state = this.store.state;
          this.updateState();
        },
      },
    );

    this.state = this.store.state;
    this.stateHandler = new StateHandler();
    this.scrollHelper = new FormScrollHelper(this.componentRefMap);
    this.valueTransformer = new FormValueTransformer(() => this.state);
    bindMethods(this);
  }

  /**
   * 获取字段组件实例
   * @param fieldName 字段名
   * @returns 组件实例
   */
  getFieldComponentRef<T = any>(fieldName: string): T | undefined {
    return this.scrollHelper.getFieldComponentRef<T>(fieldName);
  }

  /**
   * 获取当前聚焦的字段，如果没有聚焦的字段则返回undefined
   */
  getFocusedField() {
    return this.scrollHelper.getFocusedField();
  }

  getLatestSubmissionValues() {
    return this.latestSubmissionValues || {};
  }

  getState() {
    return this.state;
  }

  async getValues<T = Recordable<any>>() {
    const form = await this.getForm();
    return (form.values ? this.valueTransformer.handleRangeTimeValue(form.values) : {}) as T;
  }

  async isFieldValid(fieldName: string) {
    const form = await this.getForm();
    return form.isFieldValid(fieldName);
  }

  merge(formApi: FormApi) {
    const chain = [this, formApi];
    const proxy = new Proxy(formApi, {
      get(target: any, prop: any) {
        if (prop === 'merge') {
          return (nextFormApi: FormApi) => {
            chain.push(nextFormApi);
            return proxy;
          };
        }
        if (prop === 'submitAllForm') {
          return async (needMerge: boolean = true) => {
            try {
              const results = await Promise.all(
                chain.map(async (api) => {
                  const validateResult = await api.validate();
                  if (!validateResult.valid) {
                    return;
                  }
                  const rawValues = toRaw((await api.getValues()) || {});
                  return rawValues;
                }),
              );
              if (needMerge) {
                const mergedResults = Object.assign({}, ...results);
                return mergedResults;
              }
              return results;
            } catch (error) {
              console.error('Validation error:', error);
            }
          };
        }
        return target[prop];
      },
    });

    return proxy;
  }

  mount(formActions: FormActions, componentRefMap: Map<string, unknown>) {
    if (!this.isMounted) {
      Object.assign(this.form, formActions);
      this.stateHandler.setConditionTrue();
      this.setLatestSubmissionValues({
        ...toRaw(this.valueTransformer.handleRangeTimeValue(this.form.values)),
      });
      this.componentRefMap = componentRefMap;
      this.scrollHelper.updateRefMap(componentRefMap);
      this.isMounted = true;
    }
  }

  /**
   * 根据字段名移除表单项
   * @param fields 字段名列表
   */
  async removeSchemaByFields(fields: string[]) {
    const fieldSet = new Set(fields);
    const schema = this.state?.schema ?? [];

    const filterSchema = schema.filter((item) => !fieldSet.has(item.fieldName));

    this.setState({
      schema: filterSchema,
    });
  }

  /**
   * 重置表单
   */
  async resetForm(
    state?: Partial<FormState<GenericObject>> | undefined,
    opts?: Partial<ResetFormOpts>,
  ) {
    const form = await this.getForm();
    return form.resetForm(state, opts);
  }

  async resetValidate() {
    const form = await this.getForm();
    const fields = Object.keys(form.errors.value);
    fields.forEach((field) => {
      form.setFieldError(field, undefined);
    });
  }

  /**
   * 滚动到第一个错误字段
   * @param errors 验证错误对象
   */
  scrollToFirstError(errors: Record<string, any> | string) {
    this.scrollHelper.scrollToFirstError(errors);
  }

  async setFieldValue(field: string, value: any, shouldValidate?: boolean) {
    const form = await this.getForm();
    form.setFieldValue(field, value, shouldValidate);
  }

  setLatestSubmissionValues(values: null | Recordable<any>) {
    this.latestSubmissionValues = { ...toRaw(values) };
  }

  setState(
    stateOrFn:
      | ((prev: YDSZFormProps) => Partial<YDSZFormProps>)
      | Partial<YDSZFormProps>,
  ) {
    if (isFunction(stateOrFn)) {
      this.store.setState((prev) => {
        return mergeWithArrayOverride(stateOrFn(prev), prev);
      });
    } else {
      this.store.setState((prev) => mergeWithArrayOverride(stateOrFn, prev));
    }
  }

  /**
   * 设置表单值
   * @param fields record
   * @param filterFields 过滤不在schema中定义的字段 默认为true
   * @param shouldValidate
   */
  async setValues(
    fields: Record<string, any>,
    filterFields: boolean = true,
    shouldValidate: boolean = false,
  ) {
    const form = await this.getForm();
    if (!filterFields) {
      form.setValues(fields, shouldValidate);
      return;
    }

    const fieldMergeFn = createMerge((obj, key, value) => {
      if (key in obj) {
        obj[key] =
          !Array.isArray(obj[key]) &&
          isObject(obj[key]) &&
          !isDayjsObject(obj[key]) &&
          !isDate(obj[key])
            ? fieldMergeFn(obj[key], value)
            : value;
      }
      return true;
    });
    const filteredFields = fieldMergeFn(fields, form.values);
    this.valueTransformer.handleStringToArrayFields(filteredFields);
    form.setValues(filteredFields, shouldValidate);
  }

  async submitForm(e?: Event) {
    e?.preventDefault();
    e?.stopPropagation();
    const form = await this.getForm();
    await form.submitForm();
    const rawValues = toRaw(await this.getValues());
    this.valueTransformer.handleArrayToStringFields(rawValues);
    await this.state?.handleSubmit?.(rawValues);

    return rawValues;
  }

  unmount() {
    this.form?.resetForm?.();
    this.latestSubmissionValues = null;
    this.isMounted = false;
    this.stateHandler.reset();
  }

  updateSchema(schema: Partial<FormSchema>[]) {
    const updated: Partial<FormSchema>[] = [...schema];
    const allItemsHaveFieldName = updated.every(
      (item) => Reflect.has(item, 'fieldName') && item.fieldName,
    );

    if (!allItemsHaveFieldName) {
      console.error(
        'All items in the schema array must have a valid `fieldName` property to be updated',
      );
      return;
    }
    const currentSchema = [...(this.state?.schema ?? [])];

    const updatedMap: Record<string, any> = {};

    updated.forEach((item) => {
      if (item.fieldName) {
        updatedMap[item.fieldName] = item;
      }
    });

    currentSchema.forEach((schema, index) => {
      const updatedData = updatedMap[schema.fieldName];
      if (updatedData) {
        currentSchema[index] = mergeWithArrayOverride(
          updatedData,
          schema,
        ) as FormSchema;
      }
    });
    this.setState({ schema: currentSchema });
  }

  async validate(opts?: Partial<ValidationOptions>) {
    const form = await this.getForm();

    const validateResult = await form.validate(opts);

    if (Object.keys(validateResult?.errors ?? {}).length > 0) {
      console.error('validate error', validateResult?.errors);

      if (this.state?.scrollToFirstError) {
        this.scrollHelper.scrollToFirstError(validateResult.errors);
      }
    }
    return validateResult;
  }

  async validateAndSubmitForm() {
    const form = await this.getForm();
    const { valid, errors } = await form.validate();
    if (!valid) {
      if (this.state?.scrollToFirstError) {
        this.scrollHelper.scrollToFirstError(errors);
      }
      return;
    }
    return await this.submitForm();
  }

  async validateField(fieldName: string, opts?: Partial<ValidationOptions>) {
    const form = await this.getForm();
    const validateResult = await form.validateField(fieldName, opts);

    if (Object.keys(validateResult?.errors ?? {}).length > 0) {
      console.error('validate error', validateResult?.errors);

      if (this.state?.scrollToFirstError) {
        this.scrollHelper.scrollToFirstError(fieldName);
      }
    }
    return validateResult;
  }

  private async getForm() {
    if (!this.isMounted) {
      await this.stateHandler.waitForCondition();
    }
    if (!this.form?.meta) {
      throw new Error('<YDSZForm /> is not mounted');
    }
    return this.form;
  }

  private updateState() {
    const currentSchema = this.state?.schema ?? [];
    const prevSchema = this.prevState?.schema ?? [];
    if (currentSchema.length < prevSchema.length) {
      const currentFields = new Set(
        currentSchema.map((item) => item.fieldName),
      );
      const deletedSchema = prevSchema.filter(
        (item) => !currentFields.has(item.fieldName),
      );
      for (const schema of deletedSchema) {
        this.form?.setFieldValue?.(schema.fieldName, undefined);
      }
    }
  }
}
