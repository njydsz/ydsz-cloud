/**
 * 角色 API 模块（前端）
 *
 * <p>封装角色（{@code ydsz_role}）的 CRUD + 权限分配接口调用，对应后端
 * {@code /api/v1/role/*} 端点。供「用户管理 → 角色管理」使用。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li>{@link getRolePageApi} — 分页查询角色</li>
 *   <li>{@link getRoleListApi} — 全量查询角色（用户分配角色下拉）</li>
 *   <li>{@link createRoleApi} — 创建角色</li>
 *   <li>{@link updateRoleApi} — 更新角色</li>
 *   <li>{@link deleteRoleApi} — 删除角色</li>
 *   <li>{@link assignPermissionsApi} — 分配权限</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace RoleApi {
  /** 角色视图对象 */
  export interface RoleVO {
    id: string;
    roleCode: string;
    roleName: string;
    dataScope?: number;
    sort?: number;
    status: number;
    remark?: string;
    createTime?: string;
  }

  /** 角色分页查询条件 */
  export interface RolePageQuery {
    pageNum?: number;
    pageSize?: number;
    roleName?: string;
    roleCode?: string;
    status?: number;
  }

  /** 角色保存 DTO（创建/更新） */
  export interface RoleSaveDTO {
    id?: string;
    roleCode: string;
    roleName: string;
    dataScope?: number;
    sort?: number;
    status?: number;
    remark?: string;
  }
}

/** 分页查询角色列表 */
export function getRolePageApi(params: RoleApi.RolePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RoleApi.RoleVO[];
  }>('/api/v1/role/page', { params });
}

/** 查询全部角色列表 */
export function getRoleListApi() {
  return requestClient.get<RoleApi.RoleVO[]>('/api/v1/role/list');
}

/** 根据 ID 查询角色 */
export function getRoleByIdApi(id: string) {
  return requestClient.get<RoleApi.RoleVO>(`/api/v1/role/${id}`);
}

/** 创建角色 */
export function createRoleApi(data: RoleApi.RoleSaveDTO) {
  return requestClient.post<string>('/api/v1/role', data);
}

/** 更新角色 */
export function updateRoleApi(data: RoleApi.RoleSaveDTO) {
  return requestClient.put<boolean>('/api/v1/role', data);
}

/** 删除角色 */
export function deleteRoleApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/role/${id}`);
}

/** 分配角色权限 */
export function assignRolePermissionsApi(roleId: string, permissionIds: string[]) {
  return requestClient.post<boolean>(`/api/v1/role/${roleId}/permissions`, {
    permissionIds,
  });
}

/** 查询角色权限 ID 列表 */
export function getRolePermissionsApi(roleId: string) {
  return requestClient.get<string[]>(`/api/v1/role/${roleId}/permissions`);
}
