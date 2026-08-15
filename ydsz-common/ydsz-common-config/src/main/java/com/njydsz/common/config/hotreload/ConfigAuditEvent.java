package com.njydsz.common.config.hotreload;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEvent;

/**
 * 配置变更审计事件
 *
 * <p>当配置中心（Nacos / Apollo / Spring Cloud Config）下发配置刷新且检测到属性变更时，
 * 由 {@link ConfigChangeBridge} 发布此事件。与 {@link ConfigChangeEvent} 不同，
 * 本事件侧重于审计追踪，携带更完整的元数据信息。
 *
 * <h3>事件携带的审计字段</h3>
 * <ul>
 *   <li>eventTime：事件发生的 UTC 时间戳</li>
 *   <li>nodeIp：当前节点的 IP 地址（用于分布式环境追溯）</li>
 *   <li>source：事件来源（通常为 {@link ConfigChangeBridge} 实例 hash）</li>
 *   <li>changes：变更的属性列表（包含 key、oldValue、newValue）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class ConfigAuditLogger {
 *     &#64;EventListener
 *     public void onConfigAudit(ConfigAuditEvent event) {
 *         auditLog.info("config_changed, node={}, count={}, keys={}",
 *             event.getNodeIp(),
 *             event.getChanges().size(),
 *             event.getChanges().stream().map(ConfigChangeEvent.ConfigChange::key).collect(Collectors.toList())
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see ConfigChangeEvent
 */
public class ConfigAuditEvent extends ApplicationEvent {

    private final Instant eventTime;
    private final String nodeIp;
    private final List<ConfigChangeEvent.ConfigChange> changes;

    /**
     * @param source   事件源（通常是 {@link ConfigChangeBridge} 实例）
     * @param nodeIp   当前节点的 IP 地址
     * @param changes  变更的属性列表
     */
    public ConfigAuditEvent(Object source, String nodeIp, List<ConfigChangeEvent.ConfigChange> changes) {
        super(source);
        this.eventTime = Instant.now();
        this.nodeIp = nodeIp;
        this.changes = List.copyOf(changes);
    }

    /**
     * 获取事件发生的 UTC 时间戳
     *
     * @return 不可变的 UTC 时间戳
     */
    public Instant getEventTime() {
        return eventTime;
    }

    /**
     * 获取当前节点的 IP 地址
     *
     * @return 节点 IP 地址，若无法确定则返回 "unknown"
     */
    public String getNodeIp() {
        return nodeIp;
    }

    /**
     * 获取本次配置刷新的所有属性变更
     *
     * @return 不可变的变更列表
     */
    public List<ConfigChangeEvent.ConfigChange> getChanges() {
        return changes;
    }
}
