/**
 * System Web OpenAPI Schema 类型定义
 *
 * @path apps/system-web/src/api/sdk/schema.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 由 bash/gen-api.mjs 从后端 /v3/api-docs 自动生成。
 * 当前版本为离线骨架（后端未连接时的手写类型对齐版本），
 * 后端可用后运行 `pnpm gen:api system` 覆盖为完整 schema。
 *
 * 结构遵循 openapi-typescript 生成格式：
 * paths 键为 API 路径（含 {param} 占位符），值为各方法的入参/响应类型。
 */

/** 通用分页响应 */
interface PageResult<T> {
  total: number;
  current: number;
  size: number;
  items: T[];
}

/** 统一响应包装（对齐后端 BaseResponse） */
interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

/** 字典项 */
interface DictItemVO {
  id: string;
  typeCode: string;
  itemCode: string;
  itemText: string;
  itemValue: string;
  sort: number;
  status: number;
}

/** 字典类型 */
interface DictTypeVO {
  id: string;
  typeCode: string;
  typeName: string;
  remark: string;
  status: number;
  createTime: string;
}

/** 系统配置 */
interface ConfigVO {
  id: string;
  configKey: string;
  configValue: string;
  remark: string;
}

/** 应用注册 */
interface AppVO {
  id: string;
  appCode: string;
  appName: string;
  status: number;
}

/** 变量 */
interface VariableVO {
  id: string;
  varKey: string;
  varValue: string;
  remark: string;
}

export interface paths {
  '/api/v1/dict/type/page': {
    get: {
      parameters: {
        query: { pageNum?: number; pageSize?: number; typeName?: string; typeCode?: string };
      };
      responses: { 200: { content: { 'application/json': ApiResponse<PageResult<DictTypeVO>> } } };
    };
  };
  '/api/v1/dict/type/list': {
    get: {
      responses: { 200: { content: { 'application/json': ApiResponse<DictTypeVO[]> } } };
    };
  };
  '/api/v1/dict/type/{id}': {
    get: {
      parameters: { path: { id: string } };
      responses: { 200: { content: { 'application/json': ApiResponse<DictTypeVO> } } };
    };
    delete: {
      parameters: { path: { id: string } };
      responses: { 200: { content: { 'application/json': ApiResponse<boolean> } } };
    };
  };
  '/api/v1/dict/item/type/{typeCode}': {
    get: {
      parameters: { path: { typeCode: string } };
      responses: { 200: { content: { 'application/json': ApiResponse<DictItemVO[]> } } };
    };
  };
  '/api/v1/dict/item/page': {
    get: {
      parameters: {
        query: { pageNum?: number; pageSize?: number; typeCode?: string; status?: string };
      };
      responses: { 200: { content: { 'application/json': ApiResponse<PageResult<DictItemVO>> } } };
    };
  };
  '/api/v1/config/page': {
    get: {
      parameters: { query: { pageNum?: number; pageSize?: number; configKey?: string } };
      responses: { 200: { content: { 'application/json': ApiResponse<PageResult<ConfigVO>> } } };
    };
  };
  '/api/v1/app/page': {
    get: {
      parameters: { query: { pageNum?: number; pageSize?: number; appName?: string } };
      responses: { 200: { content: { 'application/json': ApiResponse<PageResult<AppVO>> } } };
    };
  };
  '/api/v1/variable/page': {
    get: {
      parameters: { query: { pageNum?: number; pageSize?: number; varKey?: string } };
      responses: { 200: { content: { 'application/json': ApiResponse<PageResult<VariableVO>> } } };
    };
  };
}
