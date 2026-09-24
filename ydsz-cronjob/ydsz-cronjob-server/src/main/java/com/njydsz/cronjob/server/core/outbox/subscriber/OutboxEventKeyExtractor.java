package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.YdszJson;

/**
 * OutboxMessage extInfo 解析工具。
 *
 * <p>自建 OutboxEvent 迁移至 ydsz-common-event {@code OutboxMessage} 后，原 {@code eventKey}
 * 字段携带于 {@code extInfo} JSON 中（格式：{@code {"eventKey":"xxx"}}）。
 * 本工具从 extInfo JSON 中提取 eventKey，供 subscribers 使用。
 *
 * @author ydsz-team
 * @since 26.09.29
 */
final class OutboxEventKeyExtractor {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxEventKeyExtractor.class);

  /** extInfo 中 eventKey 的 JSON 键名 */
  private static final String EVENT_KEY_FIELD = "eventKey";

  private OutboxEventKeyExtractor() {
    // 工具类禁止实例化
  }

  /**
   * 从 extInfo JSON 中提取 eventKey。
   *
   * @param extInfo extInfo JSON 字符串（格式：{@code {"eventKey":"xxx"}}），可为 null
   * @return eventKey 字符串；extInfo 为空或解析失败时返回 null
   */
  static String extractEventKey(String extInfo) {
    if (extInfo == null || extInfo.isBlank()) {
      return null;
    }
    try {
      Map<String, Object> map = YdszJson.fromJsonToMap(extInfo, String.class, Object.class);
      return map != null ? (String) map.get(EVENT_KEY_FIELD) : null;
    } catch (Exception e) {
      LOG.debug("extInfo 解析失败: {}", e.getMessage());
      return null;
    }
  }
}
