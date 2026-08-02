/**
 * types 模块
 *
 * @path comm\@core\ui-kit\form-ui\src\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { FieldOptions, FormContext, GenericObject } from 'vee-validate';
import type { ZodTypeAny } from 'zod';

import type { Component, HtmlHTMLAttributes, Ref } from 'vue';

import type { YDSZButtonProps } from '@ydsz-core/shadcn-ui';
import type { ClassType, MaybeComputedRef } from '@ydsz-core/typings';

import type { FormApi } from './form-api';

/**
 * 表单项的标签排布方式。
 *
 * @remarks
 * `horizontal` 表示 label 与控件左右排列（label 宽度由 `labelWidth` 决定），
 * `vertical` 表示 label 在控件上方换行显示。窄屏下若仍用 horizontal，
 * label 会挤压控件可用宽度，移动端建议切到 vertical。
 */
export type FormLayout = 'horizontal' | 'vertical';

/**
 * 表单可用控件的类型标识。
 *
 * @remarks
 * 联合类型末尾的 `Record<never, never> & string` 是一个「保留字面量提示」的技巧：
 * 它让该类型在接受任意字符串的同时，IDE 仍能补全前面列出的内置控件名。
 * 因此适配器注册的自定义组件名可以直接传入而无需扩展本类型，
 * 代价是**拼写错误不会在编译期报错**，只会在运行时表现为控件渲染不出来。
 */
export type BaseFormComponentType =
  | 'DefaultButton'
  | 'PrimaryButton'
  | 'YDSZCheckbox'
  | 'YDSZInput'
  | 'YDSZInputPassword'
  | 'YDSZPinInput'
  | 'YDSZSelect'
  | (Record<never, never> & string);

type Breakpoints = '2xl:' | '3xl:' | '' | 'lg:' | 'md:' | 'sm:' | 'xl:';

type GridCols = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13;

/**
 * 表单外层栅格容器的 class 类型，用于控制整个表单分几列。
 *
 * @remarks
 * 通过模板字面量类型约束出 `grid-cols-1` ~ `grid-cols-13` 及其带断点前缀的形式
 * （如 `md:grid-cols-2`），使 IDE 能直接补全合法栅格值，避免手写 typo 导致样式静默失效。
 * 同样保留了任意字符串的兜底分支，可传入项目自定义 class，但会失去校验能力。
 */
export type WrapperClassType =
  | `${Breakpoints}grid-cols-${GridCols}`
  | (Record<never, never> & string);

/**
 * 单个表单项在栅格中的占位 class 类型。
 *
 * @remarks
 * 在 {@link WrapperClassType} 的基础上追加了 `cols-span-*`、`cols-start-*`、`cols-end-*`
 * 三类定位能力，用于让某个字段跨列或对齐到指定列，典型场景是备注、富文本这类需要独占整行的字段
 * （`cols-span-full`）。
 *
 * 注意这里的 class 名不带 Tailwind 原生的 `col-` 前缀写法，是项目内约定的样式名，
 * 需要与表单渲染层的样式表配套使用，直接照搬 Tailwind 文档中的类名不会生效。
 */
export type FormItemClassType =
  | `${Breakpoints}cols-end-${'auto' | GridCols}`
  | `${Breakpoints}cols-span-${'auto' | 'full' | GridCols}`
  | `${Breakpoints}cols-start-${'auto' | GridCols}`
  | (Record<never, never> & string)
  | WrapperClassType;

/**
 * 透传给 vee-validate `useField` 的字段级配置。
 *
 * @remarks
 * 在 vee-validate 原生 `FieldOptions` 之外显式补充了四个校验时机开关，
 * 用于按字段粒度控制「何时触发校验」：过早校验（如 input 即校验）会让用户刚开始输入就看到红字，
 * 体验差；过晚（仅 submit）又难以即时纠错。默认策略由表单层统一给定，此处用于个别字段做覆盖。
 *
 * 整体被 `Partial` 包裹，任意项均可省略，省略时回落到 vee-validate 或表单层的默认行为。
 */
