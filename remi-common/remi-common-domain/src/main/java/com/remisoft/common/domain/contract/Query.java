package com.remisoft.common.domain.contract;

import java.io.Serializable;

/**
 * 查询对象标记接口（CQRS 读操作入参）。
 *
 * <p>用于标识 CQRS 架构中的 Query 对象，表示一次读操作的入参。
 * 零方法开销，仅做编译期类型约束和文档化作用。</p>
 *
 * <p>与 {@link com.remisoft.common.domain.query.BaseQuery} 的关系：BaseQuery 是分页查询的
 * 具体基类（含 pageNum/pageSize 等字段），本接口是更上层的语义标记，两者互不冲突。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public class FindUserQuery implements Query {
 *     private String name;
 *     // ...
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.5.0
 * @see Command
 * @see DTO
 * @see VO
 */
public interface Query extends Serializable {
}
