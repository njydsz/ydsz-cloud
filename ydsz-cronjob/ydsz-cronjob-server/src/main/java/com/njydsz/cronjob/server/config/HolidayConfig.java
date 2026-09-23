package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P2-3: 节假日 API 配置。
 *
 * <p>支持两种节假日数据获取方式：
 *
 * <ul>
 *   <li><b>任务级手动配置</b>：在任务的 {@code params_json} 中配置 {@code holidays: [...]}（已有能力）
 *   <li><b>全局 API 拉取</b>：通过本配置拉取第三方节假日 API（如 timor.tech、apihubs.cn），
 *       自动填充 WORKDAY / HOLIDAY 策略所需的节假日集合
 * </ul>
 *
 * <p>API 响应预期格式（JSON 数组）：
 *
 * <pre>{@code
 * [{"date":"2024-01-01","type":"HOLIDAY"},{"date":"2024-01-02","type":"WORKDAY"},...]
 * }</pre>
 *
 * <p>缓存策略：每 24 小时刷新一次；API 不可用时仍使用任务级手动配置的 holidays。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class HolidayConfig {

  /**
   * 是否启用全局节假日 API 拉取。
   *
   * <p>默认 false（仅使用任务级手动配置的节假日）。
   */
  private boolean enabled = false;

  /**
   * 节假日 API URL。
   *
   * <p>应返回包含 date（yyyy-MM-dd）和 type（HOLIDAY/WORKDAY）的 JSON 数组。
   *
   * <p>推荐：{@code https://timor.tech/api/holiday/year/{year}} 或企业自建节假日服务。
   */
  private String apiUrl;

  /**
   * API 请求超时（毫秒）。
   */
  private int timeoutMs = 3000;

  /**
   * 缓存刷新周期（小时）。
   *
   * <p>默认 24 小时，避免频繁请求 API。
   */
  private int refreshHours = 24;
}
