/**
 * 国际化 API 模块（前端）
 * <p>封装国际化语言包接口，对应后端 {@code /api/v1/userinfo/language/*} 端点。
 * <p>支持多语言文本、占位符、租户自定义。
 * <p>供「系统管理 → 国际化」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace LanguageApi {
  export interface LanguageVO {
    id: string;
    languageCode: string;
    languageName: string;
    nativeName?: string;
    sort?: number;
    status: number;
    createTime?: string;
  }

  export interface LanguagePageQuery {
    pageNum?: number;
    pageSize?: number;
    languageName?: string;
    languageCode?: string;
    status?: number;
  }

  export interface LanguageSaveDTO {
    id?: string;
    languageCode: string;
    languageName: string;
    nativeName?: string;
    sort?: number;
    status?: number;
  }
}

/** 分页查询语言列表 */
export function getLanguagePageApi(params: LanguageApi.LanguagePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: LanguageApi.LanguageVO[];
  }>('/api/v1/language/page', { params });
}

/** 查询全部语言列表 */
export function getLanguageListApi() {
  return requestClient.get<LanguageApi.LanguageVO[]>('/api/v1/language/list');
}

/** 根据 ID 查询语言 */
export function getLanguageByIdApi(id: string) {
  return requestClient.get<LanguageApi.LanguageVO>(`/api/v1/language/${id}`);
}

/** 创建语言 */
export function createLanguageApi(data: LanguageApi.LanguageSaveDTO) {
  return requestClient.post<string>('/api/v1/language', data);
}

/** 更新语言 */
export function updateLanguageApi(data: LanguageApi.LanguageSaveDTO) {
  return requestClient.put<boolean>('/api/v1/language', data);
}

/** 删除语言 */
export function deleteLanguageApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/language/${id}`);
}
