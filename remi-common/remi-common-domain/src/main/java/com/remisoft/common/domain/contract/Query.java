package com.remisoft.common.domain.contract;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

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
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class FindUserQuery implements Query {
 *     private String name;
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.5.0
 * @since 1.5.0 增加 queryId() / submittedAt() 元数据约定（default 实现，向后兼容）
 * @see Command
 * @see DTO
 * @see VO
 */
public interface Query extends Serializable {

    /**
     * 查询唯一标识。
     *
     * <p>用于日志追踪、链路关联、请求去重。
     * 默认返回随机 UUID，业务方可覆盖以接入 traceId 透传。
     *
     * @return 查询唯一标识，非 null
     */
    default String queryId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 查询提交时间。
     *
     * <p>默认返回调用时刻，业务方可覆盖以支持回放测试。
     *
     * @return 提交时间，非 null
     */
    default Instant submittedAt() {
        return Instant.now();
    }
}