export type FormFieldOptions = Partial<
  FieldOptions & {
    validateOnBlur?: boolean;
    validateOnChange?: boolean;
    validateOnInput?: boolean;
    validateOnModelUpdate?: boolean;
  }
>;

/**
 * 从表单 schema 中抽取出的字段「形状」描述，供初始值构造与校验器生成使用。
 *
 * @remarks
 * 它是 {@link FormSchema} 的精简投影：只保留生成 initialValues 和 zod schema 所必需的信息，
 * 剥离了组件、label 等渲染相关字段，避免渲染层改动影响数据层逻辑。
 */
export interface FormShape {
  /**
   * 默认值
   *
   * @remarks
   * 表单初始化及 reset 后回填的值。未设置时字段初始为 `undefined`，
   * 对于 Select 等受控组件可能触发「非受控转受控」告警，建议显式给出。
   */
  default?: any;
  /**
   * 字段名
   *
   * @remarks
   * 对应表单数据对象中的键，支持 `a.b` 形式的嵌套路径，必须在同一表单内唯一。
   */
  fieldName: string;
  /**
   * 是否必填
   *
   * @remarks
   * 仅代表由 `rules` 简写（如 `'required'`）推导出的必填态，用于渲染 label 前的星号标记；
   * 真正的校验仍以 `rules` 为准，二者不同步时以 `rules` 的结果为最终校验依据。
   */
  required?: boolean;
  /**
   * 该字段的 zod 校验规则；为空表示不参与校验，提交时原样透传。
   */
  rules?: ZodTypeAny;
}

/**
 * 可传给表单控件的 prop 名集合。
 *
 * @remarks
 * 由于控件是运行时动态决定的，无法静态得知其确切 props，这里以「原生 HTML 属性 +
 * 三个最高频的业务 prop（options / placeholder / title）」作为补全提示的基础，
 * 再用任意字符串兜底以容纳各 UI 库的私有 prop。
 */
export type MaybeComponentPropKey =
  | 'options'
  | 'placeholder'
  | 'title'
  | keyof HtmlHTMLAttributes
  | (Record<never, never> & string);

/**
 * 传递给具体表单控件的 props 对象。
 *
 * @remarks
 * 值类型为 `any` 是刻意为之——控件类型直到运行时才确定，无法做静态约束。
 * 代价是这里**完全没有类型保护**，prop 名或值写错只会在运行时表现为控件行为异常，
 * 编写时建议对照目标控件的文档。
 */
export type MaybeComponentProps = { [K in MaybeComponentPropKey]?: any };

/**
 * 表单操作句柄，即 vee-validate 的表单上下文。
 *
 * @remarks
 * 起别名而非直接暴露 vee-validate 类型，是为了收敛外部依赖：
 * 后续若更换校验库，只需改动此处的类型指向，依赖方（如联动回调的第二个入参）无需调整。
 * 通过它可以读写字段值、手动触发校验、设置错误信息等。
 */
export type FormActions = FormContext<GenericObject>;

/**
 * 可自定义渲染的内容类型，用于 label、help、description、suffix 等插槽位。
 *
 * @remarks
 * 传字符串时按纯文本渲染；传函数时**每次重渲染都会被调用**，
 * 因此函数内不要做耗时计算或产生副作用。函数可返回组件（渲染为节点）或字符串。
 *
 * 注意：字符串分支不做 HTML 转义处理之外的加工，需要富文本请返回组件而非拼接 HTML 串。
 */
export type CustomRenderType = (() => Component | string) | string;

/**
 * 字段校验规则的声明形式。
 *
 * @remarks
 * 支持三种写法，按表达能力递增：
 * 1. 内置简写字符串 `'required'` / `'selectRequired'`——由适配器在
 *    {@link YDSZFormAdapterOptions.defineRules} 中注册具体实现，二者区别在于错误文案
 *    （「请输入」vs「请选择」）；
 * 2. 任意自定义规则名字符串——需适配器提前注册，未注册时该字段校验被静默跳过；
 * 3. zod schema——直接给出完整校验逻辑，表达能力最强。
 *
 * 传 `null` 表示显式声明「无校验」，语义上区别于 `undefined`（未配置，可能被上层默认配置覆盖）。
 */
