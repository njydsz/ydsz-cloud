package com.remisoft.common.domain.contract;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 命令对象标记接口（CQRS 写操作入参）。
 *
 * <p>用于标识 CQRS 架构中的 Command 对象，表示一次写操作的入参。
 * 携带标准元数据：{@link #commandId()} 与 {@link #issuedAt()}，
 * 提供事件溯源、日志追踪、幂等判断的锚点。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class CreateUserCommand implements Command {
 *     private final String name;
 *
 *     // 可选择性覆盖元数据方法
 *     &#64;Override
 *     public String commandId() { return "cmd-" + UUID.randomUUID(); }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.5.0
 * @since 1.5.0 增加 commandId() / issuedAt() 元数据约定（default 实现，向后兼容）
 * @see Query
 * @see DTO
 * @see VO
 */
public interface Command extends Serializable {

    /**
     * 命令唯一标识。
     *
     * <p>用于幂等判断、事件溯源关联、日志追踪。
     * 默认返回随机 UUID，业务方可覆盖以接入分布式 ID（如雪花算法）。
     *
     * @return 命令唯一标识，非 null
     */
    default String commandId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 命令发布时间。
     *
     * <p>默认返回调用时刻，业务方可覆盖以支持延迟命令或重放场景。
     *
     * @return 发布时间，非 null
     */
    default Instant issuedAt() {
        return Instant.now();
    }
}
