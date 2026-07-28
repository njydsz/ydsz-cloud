/**
 * 国际化 API 模块（前端）
 *
 * 封装国际化语言包接口，对应后端 {@code /api/v1/language/*} 端点。
 * 使用 @ydsz/shared-api 的 createCrudApi 工厂消除重复 CRUD 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';
import { createCrudApi } from '@ydsz/shared-api';

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

/** 语言 CRUD API（由 createCrudApi 工厂创建） */
export const languageApi = createCrudApi<
  LanguageApi.LanguageVO,
  LanguageApi.LanguagePageQuery,
  LanguageApi.LanguageSaveDTO
>(requestClient, '/api/v1/language');

/** 分页查询语言列表 */
export function getLanguagePageApi(params: LanguageApi.LanguagePageQuery) {
  return languageApi.page(params as any);
}

/** 查询全部语言列表 */
export function getLanguageListApi() {
  return languageApi.list();
}

/** 根据 ID 查询语言 */
export function getLanguageByIdApi(id: string) {
  return languageApi.getById(id);
}

/** 创建语言 */
export function createLanguageApi(data: LanguageApi.LanguageSaveDTO) {
  return languageApi.create(data);
}

/** 更新语言 */
export function updateLanguageApi(data: LanguageApi.LanguageSaveDTO) {
  return languageApi.update(data.id ?? '', data);
}

/** 删除语言 */
export function deleteLanguageApi(id: string) {
  return languageApi.remove(id);
}
