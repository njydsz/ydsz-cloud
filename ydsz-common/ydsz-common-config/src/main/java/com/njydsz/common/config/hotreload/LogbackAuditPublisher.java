package com.njydsz.common.config.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.YdszJson;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 SLF4J 日志的默认配置审计发布器。
 *
 * <p>配置变更记录以 INFO 级别输出到日志，格式包含节点 IP、变更数量、来源命名空间、租户信息。
 *
 * <p>适用于开发环境和未引入 MQ 的生产环境。高合规要求场景建议替换为异步 MQ 实现。
 *
 * @author ydsz-team
 * @since 26.09.20
 * @see ConfigAuditPublisher
 */
public class LogbackAuditPublisher implements ConfigAuditPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(LogbackAuditPublisher.class);

  /** 审计日志 Logger 名称（可通过 logback.xml 单独配置输出目标） */
  private static final Logger AUDIT_LOG = LoggerFactory.getLogger("ydsz.config.audit");

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

      AUDIT_LOG.info("[ConfigAudit] {}", YdszJson.toJson(auditRecord));
    } catch (Exception e) {
      // 审计失败绝不能影响主流程
      LOG.warn("[ConfigAudit] 审计记录发布失败: {}", e.getMessage());
    }
  }
}
