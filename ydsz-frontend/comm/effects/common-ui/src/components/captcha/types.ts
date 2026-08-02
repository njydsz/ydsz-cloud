/**
 * types 模块
 *
 * @path comm\effects\common-ui\src\components\captcha\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CSSProperties } from 'vue';

import type { ClassType } from '@ydsz/types';

/**
 * 点选验证码的单次点击原始数据。
 *
 * @remarks
 * 坐标以**验证码图片元素左上角**为原点、单位为 px（已做 `Math.ceil` 取整），
 * 并非视口坐标，因此可直接提交给后端与出题坐标比对。
 */
export interface CaptchaData {
  /**
   * 相对图片左边缘的横坐标（px）
   */
  x: number;
  /**
   * 相对图片上边缘的纵坐标（px）
   */
  y: number;
  /**
   * 点击发生时的时间戳（`Date.now()`，毫秒），后端可据此做人机行为分析
   */
  t: number;
}

/**
 * 点选验证码中带序号的点位，用于按顺序校验点击结果。
 *
 * @remarks
 * 序号由组件内部按点击先后自动生成，界面上以气泡数字 `i + 1` 展示。
 */
export interface CaptchaPoint extends CaptchaData {
  /**
   * 点击顺序索引，从 0 开始递增；清空点位后重新计数
   */
  i: number;
}

/**
 * 点选验证码卡片（外壳）的 Props，只负责图片展示与尺寸布局，不含交互逻辑。
 */
export interface PointSelectionCaptchaCardProps {
  /**
   * 验证码图片
   */
  captchaImage: string;
  /**
   * 验证码图片高度
   * @default '220px'
   */
  height?: number | string;
  /**
   * 水平内边距
   * @default '12px'
   */
  paddingX?: number | string;
  /**
   * 垂直内边距
   * @default '16px'
   */
  paddingY?: number | string;
  /**
   * 标题
   * @default '请按图依次点击'
   */
  title?: string;
  /**
   * 验证码图片宽度
   * @default '300px'
   */
  width?: number | string;
}

/**
 * 点选验证码组件的 Props，在卡片布局基础上追加提示区与确认交互配置。
 *
 * @remarks
 * 交互约定：
 * - 点击图片即累加点位并触发 `click` 事件，组件**不做数量上限控制**，需由业务侧在 `confirm` 中判断；
 * - `showConfirm` 为 `false` 时确定按钮不渲染，`confirm` 事件永远不会触发，
 *   业务需自行在 `click` 回调里判断点位数量并提交；
 * - `confirm` 事件第二个参数是 `clear` 回调，校验失败后由业务侧调用它清空已选点位；
 * - `hintImage` 与 `hintText` 至少提供一个，二者皆空时组件会打印 `console.warn`（不阻断渲染），
 *   同时提供时优先渲染 `hintImage`。
 */
export interface PointSelectionCaptchaProps extends PointSelectionCaptchaCardProps {
  /**
   * 是否展示确定按钮；为 false 时不触发 confirm 事件
   * @default false
   */
  showConfirm?: boolean;
  /**
   * 提示图片地址（要求按序点击的文字图），优先级高于 hintText
   * @default ''
   */
  hintImage?: string;
  /**
   * 提示文本，仅在 hintImage 为空时渲染
   * @default ''
   */
  hintText?: string;
}

/**
 * 滑块验证码（横向拖动条）组件的 Props。
 *
 * @remarks
 * 交互约定：拖动到轨道右端即判定通过，通过后组件锁定（再次按下不响应），
 * 需通过模板 ref 调用 `resume()` 才能重置（见 {@link SliderCaptchaActionType}）。
 * 当 `isSlot` 为 `true` 时，本组件退化为「纯拖动条」：不自行判定通过，
 * 只向外抛 `start` / `move` / `end` 事件，由宿主组件（旋转、拼图验证码）决定成败并回写 `v-model`。
 */
export interface SliderCaptchaProps {
  /** 根元素追加的 class，与内置样式经 `cn()` 合并 */
  class?: ClassType;
  /**
   * @description 滑块的样式
   * @default {}
   */
  actionStyle?: CSSProperties;

  /**
   * @description 滑块条的样式
   * @default {}
   */
  barStyle?: CSSProperties;

  /**
   * @description 内容的样式
   * @default {}
   */
  contentStyle?: CSSProperties;

  /**
   * @description 组件的样式
   * @default {}
   */
  wrapperStyle?: CSSProperties;

  /**
   * @description 是否作为插槽使用，用于联动组件，可参考旋转校验组件
   * @default false
   */
  isSlot?: boolean;

  /**
   * @description 验证成功的提示
   * @default '验证通过'
   */
  successText?: string;

  /**
   * @description 提示文字
   * @default '请按住滑块拖动'
   */
  text?: string;
}