export type FormSchemaRuleType =
  | 'required'
  | 'selectRequired'
  | null
  | (Record<never, never> & string)
  | ZodTypeAny;

type FormItemDependenciesCondition<T = boolean | PromiseLike<boolean>> = (
  value: Partial<Record<string, any>>,
  actions: FormActions,
) => T;

type FormItemDependenciesConditionWithRules = (
  value: Partial<Record<string, any>>,
  actions: FormActions,
) => FormSchemaRuleType | PromiseLike<FormSchemaRuleType>;

type FormItemDependenciesConditionWithProps = (
  value: Partial<Record<string, any>>,
  actions: FormActions,
) => MaybeComponentProps | PromiseLike<MaybeComponentProps>;

/**
 * 表单项联动配置：声明当前字段如何随其他字段的值变化而改变。
 *
 * @remarks
 * 所有联动回调都以「当前全量表单值 + 表单操作句柄」为入参，返回目标状态，
 * 由渲染层负责应用。回调**仅在 {@link triggerFields} 中列出的字段发生变化时执行**，
 * 而非任意字段变化都执行——漏配 `triggerFields` 是联动不生效的最常见原因。
 *
 * 回调均支持返回 Promise（可发起远程校验/查询），但要注意：
 * 多次快速变更会并发触发，返回顺序不保证与触发顺序一致，
 * 存在竞态时需在回调内自行做防抖或版本号校验。
 */
export interface FormItemDependencies {
  /**
   * 动态计算控件 props，返回值会与静态 `componentProps` 合并，同名键以此处结果为准。
   *
   * @remarks
   * 典型用途是级联下拉：上级选中后在此拉取并返回下级的 `options`。
   */
  componentProps?: FormItemDependenciesConditionWithProps;
  /**
   * 是否禁用。可传静态布尔值，或按表单值动态计算的函数。
   *
   * @remarks
   * 禁用只影响交互，字段值仍会随表单一并提交。若希望值也一并排除，请改用 {@link if}。
   */
  disabled?: boolean | FormItemDependenciesCondition;
  /**
   * 是否渲染该字段（条件为假时从 DOM 中移除）。
   *
   * @remarks
   * 与 {@link show} 的关键差异：`if` 为假时字段被销毁，其**值会从表单数据中移除**，
   * 也不再参与校验；再次显示时恢复为默认值，原先输入的内容丢失。
   * 需要保留用户已填内容的场景请用 `show`。
   */
  if?: boolean | FormItemDependenciesCondition;
  /**
   * 动态计算是否必填，返回 `true` 时显示必填标记并施加必填校验。
   */
  required?: FormItemDependenciesCondition;
  /**
   * 动态计算校验规则，返回值语义同 {@link FormSchemaRuleType}。
   *
   * @remarks
   * 返回结果会**整体替换**静态 `rules` 而非合并；返回 `null` 可临时取消该字段的校验。
   */
  rules?: FormItemDependenciesConditionWithRules;
  /**
   * 是否显示（通过 CSS 隐藏，DOM 仍保留）。
   *
   * @remarks
   * 隐藏期间字段值**依旧保留且参与校验**——若隐藏的是必填字段，会出现「校验不通过却看不到错误项」
   * 的情况，此时应改用 {@link if} 或同步放开必填。
   */
  show?: boolean | FormItemDependenciesCondition;
  /**
   * 通用副作用钩子：只要 {@link triggerFields} 中任一字段变化就会执行，无返回值。
   *
   * @remarks
   * 用于上述声明式能力覆盖不到的场景，例如联动清空下级字段、按选项拉取远程数据。
   * 由于是纯副作用，回调内通过 `actions` 修改其他字段可能再次触发联动，
   * 需自行避免形成循环。
   */
  trigger?: FormItemDependenciesCondition<void>;
  /**
   * 触发字段
   *
   * @remarks
   * 依赖的字段名列表，是本对象内所有回调的**唯一触发源**，必填。
   * 只有这些字段的值变化才会重新计算联动；填写的是字段名而非 label。
   */
  triggerFields: string[];
}

