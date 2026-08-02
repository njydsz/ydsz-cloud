/**
 * date 配置模块
 *
 * @path conf\node-utils\src\date.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';

dayjs.extend(utc);
dayjs.extend(timezone);

dayjs.tz.setDefault('Asia/Shanghai');

/**
 * 已配置好时区的 dayjs 实例。
 *
 * 已启用 utc / timezone 插件并将默认时区设为 Asia/Shanghai，
 * 全项目统一使用该实例以保证日期处理时区一致。
 */
const dateUtil = dayjs;

export { dateUtil };
