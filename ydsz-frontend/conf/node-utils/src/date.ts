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

const dateUtil = dayjs;

export { dateUtil };