type ComponentProps =
  | ((
      value: Partial<Record<string, any>>,
      actions: FormActions,
    ) => MaybeComponentProps)
  | MaybeComponentProps;

/**
 * 表单项的通用后备配置。
 *
 * @remarks
 * 作用是消除重复：整表统一的 label 宽度、禁用态、样式等在此声明一次即可，
 * 无需在每个 {@link FormSchema} 里复写。
 *
 * **优先级规则**：字段级配置 &gt; 本对象配置。合并按属性逐项覆盖（浅合并），
 * 因此字段级只要声明了某个 key，本对象中同名 key 的值即完全失效，不做深度合并。
 *
 * 修改本对象会影响表单内**所有**字段，属于全局性变更，谨慎用于个别字段的特殊需求。
 */
export interface FormCommonConfig {
  /**
   * 在Label后显示一个冒号
   */
  colon?: boolean;
  /**
   * 所有表单项的props
   */
  componentProps?: ComponentProps;
  /**
   * 所有表单项的控件样式
   */
  controlClass?: string;
  /**
   * 所有表单项的禁用状态
   * @default false
   */
  disabled?: boolean;
  /**
   * 是否禁用所有表单项的change事件监听
   * @default true
   */
  disabledOnChangeListener?: boolean;
  /**
   * 是否禁用所有表单项的input事件监听
   * @default true
   */
  disabledOnInputListener?: boolean;
  /**
   * 所有表单项的空状态值,默认都是undefined，naive-ui的空状态值是null
   */
  emptyStateValue?: null | undefined;
  /**
   * 所有表单项的控件样式
   * @default {}
   */
  formFieldProps?: FormFieldOptions;
  /**
   * 所有表单项的栅格布局，支持函数形式
   * @default ""
   */
  formItemClass?: (() => string) | string;
  /**
   * 隐藏所有表单项label
   * @default false
   */
  hideLabel?: boolean;
  /**
   * 是否隐藏必填标记
   * @default false
   */
  hideRequiredMark?: boolean;
  /**
   * 所有表单项的label样式
   * @default ""
   */
  labelClass?: string;
  /**
   * 所有表单项的label宽度
   */
  labelWidth?: number;
  /**
   * 所有表单项的model属性名
   * @default "modelValue"
   */
  modelPropName?: string;
  /**
   * 所有表单项的wrapper样式
   */
  wrapperClass?: string;
}

type RenderComponentContentType = (
  value: Partial<Record<string, any>>,
  api: FormActions,
) => Record<string, any>;

/**
 * 表单提交回调。
 *
 * @remarks
 * **仅在校验全部通过后**才会被调用，因此回调内无需再做必填等基础校验。
 * 入参是经过 `fieldMappingTime`、`arrayToStringFields` 等后处理的最终值，
 * 而非控件的原始值。
 *
 * 返回 Promise 时表单会等待其 resolve，期间提交按钮保持 loading，可借此防重复提交；
 * 若回调内抛出异常或 Promise reject，loading 状态由表单负责结束，但**不会自动提示错误**，
 * 需调用方自行捕获处理。
 *
 * @param values - 经过归一化处理的表单值
 */
export type HandleSubmitFn = (
  values: Record<string, any>,
) => Promise<void> | void;

/**
 * 表单重置回调。
 *
 * @remarks
 * 在表单值**已经被重置为默认值之后**触发，因此入参是重置后的值而非重置前的旧值；
 * 需要旧值请在回调外自行缓存。常用于重置后重新拉取列表数据。
 *
 * @param values - 重置完成后的表单值
 */
