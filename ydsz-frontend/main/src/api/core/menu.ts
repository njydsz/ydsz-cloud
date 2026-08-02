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
 * 获取当前用户可访问的菜单树（用于生成动态路由）。
 *
 * @returns 菜单路由数组，结构见 {@link RouteRecordStringComponent}
 */
export async function getAllMenusApi() {
  return requestClient.get<RouteRecordStringComponent[]>(
    '/api/v1/menu/routes',
  );
}

/**
 * 获取全量菜单树（后台管理用，不含权限过滤）。
 *
 * @returns 完整菜单树数组
 */
export async function getMenuTreeApi() {
  return requestClient.get<RouteRecordStringComponent[]>('/api/v1/menu/tree');
}
