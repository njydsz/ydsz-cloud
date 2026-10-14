package com.njydsz.common.core.graphql;

/**
 * GraphQL DataFetcher 接口，标记性接口，供 BFF trial 使用。
 *
 * <p>业务模块实现此接口定义单个字段的解析逻辑，与 GraphQL Java
 * {@code graphql.schema.DataFetcher} 保持兼容语义。
 *
 * @param <T> 解析结果的返回类型
 * @author ydsz-team
 * @since 26.10.14
 */
@FunctionalInterface
public interface DataFetcher<T> {

    /**
     * 根据 DataFetchingEnvironment 解析并返回字段值。
     *
     * @param env GraphQL 数据获取上下文（包含 parent、arguments、selection set 等）
     * @return 解析结果
     * @throws Exception 解析过程中的任意异常
     */
    T fetch(DataFetchingEnvironment env) throws Exception;
}
