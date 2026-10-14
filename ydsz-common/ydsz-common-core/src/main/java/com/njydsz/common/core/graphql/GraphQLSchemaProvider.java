package com.njydsz.common.core.graphql;

/**
 * GraphQL Schema 提供者 SPI 接口。
 *
 * <p>业务模块实现此接口注册本地 GraphQL schema（SDL 格式），
 * 供网关层做 schema stitching 聚合。
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public interface GraphQLSchemaProvider {

    /**
     * 返回模块的 GraphQL Schema Definition Language（SDL）文本。
     *
     * <p>示例格式（type-first）：
     * <pre>{@code
     * type Query {
     *   agentChat(sessionId: ID!): ChatSession
     * }
     * }</pre>
     *
     * @return SDL 格式的 GraphQL schema 字符串
     */
    String schema();

    /**
     * 模块名称，用于 schema stitching 去重与路由标识。
     *
     * <p>命名规范：与 artifactId 的小写连字符形式一致，
     * 如 {@code "agent-chat"}、{@code "system-config"}。
     *
     * @return 模块唯一标识名
     */
    String moduleName();
}