export type HandleResetFn = (
  values: Record<string, any>,
) => Promise<void> | void;

/**
 * 区间时间字段的拆分映射规则。
 *
 * @remarks
 * 用于解决「UI 上是一个日期区间选择器，接口却要求两个独立字段」的常见错配。
 * 每条规则是一个三元组：
 * 1. 第 0 项：区间控件绑定的源字段名，其值形如 `[start, end]`；
 * 2. 第 1 项：拆分后的两个目标字段名 `[startFieldName, endFieldName]`；
 * 3. 第 2 项（可选）：格式化方式——传字符串按该 dayjs 模板格式化两端；
 *    传 `[startFormat, endFormat]` 分别指定两端模板（如开始取 `00:00:00`、结束取 `23:59:59`）；
 *    传函数则完全自定义转换；传 `null` 或省略表示不格式化，原样输出。
 *
 * 提交时源字段会被**从结果中移除**，只保留拆分出的两个目标字段。
 * 源字段为空时，两个目标字段会被置空而非省略。
 */
export type FieldMappingTime = [
  string,
  [string, string],
  (
    | ((value: any, fieldName: string) => any)
    | [string, string]
    | null
    | string
  )?,
][];

/**
 * 需要在提交时把数组值拼接为字符串的字段配置。
 *
 * @remarks
 * 面向后端只接受逗号分隔字符串、前端控件却产出数组的场景（多选、级联选择器等）。
 * 支持三种书写形式，可混用：
 * - `'fieldName'`：单字段，使用默认分隔符 `,`；
 * - `['a', 'b', '|']`：简单数组形式，**最后一个元素若被识别为分隔符则不作为字段名**，
 *   因此当字段名本身可能与分隔符混淆时，请改用下面的嵌套形式以消除歧义；
 * - `[['a', 'b'], '|']`：嵌套形式，字段名与分隔符边界明确，推荐使用。
 *
 * 转换只在提交阶段发生，不影响表单内部的响应式值，回显时仍需自行把字符串拆回数组。
 */
export type ArrayToStringFields = Array<
  | [string[], string?] // 嵌套数组格式，可选分隔符
  | string // 单个字段，使用默认分隔符
  | string[] // 简单数组格式，最后一个元素可以是分隔符
>;

/**
 * 单个表单项的完整声明，是「配置驱动表单」的最小描述单元。
 *
 * @remarks
 * 继承 {@link FormCommonConfig}，因此可在字段级覆盖任意通用配置，覆盖为浅合并。
 * 一份 `FormSchema[]` 即可完整描述一张表单的结构、校验与联动，
 * 渲染顺序与数组顺序一致。
 */
export interface FormSchema<
  T extends BaseFormComponentType = BaseFormComponentType,
