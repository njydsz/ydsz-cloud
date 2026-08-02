/**
 * access 路由模块
 *
 * @path main\src\router\access.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
} from '@ydsz/types';

import { generateAccessible } from '@ydsz/access';
import { preferences } from '@ydsz/preferences';

import { ElMessage } from 'element-plus';

import { getAllMenusApi } from '#/api';
import { BasicLayout, IFrameView } from '#/layouts';
import { $t } from '#/locales';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

/**
 * 生成可访问的菜单与动态路由。
 *
 * 基于用户角色与后端返回的菜单树，调用权限库生成最终路由与菜单配置。
 *
 * @param options - 生成选项（含 router、roles、routes 等）
 * @returns 可访问的菜单与路由信息
 */
async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = import.meta.glob('../views/**/*.vue');

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      ElMessage.info(`${$t('common.loadingMenu')}...`);
      return await getAllMenusApi();
    },
    forbiddenComponent,
    layoutMap,
    pageMap,
  });
}

export { generateAccess };
