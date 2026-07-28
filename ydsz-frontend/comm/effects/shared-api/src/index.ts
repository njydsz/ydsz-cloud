/**
 * @ydsz/shared-api — 微前端子应用通用业务 API 工具包
 *
 * 提供各子应用复用的通用 API 模式：
 * - createCrudApi() — 标准 CRUD API 工厂（list/page/create/update/delete）
 * - createPageQuery() — 分页查询参数构建器
 * - ApiTypes — 通用 API 类型定义
 */

export type {
  BaseEntity,
  PageQuery,
  PageResult,
  BaseResponse,
  CrudApi,
} from './types';

export {
  createCrudApi,
} from './crud-factory';

export {
  createPageQuery,
} from './page-query';
