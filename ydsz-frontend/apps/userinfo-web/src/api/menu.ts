import { requestClient } from '#/api/request';

export namespace MenuApi {
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

  export interface MenuTreeVO {
    id: string;
    label: string;
    parentId: string;
    children?: MenuTreeVO[];
  }

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
