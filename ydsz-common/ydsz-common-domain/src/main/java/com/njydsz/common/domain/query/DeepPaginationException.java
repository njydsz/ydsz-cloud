package com.njydsz.common.domain.query;

import java.io.Serializable;

import lombok.Getter;

/**
 * 深度分页异常。
 *
 * <p>当 offset 分页的偏移量超过阈值（{@code ydsz.domain.page.cursor-reject-threshold}）时抛出，
 * 强制调用方改用游标分页（SliceQuery / SliceResult 游标模式）。
 *
 * <p>对齐阿里巴巴 Java 开发手册（嵩山版）的深度分页治理建议：超过 10w 条记录的表，禁止 offset > 10000。
 *
 * <p><b>i18n 支持：</b>通过 {@link #getMessageKey()} 获取国际化消息键，
 * 通过 {@link #getMessageParams()} 获取格式化参数，由消费方按请求线程 Locale 解析文案。
 * 在脱离 Spring 上下文的场景（如单元测试），{@link #getMessage()} 提供英文兜底文案。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
public class DeepPaginationException extends RuntimeException implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 国际化消息键（ydsz i18n 规范：domain.deep.pagination.rejected） */
  public static final String MESSAGE_KEY = "domain.deep.pagination.rejected";

  /** 当前请求的 offset 值 */
  private final long offset;

  /** 当前页码 */
  private final int pageNum;

  /** 每页记录数 */
  private final int pageSize;

  /** 拒绝阈值 */
  private final long threshold;

  /**
   * 构造深度分页异常。
   *
   * @param offset 当前 offset 值
   * @param pageNum 当前页码
   * @param pageSize 每页记录数
   * @param threshold 拒绝阈值
   */
  public DeepPaginationException(long offset, int pageNum, int pageSize, long threshold) {
    super(
        String.format(
            "Deep pagination rejected: offset=%d exceeds threshold=%d (pageNum=%d, pageSize=%d). "
                + "Please switch to cursor-based pagination.",
            offset, threshold, pageNum, pageSize));
    this.offset = offset;
    this.pageNum = pageNum;
    this.pageSize = pageSize;
    this.threshold = threshold;
  }

  /**
   * 获取国际化消息键。
   *
   * <p>消息键 {@value #MESSAGE_KEY} 对应的 i18n 文案模板示例：
   * {@code "深度分页被拒绝：offset={0} 超过阈值={1}（pageNum={2}，pageSize={3}），请改用游标分页。"}
   *
   * @return 永不为 {@code null} 的 i18n 消息键
   * @since 26.09.19
   */
  public String getMessageKey() {
    return MESSAGE_KEY;
  }

  /**
   * 获取格式化参数（与 i18n 消息模板中的 {0} {1} {2} {3} 一一对应）。
   *
   * @return 参数数组 [offset, threshold, pageNum, pageSize]
   * @since 26.09.19
   */
  public Object[] getMessageParams() {
    return new Object[] {offset, threshold, pageNum, pageSize};
  }
}
