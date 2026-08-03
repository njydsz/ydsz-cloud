/**
 * 菜单 API 模块（前端）
 * <p>封装菜单（{@code ydsz_menu}）CRUD 接口，对应后端 {@code /api/v1/userinfo/menu/*} 端点。
 * <p>支持菜单树、权限标识、组件路径、图标、排序。
 * <p>供「组织架构 → 菜单管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace MenuApi {
  /** 菜单视图对象，children 表示子菜单树 */
  export interface MenuVO {
    id: string;
    menuName: string;
    parentId: string;
    menuType: number;
    path?: string;
    component?: string;
    icon?: string;
    permission?: string;
    sort?: number;
    visible?: number;
    status: number;
    children?: MenuVO[];
  }

  /** 菜单树节点（仅含树形渲染所需字段） */
  export interface MenuTreeVO {
    id: string;
    label: string;
    parentId: string;
    children?: MenuTreeVO[];
  }

  /** 菜单创建/更新请求参数 */
  export interface MenuSaveDTO {
    id?: string;
    menuName: string;
    parentId: string;
    menuType: number;
    path?: string;
    component?: string;
    icon?: string;
    permission?: string;
    sort?: number;
    visible?: number;
    status?: number;
  }
}

/** 查询全部菜单列表 */
export function getMenuListApi() {
  return requestClient.get<MenuApi.MenuVO[]>('/api/v1/menu/list');
}

/** 查询菜单树形结构 */
export function getMenuTreeApi() {
  return requestClient.get<MenuApi.MenuTreeVO[]>('/api/v1/menu/tree');
}

/** 根据 ID 查询菜单 */
export function getMenuByIdApi(id: string) {
  return requestClient.get<MenuApi.MenuVO>(`/api/v1/menu/${id}`);
}

/** 创建菜单 */
export function createMenuApi(data: MenuApi.MenuSaveDTO) {
  return requestClient.post<string>('/api/v1/menu', data);
}

/** 更新菜单 */
export function updateMenuApi(data: MenuApi.MenuSaveDTO) {
  return requestClient.put<boolean>('/api/v1/menu', data);
}

/** 删除菜单 */
export function deleteMenuApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/menu/${id}`);
}
