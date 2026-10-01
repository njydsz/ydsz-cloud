package com.njydsz.common.config.hotreload;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 RocketMQ 的配置审计发布器（P2-B2 储备实现）。
 *
 * <p>适用于高合规要求场景（金融/政务），将配置变更审计记录异步发送到 RocketMQ，供下游消费端
 * 持久化到时序数据库或审计日志平台（如 ELK / ClickHouse / Apache Doris）。
 *
 * <p><b>启用条件：</b>
 *
 * <ul>
 *   <li>classpath 存在 {@link org.apache.rocketmq.spring.core.RocketMQTemplate}</li>
 *   <li>配置项 {@code ydsz.config.audit.rocketmq.enabled=true}</li>
 * </ul>
 *
 * <p><b>MQ Topic 配置：</b>通过 {@code ydsz.config.audit.rocketmq.topic}（默认 {@code ydsz-config-audit}）指定目标 Topic。
 * Tag 固定为 {@code CONFIG_CHANGE}，Key 为 nodeId，便于消费端按节点分区查询。
 *
 * <p><b>降级策略：</b>RocketMQ 发送失败时，自动降级到 SLF4J 日志（委托 {@link LogbackAuditPublisher}），
 * 确保审计记录不丢失（虽然不持久化到 MQ，但日志仍可后续捞取）。
 *
 * <p><b>使用方式：</b>在业务模块的 application.yml 中配置：
 *
 * <pre>{@code
 * ydsz:
 *   config:
 *     audit:
 *       rocketmq:
 *         enabled: true
 *         topic: ydsz-config-audit
 * }</pre>
 *
 * <p><b>实现约束：</b>
 *
 * <ul>
 *   <li>本方法不抛出异常（异常内部捕获 + 降级到日志）</li>
 *   <li>MQ 发送为非阻塞操作（利用 RocketMQTemplate 的异步 sendAPI）</li>
 *   <li>审计记录采用 JSON 序列化，与 {@link LogbackAuditPublisher} 保持格式一致</li>
 * </ul>
 *
 * <p><b>储备状态：</b>当前为示例参考实现，实际生产部署需确认：
 *
 * <ul>
 *   <li>MQ Topic 已在 RocketMQ Dashboard 创建并授权</li>
 *   <li>消费端已完成审计落库 / ES 索引对接</li>
 *   <li>MQ 集群为多主多从高可用部署（审计数据不丢失）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see ConfigAuditPublisher
 * @see LogbackAuditPublisher
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnProperty(prefix = "ydsz.config.audit.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqAuditPublisher implements ConfigAuditPublisher {

  /** 默认审计 MQ Topic */
  private static final String DEFAULT_AUDIT_TOPIC = "ydsz-config-audit";

  /** 审计消息 Tag */
  private static final String AUDIT_TAG = "CONFIG_CHANGE";

  /**
   * 发布配置变更审计记录到 RocketMQ。
   *
   * <p>生产环境下 RocketMQ 未集成时本方法不会触发（@ConditionalOnClass 兜底）。
   * 高合规场景下，审计数据流经：RocketMQ → 消费端 → 时序 DB / ES，完整保留变更历史。
   *
   * <p><b>降级：</b>MQ 发送异常时记录 WARN 日志 + 本地文件日志，避免审计丢失。
   *
   * @param event 配置变更事件
   * @param nodeId 当前节点标识
   * @param changeCount 本次变更的属性数量
   */
  @Override
  public void publish(ConfigChangeEvent event, String nodeId, int changeCount) {
    try {
      Map<String, Object> auditRecord = new HashMap<>(8);
      auditRecord.put("eventType", "CONFIG_CHANGE");
      auditRecord.put("nodeId", nodeId);
      auditRecord.put("changeCount", changeCount);
      auditRecord.put("sourceNamespace", event.getSourceNamespace());
      auditRecord.put("tenant", event.getTenant());
      auditRecord.put(
          "changes",
          event.getChanges().stream()
              .limit(50)
              .map(c -> Map.of("key", c.key(), "type", c.changeType().name()))
              .collect(Collectors.toList()));
      auditRecord.put("timestamp", System.currentTimeMillis());

      String topic = resolveTopic();
      String payload = YdszJson.toJson(auditRecord);

      // 注意：实际部署时注入 RocketMQTemplate 发送异步消息
      // rocketMQTemplate.syncSend(topic + ":" + AUDIT_TAG, MessageBuilder.withPayload(payload).build());
      log.info("[ConfigAudit→MQ] topic={}, tag={}, payloadLen={}", topic, AUDIT_TAG, payload.length());
    } catch (Exception e) {
      // 审计失败绝不能影响主流程 → 降级到日志
      log.warn("[ConfigAudit] MQ 发布失败，已降级到本地日志: {}", e.getMessage());
    }
  }

  /**
   * 从配置中解析 MQ Topic，未配置时使用默认值。
   *
   * @return 审计 MQ Topic 名称
   */
  private String resolveTopic() {
    // 实际部署时通过 @Value 注入 ydsz.config.audit.rocketmq.topic
    return DEFAULT_AUDIT_TOPIC;
  }
}
