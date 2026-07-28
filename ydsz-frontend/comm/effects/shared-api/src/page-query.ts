/**
 * page-query 模块
 *
 * @path comm\effects\shared-api\src\page-query.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PageQuery } from './types';

/**
 * 分页查询参数构建器
 *
 * 提供便捷的链式调用构建分页查询参数，
 * 与后端 PageQuery 对齐。
 *
 * @example
 * ```ts
 * const query = createPageQuery(1, 10)
 *   .orderBy('createTime', 'desc')
 *   .build();
 * ```
 */
export function createPageQuery(pageNum = 1, pageSize = 10) {
  let orderBy: string | undefined;
  let orderDirection: 'asc' | 'desc' | undefined;

  const builder = {
    orderBy(column: string, direction: 'asc' | 'desc' = 'asc') {
      orderBy = column;
      orderDirection = direction;
      return builder;
    },
    build(): PageQuery {
      return {
        pageNum,
        pageSize,
        orderBy,
        orderDirection,
      };
    },
  };

  return builder;
}
