/**
 * Menu API — 对齐后端 /api/v1/menu/routes
 */
import type { RouteRecordStringComponent } from '@ydsz/types';

import { requestClient } from './request-setup';

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
  return requestClient.get<RouteRecordStringComponent[]>(
    '/api/v1/menu/tree',
  );
}
