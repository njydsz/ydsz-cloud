/**
 * menu API 接口定义
 *
 * @path main\src\api\core\menu.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteRecordStringComponent } from '@ydsz/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户可访问的菜单树（动态路由）
 */
export async function getAllMenusApi() {
  return requestClient.get<RouteRecordStringComponent[]>(
    '/api/v1/menu/routes',
  );
}

/**
 * 获取全部菜单树（管理用）
 */
export async function getMenuTreeApi() {
  return requestClient.get<RouteRecordStringComponent[]>('/api/v1/menu/tree');
}
