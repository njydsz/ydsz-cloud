import { requestClient } from '#/api/request';

export namespace DeptApi {
  export interface DepartmentVO {
    id: string;
    deptName: string;
    parentId: string;
    parentName?: string;
    sort?: number;
    leader?: string;
    phone?: string;
    email?: string;
    status: number;
    companyId?: string;
    createTime?: string;
    children?: DepartmentVO[];
  }

  export interface DepartmentTreeVO {
    id: string;
    label: string;
    parentId: string;
    children?: DepartmentTreeVO[];
  }

  export interface DepartmentSaveDTO {
    id?: string;
    deptName: string;
    parentId: string;
    sort?: number;
    leader?: string;
    phone?: string;
    email?: string;
    status?: number;
    companyId?: string;
  }
}

/** 查询全部部门列表 */
export function getDeptListApi() {
  return requestClient.get<DeptApi.DepartmentVO[]>('/api/v1/dept/list');
}

/** 查询部门树形结构 */
export function getDeptTreeApi() {
  return requestClient.get<DeptApi.DepartmentTreeVO[]>('/api/v1/dept/tree');
}

/** 根据 ID 查询部门 */
export function getDeptByIdApi(id: string) {
  return requestClient.get<DeptApi.DepartmentVO>(`/api/v1/dept/${id}`);
}

/** 创建部门 */
export function createDeptApi(data: DeptApi.DepartmentSaveDTO) {
  return requestClient.post<string>('/api/v1/dept', data);
}

/** 更新部门 */
export function updateDeptApi(data: DeptApi.DepartmentSaveDTO) {
  return requestClient.put<boolean>('/api/v1/dept', data);
}

/** 删除部门 */
export function deleteDeptApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/dept/${id}`);
}
