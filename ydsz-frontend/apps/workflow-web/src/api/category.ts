/**
 * 流程分类 API 模块（前端）
 * <p>封装流程分类（{@code ydsz_flow_category}）CRUD 接口，对应后端 {@code /api/v1/workflow/category/*} 端点。
 * <p>按业务域/部门对流程模板进行分类管理。
 * <p>供「工作流 → 流程分类」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace CategoryApi {
  /** 流程分类视图对象 */
  export interface CategoryVO {
    id: string;
    categoryCode: string;
    categoryName: string;
    sort: number;
    status: number;
    createTime: string;
  }

  /** 流程分类分页查询参数 */
  export interface CategoryPageQuery {
    pageNum?: number;
    pageSize?: number;
    categoryName?: string;
  }

  /** 流程分类创建/更新请求参数 */
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