/**
 * 旋转图片验证码组件的 Props。
 *
 * @remarks
 * 交互约定：图片加载完成（`onload`）时随机旋转 `[minDegree, maxDegree)` 之间的角度，
 * 用户拖动底部滑块把图片转正，松手时若与原始随机角度的偏差小于 `diffDegree` 则判定通过。
 * 判定失败会以 300ms 动画把图片转回随机角度并展示失败提示；点击图片可重置本轮验证。
 * `minDegree > maxDegree` 时仅打印 `console.warn`，不会抛异常但随机角度会失真。
 */
export interface SliderRotateCaptchaProps {
  /**
   * @description 允许的最大角度偏差，松手时误差小于该值即判定通过；值越大越宽松
   * @default 20
   */
  diffDegree?: number;

  /**
   * @description 图片容器的边长（px，宽高相同），同时作为拖动距离换算旋转角度的分母，为 0 时拖动不生效
   * @default 260
   */
  imageSize?: number;

  /**
   * @description 图片容器的额外样式，会覆盖由 imageSize 推导出的宽高
   * @default {}
   */
  imageWrapperStyle?: CSSProperties;

  /**
   * @description 初始随机旋转角度的上界（不含）
   * @default 300
   */
  maxDegree?: number;

  /**
   * @description 初始随机旋转角度的下界（含）；大于 maxDegree 时仅告警不报错
   * @default 120
   */
  minDegree?: number;

  /**
   * @description 待旋转图片的地址，建议使用正方形图片以保证旋转视觉效果
   */
  src?: string;
  /**
   * @description 滑块默认提示文本，为空时回退到 i18n 文案 `ui.captcha.sliderRotateDefaultTip`
   */
  defaultTip?: string;
}

/**
 * 拼图（滑动还原）验证码组件的 Props。
 *
 * @remarks
 * 交互约定：组件用两张 canvas 分别绘制「带缺口的底图」与「可拖动的拼块」，
 * 拖动滑块横向平移拼块，松手时拼块与缺口的横向偏差不超过 `diffDistance` 即判定通过。
 * 修改 `canvasWidth` / `squareLength` 等尺寸会改变缺口位置的随机范围，需与后端出题参数保持一致。
 */
export interface SliderTranslateCaptchaProps {
  /**
   * @description 底图 canvas 的宽度（px），同时决定拼块可拖动的最大距离
   * @default 420
   */
  canvasWidth?: number;
  /**
   * @description 拼图的高度
   * @default 280
   */
  canvasHeight?: number;
  /**
   * @description 切块上正方形的长度
   * @default 42
   */
  squareLength?: number;
  /**
   * @description 切块上圆形的半径
   * @default 10
   */
  circleRadius?: number;
  /**
   * @description 拼图底图地址，图片会被拉伸绘制到 canvasWidth × canvasHeight
   */
  src?: string;
  /**
   * @description 允许的最大横向偏差（px），松手时拼块与缺口误差不超过该值即通过；值越大越宽松
   * @default 3
   */
  diffDistance?: number;
  /**
   * @description 滑块默认提示文本，为空时回退到 i18n 默认文案
   */
  defaultTip?: string;
}

/**
 * 各类验证码「验证通过」事件（`success`）的载荷。
 *
 * @remarks
 * 仅在验证成功时派发，失败不会触发该事件，因此 `isPassing` 实际恒为 `true`，
 * 保留该字段是为了兼容旧调用方的解构写法。
 */
export interface CaptchaVerifyPassingData {
  /** 是否通过；success 事件中恒为 true */
  isPassing: boolean;
  /** 本次验证耗时（秒），已由 `toFixed(1)` 格式化为字符串，如 `'1.4'` */
  time: number | string;
}

/**
 * 滑块验证码通过模板 ref 暴露的实例方法集合。
 *
 * @remarks
 * 验证通过后滑块会锁定在右端，宿主组件必须显式调用 `resume()` 才能开始下一轮验证，
 * 典型场景是后端二次校验失败后重置滑块。
 */
export interface SliderCaptchaActionType {
  /** 重置滑块：清空计时与通过状态，并以 300ms 动画把滑块归位到左端 */
  resume: () => void;
}

/**
 * 滑块拖动过程（`move`）与结束（`end`）事件的载荷。
 *
 * @remarks
 * 供旋转、拼图等「以滑块为插槽」的验证码换算图片旋转角度或拼块位移；
 * 命名中的 `VerifyPassing` 为历史遗留，实际与是否通过无关。
 */
export interface SliderRotateVerifyPassingData {
  /** 触发本次移动的原始鼠标或触摸事件，可用于阻止默认行为 */
  event: MouseEvent | TouchEvent;
  /** 按下时指针与滑块左边缘的距离（px），用于把 pageX 换算为滑块位移 */
  moveDistance: number;
  /** 滑块当前相对轨道左端的位移（px），可能为负值或超出轨道宽度，使用前需自行钳制 */
  moveX: number;
}