> extends FormCommonConfig {
  /**
   * 组件
   *
   * @remarks
   * 可传已注册的控件名（走 `componentMap` 查表），也可直接传入 Vue 组件对象。
   * 传字符串时若名称未注册，该字段会渲染为空白且不报错，排查时优先核对注册表。
   */
  component: Component | T;
  /**
   * 组件参数
   *
   * @remarks
   * 传对象为静态 props；传函数则可依据当前表单值动态计算，
   * 函数在每次表单值变化时都会执行，应保持轻量且无副作用。
   */
  componentProps?: ComponentProps;
  /**
   * 默认值
   *
   * @remarks
   * 同时作为表单初始值与 reset 后的回填值。注意它优先级高于控件自身的默认值。
   */
  defaultValue?: any;
  /**
   * 依赖
   *
   * @remarks
   * 声明本字段随其他字段变化的联动行为，详见 {@link FormItemDependencies}。
   */
  dependencies?: FormItemDependencies;
  /**
   * 描述
   *
   * @remarks
   * 渲染在控件下方的辅助说明文字，常驻显示，适合放填写规则；
   * 与 {@link help} 的区别是后者通常为悬浮提示，不占布局空间。
   */
  description?: CustomRenderType;
  /**
   * 字段名
   *
   * @remarks
   * 表单数据对象中的键，支持 `a.b` 嵌套路径，同一表单内必须唯一；
   * 重复会导致两个控件绑定同一个值而相互覆盖。
   */
  fieldName: string;
  /**
   * 帮助信息
   *
   * @remarks
   * 一般渲染为 label 旁的问号图标 + 悬浮气泡，不占据布局空间。
   */
  help?: CustomRenderType;
  /**
   * 表单项
   *
   * @remarks
   * 字段标签内容。省略时不渲染 label，但仍会占据 label 列的宽度以保持栅格对齐；
   * 需要完全不占位请配合 `hideLabel`。
   */
  label?: CustomRenderType;
  /**
   * 自定义控件内部插槽内容。
   *
   * @remarks
   * 返回一个「插槽名 → 渲染函数」的对象，用于向控件内部传递具名插槽，
   * 例如给 Select 定制 option 渲染。返回值在每次表单值变化时重新计算。
   */
  // 自定义组件内部渲染
  renderComponentContent?: RenderComponentContentType;
  /**
   * 字段规则
   *
   * @remarks
   * 静态校验规则，可被 `dependencies.rules` 动态覆盖，取值语义见 {@link FormSchemaRuleType}。
   */
  rules?: FormSchemaRuleType;
  /**
   * 后缀
   *
   * @remarks
   * 渲染在控件右侧的附加内容，如单位、快捷操作按钮。它位于控件外部，
   * 不会成为控件的一部分，因此不影响控件取值。
   */
  suffix?: CustomRenderType;
}

/**
 * 传递给单个字段渲染组件的 props。
 *
 * @remarks
 * 在 {@link FormSchema} 基础上追加了一个**已解析完成**的 `required` 标记：
 * schema 中的必填态可能来自 `rules` 简写或 `dependencies.required` 动态计算，
 * 渲染层不应重复推导，而是直接消费此处的结果。
 */
export interface FormFieldProps extends FormSchema {
  /**
   * 该字段当前是否必填，由上层综合静态 rules 与动态依赖计算得出，仅用于渲染必填标记。
   */
  required?: boolean;
}

/**
 * 表单渲染器（FormRender）的 props，负责「把 schema 画成 DOM」这一层。
 *
 * @remarks
 * 它是纯渲染层，**不持有表单实例**：`form` 由外部传入，因此同一份 schema 可被多个
 * 表单实例复用。与之相对，{@link YDSZFormProps} 是面向业务的上层封装，
 * 会自行创建表单实例并屏蔽 `componentMap` 等底层细节。
 * 业务代码通常不直接使用本类型。
 */
export interface FormRenderProps<
  T extends BaseFormComponentType = BaseFormComponentType,
> {
  /**
   * 表单字段数组映射字符串配置 默认使用","
   */
  arrayToStringFields?: ArrayToStringFields;
  /**
   * 是否展开，在showCollapseButton=true下生效
   */
  collapsed?: boolean;
  /**
   * 折叠时保持行数
   * @default 1
   */
  collapsedRows?: number;
  /**
   * 是否触发resize事件
   * @default false
   */
  collapseTriggerResize?: boolean;
  /**
   * 表单项通用后备配置，当子项目没配置时使用这里的配置，子项目配置优先级高于此配置
   */
  commonConfig?: FormCommonConfig;
  /**
   * 紧凑模式（移除表单每一项底部为校验信息预留的空间）
   */
  compact?: boolean;
  /**
   * 组件v-model事件绑定
   */
  componentBindEventMap?: Partial<Record<BaseFormComponentType, string>>;
  /**
   * 组件集合
   */
  componentMap: Record<BaseFormComponentType, Component>;
  /**
   * 表单字段映射到时间格式
   */
  fieldMappingTime?: FieldMappingTime;
  /**
   * 表单实例
   */
  form?: FormContext<GenericObject>;
  /**
   * 表单项布局
   */
  layout?: FormLayout;
  /**
   * 表单定义
   */
  schema?: FormSchema<T>[];

  /**
   * 是否显示展开/折叠
   */
  showCollapseButton?: boolean;
  /**
   * 格式化日期
   */

  /**
   * 表单栅格布局
   * @default "grid-cols-1"
   */
  wrapperClass?: WrapperClassType;
}

