import { requestClient } from '#/api/request';

export namespace CategoryApi {
  export interface CategoryVO {
    id: string;
    categoryCode: string;
    categoryName: string;
    sort: number;
    status: number;
    createTime: string;
  }

  export interface CategoryPageQuery {
    pageNum?: number;
    pageSize?: number;
    categoryName?: string;
  }

  export interface CategoryDTO {
    categoryCode?: string;
    categoryName?: string;
    sort?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getCategoryPageApi(params: CategoryApi.CategoryPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: CategoryApi.CategoryVO[];
  }>(`/api/v1/workflow/categories/page`, { params });
}

/** 查询全部列表 */
export function getCategoryListApi() {
  return requestClient.get<CategoryApi.CategoryVO[]>(`/api/v1/workflow/categories/list`);
}

/** 根据 ID 查询 */
export function getCategoryByIdApi(id: string) {
  return requestClient.get<CategoryApi.CategoryVO>(`/api/v1/workflow/categories/${id}`);
}

/** 创建 */
export function createCategoryApi(data: CategoryApi.CategoryDTO) {
  return requestClient.post<string>(`/api/v1/workflow/categories`, data);
}

/** 更新 */
export function updateCategoryApi(data: CategoryApi.CategoryDTO) {
  return requestClient.put<boolean>(`/api/v1/workflow/categories`, data);
}

/** 删除 */
export function deleteCategoryApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/workflow/categories/${id}`);
}
