/**
 * date 工具函数模块
 *
 * @path comm\@core\base\shared\src\utils\date.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import dayjs from 'dayjs';

/**
 * 将时间戳或日期字符串格式化为指定格式的日期文本。
 *
 * @remarks
 * 解析失败时**不会向外抛异常**：内部捕获后仅打印 `console.error`，并**原样返回入参**。
 * 这意味着返回值可能是 `number` 而非 `string`，调用方若直接拼接渲染需自行兜底，
 * 不能假定返回值一定符合 `format` 的形状。
 *
 * 入参交由 dayjs 解析，不同浏览器对非 ISO 8601 字符串（如 `'2023/1/1 上午10点'`）
 * 的宽容度不一致，跨端场景建议统一传毫秒时间戳。
 *
 * @param time - 毫秒时间戳，或任意可被 dayjs 解析的日期字符串
 * @param format - dayjs 输出模板，默认 `'YYYY-MM-DD'`
 * @returns 格式化后的日期字符串；解析失败时返回原始入参
 *
 * @example
 * ```ts
 * formatDate(1700000000000);         // '2023-11-15'
 * formatDate('2023-11-15', 'MM/DD'); // '11/15'
 * formatDate('not-a-date');          // 'not-a-date'（并打印 error）
 * ```
 */
export function formatDate(time: number | string, format = 'YYYY-MM-DD') {
  try {
    const date = dayjs(time);
    if (!date.isValid()) {
      throw new Error('Invalid date');
    }
    return date.format(format);
  } catch (error) {
    console.error(`Error formatting date: ${error}`);
    return time;
  }
}

/**
 * 按项目统一的「年-月-日 时:分:秒」格式格式化日期时间。
 *
 * @remarks
 * 仅是 {@link formatDate} 固定模板的语义化封装，抽出该函数是为了避免各业务页面
 * 手写模板字符串导致的格式漂移（如混用 `HH` 与 `hh`、遗漏秒）。
 * 容错行为完全继承 {@link formatDate}：解析失败不抛错，原样返回入参。
 *
 * @param time - 毫秒时间戳，或任意可被 dayjs 解析的日期字符串
 * @returns 形如 `'2023-11-15 03:33:20'` 的字符串；解析失败时返回原始入参
 */
export function formatDateTime(time: number | string) {
  return formatDate(time, 'YYYY-MM-DD HH:mm:ss');
}

/**
 * 判断值是否为原生 `Date` 实例，并收窄类型。
 *
 * @remarks
 * 基于 `instanceof` 实现，因此**无法跨 Realm 生效**：来自 iframe、Web Worker 或
 * Node vm 上下文的 Date 对象会被判定为 `false`。同时它只校验「是不是 Date」，
 * 不校验「是不是有效日期」——`new Date('x')`（Invalid Date）仍返回 `true`，
 * 需要有效性判断请额外检查 `!Number.isNaN(value.getTime())`。
 *
 * @param value - 任意待检测值
 * @returns 是 Date 实例返回 `true`，同时把类型收窄为 `Date`
 */
export function isDate(value: any): value is Date {
  return value instanceof Date;
}

/**
 * 判断值是否为 dayjs 实例，并收窄类型。
 *
 * @remarks
 * 表单组件（如日期选择器）回传的值可能是 dayjs 对象、Date 或字符串三者之一，
 * 提交前需借助本函数与 {@link isDate} 做分支归一化，否则序列化到后端会得到
 * `{}` 或非预期格式。
 *
 * 内部走 `dayjs.isDayjs`，其判定依赖实例原型链，若项目中混入了**不同版本/多份副本**
 * 的 dayjs，跨副本的实例会被判定为 `false`，需保证依赖收敛到同一份 dayjs。
 *
 * @param value - 任意待检测值
 * @returns 是 dayjs 实例返回 `true`，同时把类型收窄为 `dayjs.Dayjs`
 */
export function isDayjsObject(value: any): value is dayjs.Dayjs {
  return dayjs.isDayjs(value);
}