/**
 * 表单操作按钮（提交 / 重置）的配置。
 *
 * @remarks
 * 继承 {@link YDSZButtonProps}，故按钮的尺寸、类型、loading 等原生能力均可直接透传；
 * 索引签名的存在意味着**多余的属性不会被类型系统拦截**，会原样透传到按钮组件，
 * 拼错 prop 名时不会有编译错误，只表现为配置不生效。
 */
export interface ActionButtonOptions extends YDSZButtonProps {
  [key: string]: any;
  /**
   * 按钮文案。支持传 ref 或 getter，以便在切换语言时自动更新；
   * 传普通字符串则固定不变，国际化场景下会出现切换语言后文案不刷新的问题。
   */
  content?: MaybeComputedRef<string>;
  /**
   * 是否显示该按钮，默认显示。设为 `false` 时按钮不渲染，
   * 与设置 `disabled` 的区别是后者仍占据布局空间。
   */
  show?: boolean;
}

/**
 * 业务侧使用的表单组件 props，是 form-ui 对外的主入口类型。
 *
 * @remarks
 * 相较 {@link FormRenderProps}，此处 `Omit` 掉了 `componentMap`、`componentBindEventMap`
 * 与 `form` 三项：前两者由适配器（见 {@link YDSZFormAdapterOptions}）在应用启动时全局注册，
 * 后者由组件内部创建，业务无需也不应关心，从而让调用方只聚焦「字段长什么样、提交做什么」。
 *
 * 在此基础上补充了操作按钮布局与提交/重置/值变更回调等业务能力。
 */
export interface YDSZFormProps<
  T extends BaseFormComponentType = BaseFormComponentType,
> extends Omit<
  FormRenderProps<T>,
  'componentBindEventMap' | 'componentMap' | 'form'
> {
  /**
   * 操作按钮是否反转（提交按钮前置）
   */
  actionButtonsReverse?: boolean;
  /**
   * 操作按钮组的样式
   * newLine: 在新行显示。rowEnd: 在行内显示，靠右对齐（默认）。inline: 使用grid默认样式
   */
  actionLayout?: 'inline' | 'newLine' | 'rowEnd';
  /**
   * 操作按钮组显示位置，默认靠右显示
   */
  actionPosition?: 'center' | 'left' | 'right';
  /**
   * 表单操作区域class
   */
  actionWrapperClass?: ClassType;
  /**
   * 表单字段数组映射字符串配置 默认使用","
   */
  arrayToStringFields?: ArrayToStringFields;

  /**
   * 表单字段映射
   */
  fieldMappingTime?: FieldMappingTime;
  /**
   * 表单重置回调
   */
  handleReset?: HandleResetFn;
  /**
   * 表单提交回调
   */
  handleSubmit?: HandleSubmitFn;
  /**
   * 表单值变化回调
   */
  handleValuesChange?: (
    values: Record<string, any>,
    fieldsChanged: string[],
  ) => void;
  /**
   * 重置按钮参数
   */
  resetButtonOptions?: ActionButtonOptions;

  /**
   * 验证失败时是否自动滚动到第一个错误字段
   * @default false
   */
  scrollToFirstError?: boolean;

  /**
   * 是否显示默认操作按钮
   * @default true
   */
  showDefaultActions?: boolean;

  /**
   * 提交按钮参数
   */
  submitButtonOptions?: ActionButtonOptions;

  /**
   * 是否在字段值改变时提交表单
   * @default false
   */
  submitOnChange?: boolean;

  /**
   * 是否在回车时提交表单
   * @default false
   */
  submitOnEnter?: boolean;
}

