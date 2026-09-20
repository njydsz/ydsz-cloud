package com.njydsz.common.search.api;

/**
 * 搜索服务错误码枚举。
 *
 * <p>每个错误码关联一个 i18n 消息 key，前端/调用方通过 code 查 ResourceBundle 获取对应语言文案。 格式：{@code search.error.{code}}，资源文件为 classpath 下
 * {@code messages/search_messages.properties}。
 *
 * <p><b>错误码规划</b>：
 *
 * <ul>
 *   <li>{@code 1xxx} — 参数类错误（客户端问题）</li>
 *   <li>{@code 2xxx} — 限流 / 熔断（服务端保护）</li>
 *   <li>{@code 3xxx} — 引擎 / 内部错误（服务端问题）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public enum SearchErrorCode {

  // ==================== 正常 ====================

  /** 请求成功（不放入响应，仅作枚举占位） */
  OK(0, "search.error.ok"),

  // ==================== 参数类错误 1xxx ====================

  /** 关键词为空 */
  KEYWORD_EMPTY(1001, "search.error.keyword.empty"),

  /** 页码非法（小于 1） */
  PAGE_INVALID(1002, "search.error.page.invalid"),

  /** 每页大小超限（超过 maxPageSize） */
  PAGE_SIZE_INVALID(1003, "search.error.page.size.invalid"),

  /** 翻页深度超过上限 */
  PAGE_DEPTH_EXCEEDED(1004, "search.error.page.depth.exceeded"),

  /** 搜索类型无效 */
  TYPE_INVALID(1005, "search.error.type.invalid"),

  // ==================== 限流 / 熔断 2xxx ====================

  /** 用户维度限流 */
  RATE_LIMITED_USER(2001, "search.error.rate.limited.user"),

  /** 租户维度限流 */
  RATE_LIMITED_TENANT(2002, "search.error.rate.limited.tenant"),

  /** 熔断器开启 */
  CIRCUIT_BREAKER_OPEN(2003, "search.error.circuit.breaker.open"),

  /** 搜索并发数超限（信号量满） */
  CONCURRENCY_LIMITED(2004, "search.error.concurrency.limited"),

  // ==================== 引擎 / 内部错误 3xxx ====================

  /** 所有引擎不可用 */
  ENGINE_UNAVAILABLE(3001, "search.error.engine.unavailable"),

  /** 搜索超时 */
  SEARCH_TIMEOUT(3002, "search.error.timeout"),

  /** 引擎查询异常 */
  ENGINE_INTERNAL_ERROR(3003, "search.error.engine.internal.error");

  /** 数值错误码（可序列化、可与前端约定） */
  private final int code;

  /** i18n 消息 key，对应 resources/messages/search_messages*.properties */
  private final String messageKey;

  SearchErrorCode(int code, String messageKey) {
    this.code = code;
    this.messageKey = messageKey;
  }

  public int getCode() {
    return code;
  }

  public String getMessageKey() {
    return messageKey;
  }
}
