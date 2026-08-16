package com.njydsz.common.notify.channel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.template.TemplateEngine;

/**
 * 通知渠道策略接口。
 *
 * <p>每种通知渠道实现该接口，通过策略模式实现渠道自动分发。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface NotifyChannelStrategy {

  /**
   * 获取支持的渠道类型。
   *
   * @return 通知渠道枚举
   */
  NotifyChannel getChannel();

  /**
   * 发送单条通知。
   *
   * @param receiver 接收者
   * @param title 标题
   * @param content 内容
   * @return 发送结果
   */
  NotifySendResult send(String receiver, String title, String content);

  /**
   * 使用模板发送通知。
   *
   * @param receiver 接收者
   * @param templateCode 模板编码
   * @param templateParams 模板参数
   * @return 发送结果
   */
  NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams);

  /**
   * 批量发送通知。
   *
   * @param receivers 接收者列表
   * @param title 标题
   * @param content 内容
   * @return 发送结果
   */
  NotifySendResult batchSend(List<String> receivers, String title, String content);

  /**
   * 是否启用该渠道。
   *
   * @return 是否启用
   */
  boolean isEnabled();

  /**
   * 设置模板引擎（可选）。
   *
   * <p>通过此方法注入 {@link TemplateEngine} 实例后， {@link #sendTemplate} 可使用新模板引擎按模板 ID 渲染内容。
   * 未设置时，各实现可使用默认的 {@link TemplateEngine}。
   *
   * @param templateEngine 模板引擎实例
   * @see TemplateEngine
   */
  default void setTemplateEngine(TemplateEngine templateEngine) {
    // 默认空实现，按需覆盖
  }

  /**
   * 从模板参数对象中提取 Map。
   *
   * <p>将 {@code Map} 类型参数安全地转换为 {@code Map<String, Object>} 供模板渲染使用。 非 Map 参数返回空 Map。
   *
   * @param templateParams 模板参数对象
   * @return 参数映射，非 Map 输入时返回空 Map
   */
  default Map<String, Object> extractParams(Object templateParams) {
    if (templateParams instanceof Map<?, ?> rawMap) {
      Map<String, Object> params = new HashMap<>(rawMap.size());
      for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
        params.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      return params;
    }
    return Map.of();
  }

  /**
   * 发送卡片消息（P3-4 富文本/交互消息支持）。
   *
   * <p>支持卡片标题、正文、按钮、跳转链接等交互元素。 默认实现降级为普通文本发送，IM 渠道可覆盖此方法实现原生卡片消息。
   *
   * @param receiver 接收者
   * @param card 卡片消息定义
   * @return 发送结果
   */
  default NotifySendResult sendCard(String receiver, CardMessage card) {
    if (card == null) {
      return NotifySendResult.failure("卡片消息为空", "unknown");
    }
    return send(receiver, card.getTitle(), card.getContent());
  }

  /**
   * 创建 JSON 格式的 HTTP 请求头（共用工具方法）。
   *
   * @return Content-Type 为 application/json 的 HTTP 请求头
   */
  static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept-Charset", StandardCharsets.UTF_8.name());
    return headers;
  }
}