/**
 * 在 {@link FormApi} 基础上附加响应式订阅能力的表单句柄，是业务实际拿到的 API 类型。
 *
 * @remarks
 * `FormApi` 本身是普通类实例，其内部状态无法直接在模板中响应式消费；
 * 这里补充的 `useStore` 用于把状态桥接为 Vue 的只读 ref。
 *
 * `useStore` 接受一个可选 selector 以订阅状态切片，**强烈建议传入**：
 * 不传则订阅整个 state，任意字段变化都会触发重渲染，大表单下性能损耗明显。
 * selector 应保持纯函数且返回稳定引用，否则每次比较都判定为变化，等同于没有优化。
 * 返回值为只读 ref，直接赋值修改不会同步回表单，需通过 `FormApi` 的方法更新。
 */
export type ExtendedFormApi = FormApi & {
  useStore: <T = NoInfer<YDSZFormProps>>(
    selector?: (state: NoInfer<YDSZFormProps>) => T,
  ) => Readonly<Ref<T>>;
};

/**
 * 表单适配器的全局初始化选项，用于把 form-ui 对接到具体的 UI 组件库。
 *
 * @remarks
 * 由 `setupYDSZForm` 在应用启动阶段消费，**全局只应配置一次**。
 * 其存在意义是让 form-ui 与具体 UI 库解耦：不同组件库的 v-model prop 名、
 * 空值表示、必填判定方式各不相同，这些差异全部在此收敛。
 */
export interface YDSZFormAdapterOptions<
  T extends BaseFormComponentType = BaseFormComponentType,
> {
  /**
   * 控件行为的适配配置。
   */
  config?: {
    /**
     * 默认的 v-model prop 名，未在 `modelPropNameMap` 中特别指定的控件都用它，
     * 通常为 `'modelValue'`；对接 Vue 2 风格组件库时可能需要改为 `'value'`。
     */
    baseModelPropName?: string;
    /**
     * 是否全局禁止监听控件的 change 事件。
     *
     * @remarks
     * 部分组件库的 change 与 v-model 更新重复触发，会导致校验执行两次，
     * 此开关用于规避；关闭后依赖 change 才能更新的控件将无法正常取值。
     */
    disabledOnChangeListener?: boolean;
    /**
     * 是否全局禁止监听控件的 input 事件，动机同 `disabledOnChangeListener`，
     * 主要用于避免输入过程中的高频校验。
     */
    disabledOnInputListener?: boolean;
    /**
     * 清空字段时写入的「空值」。
     *
     * @remarks
     * 该值必须与 UI 库的约定一致，否则清空后控件仍显示旧值或出现受控告警：
     * 多数库用 `undefined`，naive-ui 等则要求 `null`。
     */
    emptyStateValue?: null | undefined;
    /**
     * 按控件类型覆盖 v-model prop 名，用于处理个别不遵循通用约定的控件
     * （如 Checkbox 用 `checked`、Upload 用 `fileList`）。
     */
    modelPropNameMap?: Partial<Record<T, string>>;
  };
  /**
   * 内置校验规则简写的具体实现。
   *
   * @remarks
   * 对应 {@link FormSchemaRuleType} 中的 `'required'` / `'selectRequired'` 字符串写法。
   * 若不注册，schema 中使用这些简写的字段将**不会产生任何校验**且无告警。
   */
  defineRules?: {
    /**
     * 输入类控件的必填校验。返回 `true` 表示通过，返回字符串表示不通过并作为错误文案。
     */
    required?: (
      value: any,
      params: any,
      ctx: Record<string, any>,
    ) => boolean | string;
    /**
     * 选择类控件的必填校验。
     *
     * @remarks
     * 与 `required` 分开是因为二者的「空」判定不同：选择类的空值可能是 `[]` 或 `null`，
     * 直接套用输入类的非空字符串判断会误判；错误文案也需为「请选择」。
     */
    selectRequired?: (
      value: any,
      params: any,
      ctx: Record<string, any>,
    ) => boolean | string;
  };
}
