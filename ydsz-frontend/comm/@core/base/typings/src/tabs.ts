/**
 * tabs 模块
 *
 * @path comm\@core\base\typings\src\tabs.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteLocationNormalized } from 'vue-router';

export interface TabDefinition extends RouteLocationNormalized {
  /**
   * 标签页的key
   */
  key?: string;
}
