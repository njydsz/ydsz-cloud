package com.remisoft.common.domain.contract;

import java.io.Serializable;
import java.time.Instant;

/**
 * 命令对象标记接口（CQRS 写操作入参）。
 *
 * <p>用于标识 CQRS 架构中的 Command 对象，表示一次写操作的入参。
 * 携带标准元数据：{@link #commandId()} 与 {@link #issuedAt()}，
 * 提供事件溯源、日志追踪、幂等判断的锚点。
 *
 * <p><b>设计变更（v1.7.0）：</b>
 * 移除 {@link UUID#randomUUID()} 的 default 实现，要求业务方在构造时显式传入
 * 唯一标识符（优先从 MDC traceId 获取），确保同一 Command 的标识符在生命周期内
 * 保持不变，避免幂等判断失效。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class CreateUserCommand implements Command {
 *     private final String name;
 *     private final String commandId;
 *     private final Instant issuedAt;
 *
 *     public CreateUserCommand(String name) {
 *         this.name = name;
 *         // 优先从 MDC 获取 traceId，保持链路追踪一致
 *         this.commandId = MDC.get("traceId") != null
 *             ? MDC.get("traceId")
 *             : "cmd-" + System.nanoTime();
 *         this.issuedAt = Instant.now();
 *     }
 *
 *     &#64;Override public String commandId() { return commandId; }
 *     &#64;Override public Instant issuedAt() { return issuedAt; }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.5.0
 * @since 1.5.0 增加 commandId() / issuedAt() 元数据约定
 * @since 1.7.0 移除 default 实现，要求业务方显式定义，确保幂等语义
 * @see Query
 * @see DTO
 * @see VO
 */
public interface Command extends Serializable {

    /**
     * 命令唯一标识。
     *
     * <p>用于幂等判断、事件溯源关联、日志追踪。
     * 业务方应在构造 Command 时确定此值，确保生命周期内不变。
     *
     * <p><b>推荐来源：</b>
     * <ul>
     *   <li>MDC.get("traceId") — 与分布式链路追踪体系对齐</li>
     *   <li>Idempotency-Key 请求头 — 客户端生成的幂等键</li>
     *   <li>业务流水号 — 业务主键或唯一编号</li>
     * </ul>
     *
     * @return 命令唯一标识，非 null
     */
    String commandId();

    /**
     * 命令发布时间。
     *
     * <p>业务方应在构造 Command 时确定此值，确保生命周期内不变。
     *
     * @return 发布时间，非 null
     */
    Instant issuedAt();
}
