package com.remisoft.common.domain.contract;

import java.io.Serializable;
import java.time.Instant;

/**
 * 查询对象标记接口（CQRS 读操作入参）。
 *
 * <p>用于标识 CQRS 架构中的 Query 对象，表示一次读操作的入参。
 * 携带标准元数据：{@link #queryId()} 与 {@link #submittedAt()}，
 * 用于日志追踪、请求去重、限流指纹等场景。
 *
 * <p>与 {@link com.remisoft.common.domain.query.BaseQuery} 的关系：
 * BaseQuery 是分页查询的具体基类（含 pageNum/pageSize 等字段），
 * 本接口是更上层的语义标记，两者互不冲突。
 *
 * <p><b>设计变更（v1.7.0）：</b>
 * 移除 {@link java.util.UUID#randomUUID()} 的 default 实现，要求业务方在构造时显式传入
 * 唯一标识符（优先从 MDC traceId 获取），确保同一 Query 的标识符在生命周期内保持不变。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class FindUserQuery implements Query {
 *     private final String queryId;
 *     private final Instant submittedAt;
 *     private String name;
 *
 *     public FindUserQuery() {
 *         this.queryId = MDC.get("traceId") != null
 *             ? MDC.get("traceId")
 *             : "qry-" + System.nanoTime();
 *         this.submittedAt = Instant.now();
 *     }
 *
 *     &#64;Override public String queryId() { return queryId; }
 *     &#64;Override public Instant submittedAt() { return submittedAt; }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.5.0
 * @since 1.5.0 增加 queryId() / submittedAt() 元数据约定
 * @since 1.7.0 移除 default 实现，要求业务方显式定义
 * @see Command
 * @see DTO
 * @see VO
 */
public interface Query extends Serializable {

    /**
     * 查询唯一标识。
     *
     * <p>用于日志追踪、链路关联、请求去重。
     * 业务方应在构造 Query 时确定此值，确保生命周期内不变。
     *
     * @return 查询唯一标识，非 null
     */
    String queryId();

    /**
     * 查询提交时间。
     *
     * <p>业务方应在构造 Query 时确定此值，确保生命周期内不变。
     *
     * @return 提交时间，非 null
     */
    Instant submittedAt();
}
