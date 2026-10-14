package com.njydsz.common.core.graphql;

import java.util.Map;

/**
 * GraphQL 数据获取上下文的标记性接口，供 BFF trial 骨架使用。
 *
 * <p>此接口为本地抽象，后续集成 spring-graphql 后可替换为
 * {@code graphql.schema.DataFetchingEnvironment}。
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public interface DataFetchingEnvironment {

    /**
     * 当前字段的参数映射。
     *
     * @return 不可变参数 map
     */
    Map<String, Object> arguments();

    /**
     * 父对象的源值（即当前 DataFetcher 的宿主对象）。
     *
     * @param <T> 父对象类型
     * @return 父对象源值
     */
    <T> T source();
}
