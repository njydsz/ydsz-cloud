import { requestClient } from '#/api/request';

export namespace UserApi {
  export interface UserAccountVO {
    id: string;
    username: string;
    realName: string;
    nickname?: string;
    avatar?: string;
    email?: string;
    phone?: string;
    gender?: number;
    status: number;
    deptId?: string;
    deptName?: string;
    postId?: string;
    postName?: string;
    companyId?: string;
    companyName?: string;
    lastLoginTime?: string;
    createTime?: string;
  }

  export interface UserAccountPageQuery {
    pageNum?: number;
    pageSize?: number;
    username?: string;
    realName?: string;
    phone?: string;
    email?: string;
    status?: number;
    deptId?: string;
    companyId?: string;
  }

  export interface UserAccountCreateDTO {
    username: string;
    password: string;
    realName: string;
    nickname?: string;
    email?: string;
    phone?: string;
    gender?: number;
    deptId?: string;
    postId?: string;
    companyId?: string;
    status?: number;
  }

  export interface UserAccountUpdateDTO {
    id: string;
    realName?: string;
    nickname?: string;
    email?: string;
    phone?: string;
    gender?: number;
    deptId?: string;
    postId?: string;
    companyId?: string;
    status?: number;
  }

  export interface ChangePasswordDTO {
    userId: string;
    oldPassword: string;
    newPassword: string;
  }

  export interface ResetPasswordDTO {
    userId: string;
    newPassword: string;
  }
}

/** 分页查询用户列表 */
export function getUserPageApi(params: UserApi.UserAccountPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: UserApi.UserAccountVO[];
  }>('/api/v1/user/page', { params });
}

/** 查询全部用户列表 */
export function getUserListApi() {
  return requestClient.get<UserApi.UserAccountVO[]>('/api/v1/user/list');
}

/** 根据 ID 查询用户 */
export function getUserByIdApi(id: string) {
  return requestClient.get<UserApi.UserAccountVO>(`/api/v1/user/${id}`);
}

/** 创建用户 */
export function createUserApi(data: UserApi.UserAccountCreateDTO) {
  return requestClient.post<string>('/api/v1/user', data);
}

/** 更新用户信息 */
export function updateUserApi(data: UserApi.UserAccountUpdateDTO) {
  return requestClient.put<boolean>('/api/v1/user', data);
}

/** 删除用户 */
export function deleteUserApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/user/${id}`);
}

/** 修改密码 */
export function changePasswordApi(data: UserApi.ChangePasswordDTO) {
  return requestClient.post<boolean>('/api/v1/user/change-password', data);
}

/** 重置密码（管理员） */
export function resetPasswordApi(data: UserApi.ResetPasswordDTO) {
  return requestClient.post<boolean>('/api/v1/user/reset-password', data);
}

/** 分配用户角色 */
export function assignUserRolesApi(userId: string, roleIds: string[]) {
  return requestClient.post<boolean>(`/api/v1/user/${userId}/roles`, {
    roleIds,
  });
}

/** 查询用户角色 ID 列表 */
export function getUserRolesApi(userId: string) {
  return requestClient.get<string[]>(`/api/v1/user/${userId}/roles`);
}
