/**
 * 字典类型 API 模块（前端）
 *
 * <p>封装字典类型（{@code ydsz_dict_type}）的 CRUD 接口调用，对应后端
 * {@code /api/v1/dict/type/*} 端点。供「系统管理 → 字典管理」菜单使用。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li>{@link getDicttypePageApi} — 分页查询字典类型</li>
 *   <li>{@link getDicttypeListApi} — 全量查询字典类型（下拉框）</li>
 *   <li>{@link createDicttypeApi} — 创建字典类型</li>
 *   <li>{@link updateDicttypeApi} — 更新字典类型</li>
 *   <li>{@link deleteDicttypeApi} — 删除字典类型</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace DicttypeApi {
  /** 字典类型视图对象 */
  export interface DicttypeVO {
    id: string;
    typeCode: string;
    typeName: string;
    remark: string;
    status: number;
    createTime: string;
  }

  /** 字典类型分页查询条件 */
  export interface DicttypePageQuery {
    pageNum?: number;
    pageSize?: number;
    typeName?: string;
    typeCode?: string;
  }

  /** 字典类型传输对象（创建/更新） */
  export interface DicttypeDTO {
    typeCode?: string;
    typeName?: string;
    remark?: string;
    status?: number;
  }
}

/** 分页查询字典类型 */
export function getDicttypePageApi(params: DicttypeApi.DicttypePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DicttypeApi.DicttypeVO[];
  }>(`/api/v1/dict/type/page`, { params });
}

/** 查询全部字典类型（下拉框数据源） */
export function getDicttypeListApi() {
  return requestClient.get<DicttypeApi.DicttypeVO[]>(`/api/v1/dict/type/list`);
}

/** 根据 ID 查询dictType */
export function getDicttypeByIdApi(id: string) {
  return requestClient.get<DicttypeApi.DicttypeVO>(`/api/v1/dict/type/${id}`);
}

/** 创建dictType */
export function createDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return requestClient.post<string>(`/api/v1/dict/type`, data);
}

/** 更新dictType */
export function updateDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return requestClient.put<boolean>(`/api/v1/dict/type`, data);
}

/** 删除dictType */
export function deleteDicttypeApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/dict/type/${id}`);
}
