/**
 * merge 工具函数模块
 *
 * @path comm\@core\base\shared\src\utils\merge.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createDefu } from 'defu';

export { createDefu as createMerge, defu as merge } from 'defu';

export const mergeWithArrayOverride = createDefu((originObj, key, updates) => {
  if (Array.isArray(originObj[key]) && Array.isArray(updates)) {
    originObj[key] = updates;
    return true;
  }
});
