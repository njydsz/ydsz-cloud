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
