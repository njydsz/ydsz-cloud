/**
 * index 类型定义模块
 *
 * @path comm\types\src\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export type * from './user';
export type * from '@ydsz-core/typings';

// P0-1: 后端 API 类型全量对齐
export type * from './api-response';
export type { BaseEntity, TenantEntity, AuditEntity } from './base-entity';
export { isSuccess, unwrapResponse } from './api-response';
