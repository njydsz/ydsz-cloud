import type {
  FormState,
  GenericObject,
  ResetFormOpts,
  ValidationOptions,
} from 'vee-validate';

import type { Recordable } from '@ydsz-core/typings';

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
